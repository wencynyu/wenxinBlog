package com.wenxinblog.search.dto;

import java.time.LocalDateTime;
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
        LocalDateTime publishedAt,
        double score,
        List<String> highlightTitle,
        List<String> highlightContent
) {}
