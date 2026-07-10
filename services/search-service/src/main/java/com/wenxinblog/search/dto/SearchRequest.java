package com.wenxinblog.search.dto;

import java.util.List;

public record SearchRequest(
        String query,
        int page,
        int size,
        String sortBy,
        List<String> tags,
        String category,
        String authorId
) {
    public SearchRequest {
        if (page < 0) page = 0;
        if (size <= 0 || size > 50) size = 10;
        if (sortBy == null || sortBy.isBlank()) sortBy = "relevance";
    }
}
