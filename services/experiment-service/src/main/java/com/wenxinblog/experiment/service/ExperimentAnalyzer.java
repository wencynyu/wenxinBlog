package com.wenxinblog.experiment.service;

import com.wenxinblog.experiment.dto.ExperimentResult;
import com.wenxinblog.experiment.dto.ExperimentResult.VariantMetrics;
import com.wenxinblog.experiment.repository.ExperimentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Slf4j
@Service
public class ExperimentAnalyzer {

    private static final double SIGNIFICANCE = 0.05;

    private final ExperimentRepository experimentRepo;
    private final JdbcTemplate clickHouse;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExperimentAnalyzer(ExperimentRepository experimentRepo,
                              @Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse) {
        this.experimentRepo = experimentRepo;
        this.clickHouse = clickHouse;
    }

    public Mono<ExperimentResult> analyze(UUID experimentId) {
        return Mono.fromCallable(() -> analyzeBlocking(experimentId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ExperimentResult analyzeBlocking(UUID experimentId) {
        var exp = experimentRepo.findById(experimentId).block();
        if (exp == null) throw new NoSuchElementException("Experiment not found: " + experimentId);

        List<VariantConfig> variants = parseVariants(exp.getConfig());
        if (variants.isEmpty()) throw new IllegalStateException("No variants configured");

        String expId = experimentId.toString();
        Map<String, VariantMetrics> metricsMap = queryClickHouse(expId);
        for (VariantConfig vc : variants) {
            metricsMap.putIfAbsent(vc.name(), new VariantMetrics(0, 0, 0, 0.0, 0.0));
        }
        return buildResult(expId, variants, metricsMap);
    }

    @SuppressWarnings("unchecked")
    private Map<String, VariantMetrics> queryClickHouse(String expId) {
        String sql = "SELECT variant," +
                " countIf(event_type = 'impression') AS impressions," +
                " countIf(event_type = 'view_post') AS clicks," +
                " countIf(event_type = 'like_post' OR event_type = 'comment_post') AS engagements" +
                " FROM behavior_events WHERE experiment_id = ? GROUP BY variant";
        Map<String, VariantMetrics> result = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = clickHouse.queryForList(sql, expId);
            for (Map<String, Object> row : rows) {
                String variant = String.valueOf(row.get("variant"));
                long imp = ((Number) row.get("impressions")).longValue();
                long clk = ((Number) row.get("clicks")).longValue();
                long eng = ((Number) row.get("engagements")).longValue();
                result.put(variant, new VariantMetrics(imp, clk, eng,
                    imp > 0 ? (double) clk / imp : 0.0, imp > 0 ? (double) eng / imp : 0.0));
            }
        } catch (Exception e) {
            log.error("ClickHouse query failed for {}: {}", expId, e.getMessage());
        }
        return result;
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
        } catch (Exception e) { return List.of(); }
    }

    private ExperimentResult buildResult(String expId, List<VariantConfig> variants,
                                         Map<String, VariantMetrics> metrics) {
        List<String> names = variants.stream().map(VariantConfig::name).toList();
        List<Double> weights = variants.stream().map(VariantConfig::weight).toList();
        boolean srmPassed = checkSrm(names, metrics, weights);
        double pValue = 1.0, ciLow = 0.0, ciHigh = 0.0;
        boolean significant = false;
        String recommendation;

        if (names.size() == 2) {
            VariantMetrics control = metrics.get(names.get(0));
            VariantMetrics treatment = metrics.get(names.get(1));
            if (control == null || treatment == null || control.impressions() == 0 || treatment.impressions() == 0) {
                recommendation = "样本不足";
            } else {
                double p1 = (double) control.clicks() / control.impressions();
                double p2 = (double) treatment.clicks() / treatment.impressions();
                long tc = control.clicks() + treatment.clicks();
                long ti = control.impressions() + treatment.impressions();
                double pooled = ti > 0 ? (double) tc / ti : 0.0;
                double se = Math.sqrt(pooled * (1 - pooled) * (1.0 / control.impressions() + 1.0 / treatment.impressions()));
                double z = se > 0 ? (p2 - p1) / se : 0.0;
                pValue = 2 * (1 - normalCDF(Math.abs(z)));
                double ciSe = Math.sqrt(p1*(1-p1)/control.impressions() + p2*(1-p2)/treatment.impressions());
                double diff = p2 - p1;
                ciLow = diff - 1.96 * ciSe; ciHigh = diff + 1.96 * ciSe;
                significant = pValue < SIGNIFICANCE;
                if (!significant) recommendation = "不显著";
                else if (diff > 0) recommendation = String.format("treatment 显著优于 control (CTR +%.2f%%, p=%.4f)", p1>0?(diff/p1)*100:0, pValue);
                else recommendation = String.format("control 显著优于 treatment (CTR +%.2f%%, p=%.4f)", p1>0?(-diff/p1)*100:0, pValue);
            }
        } else if (names.size() > 2) recommendation = "多变体场景，仅展示各变体 CTR";
        else recommendation = "样本不足";
        return new ExperimentResult(expId, metrics, pValue, ciLow, ciHigh, significant, srmPassed, recommendation);
    }

    private boolean checkSrm(List<String> names, Map<String, VariantMetrics> metrics, List<Double> weights) {
        long total = names.stream().mapToLong(n -> metrics.getOrDefault(n, zero()).impressions()).sum();
        if (total < 50) return true;
        double ws = weights.stream().mapToDouble(Double::doubleValue).sum();
        if (ws <= 0) return true;
        double chi2 = 0.0;
        for (int i = 0; i < names.size(); i++) {
            double expected = total * weights.get(i) / ws;
            if (expected <= 0) continue;
            chi2 += Math.pow(metrics.getOrDefault(names.get(i), zero()).impressions() - expected, 2) / expected;
        }
        return chi2 <= switch (names.size() - 1) { case 1 -> 6.635; case 2 -> 9.210; case 3 -> 11.345; case 4 -> 13.277; default -> Double.MAX_VALUE; };
    }

    private VariantMetrics zero() { return new VariantMetrics(0, 0, 0, 0.0, 0.0); }

    private double normalCDF(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2) * t
                * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z > 0 ? 1 - d : d;
    }
}
