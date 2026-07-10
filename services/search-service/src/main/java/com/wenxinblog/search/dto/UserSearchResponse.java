package com.wenxinblog.search.dto;

public record UserSearchResponse(
        String id,
        String displayName,
        String username,
        String bio,
        String avatarUrl,
        int followerCount,
        int postCount,
        double score
) {}
