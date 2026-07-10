package com.wenxinblog.ad.entity;

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
@Table("ad_creatives")
public class AdCreative {
    @Id
    private Long id;
    private Long campaignId;
    private String title;
    private String description;
    private String imageUrl;
    private String landingUrl;
    private String creativeType;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
