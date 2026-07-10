package com.wenxinblog.ad.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ad_campaigns")
public class AdCampaign {
    @Id
    private Long id;
    private String advertiserId;
    private String name;
    private String description;
    private BigDecimal budget;
    private BigDecimal dailyBudget;
    private BigDecimal spent;
    private BigDecimal dailySpent;
    private String bidStrategy;
    private BigDecimal bidAmount;
    private String targeting;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
