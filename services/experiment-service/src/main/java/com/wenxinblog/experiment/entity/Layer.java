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
 * A/B 测试分层（mutually exclusive traffic layers, e.g. recommendation / ads / search / ui）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("layers")
public class Layer {
    @Id
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}
