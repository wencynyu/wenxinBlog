package com.wenxinblog.ad.dto;

import java.math.BigDecimal;

public record CampaignStats(
        long impressions,
        long clicks,
        long conversions,
        double ctr,
        BigDecimal spend,
        BigDecimal remainingBudget
) {}
