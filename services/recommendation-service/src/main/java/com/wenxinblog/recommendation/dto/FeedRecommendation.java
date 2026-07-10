package com.wenxinblog.recommendation.dto;

public record FeedRecommendation(
        String postId,
        String title,
        String summary,
        String authorName,
        double score,
        String reason
) {}
