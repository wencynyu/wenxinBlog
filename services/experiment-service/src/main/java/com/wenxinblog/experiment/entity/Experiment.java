package com.wenxinblog.experiment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A/B 实验。同一 layer 下实验互斥分流；config 为 JSON 字符串，含 variants 与 metrics。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("experiments")
public class Experiment {
    @Id
    private UUID id;
    private String name;
    private String description;
    private UUID layerId;
    private String status = "DRAFT";
    private Integer trafficPct = 100;
    private String config;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
}
