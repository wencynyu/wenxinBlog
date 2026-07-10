package com.wenxinblog.recommendation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("recommendation_config")
public class RecommendationConfig {
    @Id
    private Long id;
    private String userId;
    private String algorithmType;
    private String weights;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
