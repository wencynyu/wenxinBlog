package com.wenxinblog.search.dto;

/** 用户搜索结果。字段名对齐前端（avatar/followersCount/postsCount）。 */
public record UserSearchResponse(
        String id,
        String displayName,
        String username,
        String bio,
        String avatar,
        int followersCount,
        int postsCount,
        double score
) {}
