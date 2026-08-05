package com.wenxinblog.search.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 博客搜索结果。author 用嵌套对象（对齐前端），createdAt/commentCount 取自 ES 文档。
 * （ES 文档无独立 author avatar，avatar 暂为 null，前端用首字母占位。）
 */
public record BlogSearchResponse(
        String id,
        String title,
        String content,
        String summary,
        AuthorDto author,
        List<String> tags,
        String category,
        int viewCount,
        int likeCount,
        int commentCount,
        LocalDateTime createdAt,
        double score,
        List<String> highlightTitle,
        List<String> highlightContent
) {}
