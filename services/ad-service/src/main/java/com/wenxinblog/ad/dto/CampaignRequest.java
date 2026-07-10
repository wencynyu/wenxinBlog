package com.wenxinblog.ad.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CampaignRequest(
        String name,
        String description,
        BigDecimal budget,
        BigDecimal dailyBudget,
        String bidStrategy,
        BigDecimal bidAmount,
        String targeting,
        LocalDateTime startDate,
        LocalDateTime endDate
) {}
