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
@Table("ad_events")
public class AdEvent {
    @Id
    private Long id;
    private Long campaignId;
    private Long creativeId;
    private String userId;
    private String eventType;
    private String ipAddress;
    private String userAgent;
    private String referrer;
    private String metadata;
    private LocalDateTime createdAt;
}
