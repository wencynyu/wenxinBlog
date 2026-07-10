package com.wenxinblog.ad.dto;

public record AdDecisionRequest(
        String positionType,
        String userId,
        String ipAddress,
        String userAgent,
        String referrer,
        int count
) {
    public AdDecisionRequest {
        if (count <= 0) count = 1;
    }
}
