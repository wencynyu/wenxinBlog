package com.wenxinblog.experiment.dto;

import java.util.Map;

/**
 * 实验统计分析结果：各变体指标 + 假设检验 + SRM 校验 + 建议。
 */
public record ExperimentResult(
        String experimentId,
        Map<String, VariantMetrics> variants,
        double pValue,
        double ciLow,
        double ciHigh,
        boolean significant,
        boolean srmPassed,
        String recommendation
) {
    /**
     * 单变体聚合指标。
     */
    public record VariantMetrics(
            long impressions,
            long clicks,
            long engagements,
            double ctr,
            double engagementRate
    ) {}
}
