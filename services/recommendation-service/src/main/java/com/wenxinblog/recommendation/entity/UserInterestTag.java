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
@Table("user_interest_tags")
public class UserInterestTag {
    @Id
    private Long id;
    private String userId;
    private String tag;
    private Double weight;
    private LocalDateTime createdAt;
}
