package com.wenxinblog.experiment.dto;

import java.util.Map;

/**
 * 用户分桶结果：命中的实验、变体、变体参数。未命中时返回 {@link #empty()}。
 */
public record AssignmentResponse(
        String experimentId,
        String variant,
        Map<String, Object> params
) {
    public static AssignmentResponse empty() {
        return new AssignmentResponse(null, "default", Map.of());
    }

    public boolean isEmpty() {
        return experimentId == null;
    }
}
