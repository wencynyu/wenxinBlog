package com.wenxinblog.ad.dto;

import java.math.BigDecimal;

public record AdDecisionResponse(
        Long creativeId,
        Long campaignId,
        String title,
        String imageUrl,
        String landingUrl,
        String creativeType,
        BigDecimal bidAmount
) {}
