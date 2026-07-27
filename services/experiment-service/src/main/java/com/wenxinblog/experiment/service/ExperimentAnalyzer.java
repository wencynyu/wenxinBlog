package com.wenxinblog.experiment.service;

import com.wenxinblog.experiment.dto.ExperimentResult;
import com.wenxinblog.experiment.dto.ExperimentResult.VariantMetrics;
import com.wenxinblog.experiment.repository.ExperimentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 实验统计分析：聚合 Redis 指标计数，对 2 变体做 CTR 两比例 z 检验 + 95% 置信区间，
 * 并做 SRM（样本比例失衡）校验，给出中文建议。
 *
 * <p>约定 variant[0]=control、variant[1]=treatment。diff = p_treatment - p_control。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentAnalyzer {

    private static final double SIGNIFICANCE = 0.05;

    private final ExperimentRepository experimentRepo;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;

    public Mono<ExperimentResult> analyze(UUID experimentId) {
        return experimentRepo.findById(experimentId)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Experiment not found: " + experimentId)))
                .flatMap(exp -> {
                    List<VariantConfig> variants = parseVariants(exp.getConfig());
                    if (variants.isEmpty()) {
                        return Mono.error(new IllegalStateException("Experiment has no variants configured"));
                    }
                    String expId = experimentId.toString();
                    return Flux.fromIterable(variants)
                            .flatMap(vc -> loadMetrics(expId, vc.name())
                                    .map(m -> Map.entry(vc.name(), m)))
                            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                            .map(metricsMap -> buildResult(expId, variants, metricsMap));
                });
    }

    private record VariantConfig(String name, double weight) {}

    private List<VariantConfig> parseVariants(String configJson) {
        try {
            JsonNode variants = mapper.readTree(configJson).path("variants");
            List<VariantConfig> list = new ArrayList<>();
            for (JsonNode v : variants) {
                list.add(new VariantConfig(v.path("name").asText(), v.path("weight").asDouble(1.0)));
            }
            return list;
        } catch (Exception e) {
            log.warn("Failed to parse experiment config: {}", e.getMessage());
            return List.of();
        }
    }

    private Mono<VariantMetrics> loadMetrics(String expId, String variant) {
        Mono<Long> impressions = readCounter(expId, variant, "impressions");
        Mono<Long> clicks = readCounter(expId, variant, "clicks");
        Mono<Long> engagements = readCounter(expId, variant, "engagements");
        return Mono.zip(impressions, clicks, engagements)
                .map(t -> {
                    long imp = t.getT1();
                    long clk = t.getT2();
                    long eng = t.getT3();
                    double ctr = imp > 0 ? (double) clk / imp : 0.0;
                    double engagementRate = imp > 0 ? (double) eng / imp : 0.0;
                    return new VariantMetrics(imp, clk, eng, ctr, engagementRate);
                });
    }

    private Mono<Long> readCounter(String expId, String variant, String metric) {
        return redis.opsForValue().get("metrics:" + expId + ":" + variant + ":" + metric)
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .defaultIfEmpty(0L);
    }

    private ExperimentResult buildResult(String expId, List<VariantConfig> variants,
                                         Map<String, VariantMetrics> metrics) {
        List<String> names = variants.stream().map(VariantConfig::name).toList();
        List<Double> weights = variants.stream().map(VariantConfig::weight).toList();
        boolean srmPassed = checkSrm(names, metrics, weights);

        double pValue = 1.0;
        double ciLow = 0.0;
        double ciHigh = 0.0;
        boolean significant = false;
        String recommendation;

        if (names.size() == 2) {
            VariantMetrics control = metrics.get(names.get(0));
            VariantMetrics treatment = metrics.get(names.get(1));
            if (control == null || treatment == null
                    || control.impressions() == 0 || treatment.impressions() == 0) {
                recommendation = "样本不足";
            } else {
                double p1 = (double) control.clicks() / control.impressions();
                double p2 = (double) treatment.clicks() / treatment.impressions();
                long totalClicks = control.clicks() + treatment.clicks();
                long totalImp = control.impressions() + treatment.impressions();
                double pooled = totalImp > 0 ? (double) totalClicks / totalImp : 0.0;
                double se = Math.sqrt(pooled * (1 - pooled)
                        * (1.0 / control.impressions() + 1.0 / treatment.impressions()));
                double z = se > 0 ? (p2 - p1) / se : 0.0;
                pValue = 2 * (1 - normalCDF(Math.abs(z)));
                // 95% 置信区间（独立比例）
                double ciSe = Math.sqrt(p1 * (1 - p1) / control.impressions()
                        + p2 * (1 - p2) / treatment.impressions());
                double diff = p2 - p1;
                ciLow = diff - 1.96 * ciSe;
                ciHigh = diff + 1.96 * ciSe;
                significant = pValue < SIGNIFICANCE;
                if (!significant) {
                    recommendation = "不显著";
                } else if (diff > 0) {
                    double lift = p1 > 0 ? (diff / p1) * 100 : 0;
                    recommendation = String.format("treatment 显著优于 control (CTR +%.2f%%, p=%.4f)", lift, pValue);
                } else {
                    double lift = p1 > 0 ? (-diff / p1) * 100 : 0;
                    recommendation = String.format("control 显著优于 treatment (CTR +%.2f%%, p=%.4f)", lift, pValue);
                }
            }
        } else if (names.size() > 2) {
            recommendation = "多变体场景，仅展示各变体 CTR，未做多组比较校正";
        } else {
            recommendation = "样本不足";
        }

        return new ExperimentResult(expId, metrics, pValue, ciLow, ciHigh, significant, srmPassed, recommendation);
    }

    /** SRM 校验：用配置权重作期望比例，卡方拟合优度，p=0.01 临界值。样本过少直接判通过。 */
    private boolean checkSrm(List<String> names, Map<String, VariantMetrics> metrics, List<Double> weights) {
        long total = names.stream().mapToLong(n -> metrics.getOrDefault(n, zeroMetrics()).impressions()).sum();
        if (total < 50) {
            return true;
        }
        double weightSum = weights.stream().mapToDouble(Double::doubleValue).sum();
        if (weightSum <= 0) {
            return true;
        }
        double chi2 = 0.0;
        for (int i = 0; i < names.size(); i++) {
            double expected = total * weights.get(i) / weightSum;
            if (expected <= 0) {
                continue;
            }
            long observed = metrics.getOrDefault(names.get(i), zeroMetrics()).impressions();
            chi2 += Math.pow(observed - expected, 2) / expected;
        }
        return chi2 <= chiSquareCritical(names.size() - 1);
    }

    private VariantMetrics zeroMetrics() {
        return new VariantMetrics(0, 0, 0, 0.0, 0.0);
    }

    /** p=0.01 卡方临界值（df 1~4）；df 未知时不报错。 */
    private double chiSquareCritical(int df) {
        return switch (df) {
            case 1 -> 6.635;
            case 2 -> 9.210;
            case 3 -> 11.345;
            case 4 -> 13.277;
            default -> Double.MAX_VALUE;
        };
    }

    /** 标准正态 CDF，Abramowitz-Stegun 26.2.17 近似（最大误差 < 7.5e-8）。 */
    private double normalCDF(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2) * t
                * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z > 0 ? 1 - d : d;
    }
}
