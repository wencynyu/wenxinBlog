package com.wenxinblog.experiment.dto;

import com.wenxinblog.experiment.entity.Experiment;
import com.wenxinblog.experiment.entity.Layer;

import java.time.LocalDateTime;

/**
 * 实验详情响应：把 layerId 解析回 layerName 透出。
 */
public record ExperimentResponse(
        String id,
        String name,
        String description,
        String layerName,
        String status,
        Integer trafficPct,
        String config,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
    public static ExperimentResponse from(Experiment experiment, Layer layer) {
        return new ExperimentResponse(
                experiment.getId() != null ? experiment.getId().toString() : null,
                experiment.getName(),
                experiment.getDescription(),
                layer != null ? layer.getName() : null,
                experiment.getStatus(),
                experiment.getTrafficPct(),
                experiment.getConfig(),
                experiment.getStartedAt(),
                experiment.getEndedAt(),
                experiment.getCreatedAt()
        );
    }
}
