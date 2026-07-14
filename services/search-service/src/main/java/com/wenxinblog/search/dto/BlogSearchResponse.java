package com.wenxinblog.search.dto;

import java.util.List;

public record BlogSearchResponse(
        String id,
        String title,
        String content,
        String summary,
        String authorId,
        String authorName,
        List<String> tags,
        String category,
        int viewCount,
        int likeCount,
        String publishedAt,
        double score,
        List<String> highlightTitle,
        List<String> highlightContent
) {}
