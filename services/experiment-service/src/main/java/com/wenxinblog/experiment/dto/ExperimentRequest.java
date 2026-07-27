package com.wenxinblog.experiment.dto;

/**
 * 创建实验请求。layerName 在 service 层解析为 layerId。
 */
public record ExperimentRequest(
        String name,
        String description,
        String layerName,
        Integer trafficPct,
        String config
) {}
