package com.wenxinblog.recommendation.dto;

import java.time.LocalDateTime;

/**
 * 热门帖子，对齐前端 recommend.ts TrendingPost（注意 viewsCount 是复数）。
 */
public record TrendingPost(
        String id,
        String title,
        long viewsCount,
        long likeCount,
        long commentCount,
        AuthorDto author,
        LocalDateTime createdAt
) {}
