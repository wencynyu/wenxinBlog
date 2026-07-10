package com.wenxinblog.recommendation.dto;

public record TrendingPost(
        String postId,
        String title,
        long viewCount,
        long likeCount,
        double score,
        String trendDirection
) {}
