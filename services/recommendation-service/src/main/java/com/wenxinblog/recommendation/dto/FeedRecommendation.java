package com.wenxinblog.recommendation.dto;

import com.wenxinblog.recommendation.entity.Post;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐/相关博文，对齐前端 recommend.ts FeedRecommendation。
 */
public record FeedRecommendation(
        String id,
        String title,
        String summary,
        String coverImage,
        String authorId,
        AuthorDto author,
        List<String> tags,
        int likeCount,
        int commentCount,
        double score,
        LocalDateTime createdAt
) {
    public static FeedRecommendation from(Post p, List<String> tags, double score) {
        AuthorDto author = new AuthorDto(
                p.getAuthorId() != null ? p.getAuthorId().toString() : null,
                p.getAuthorUsername(),
                p.getAuthorDisplayName(),
                p.getAuthorAvatarUrl());
        return new FeedRecommendation(
                p.getId().toString(),
                p.getTitle(),
                p.getSummary(),
                p.getCoverImage(),
                p.getAuthorId() != null ? p.getAuthorId().toString() : null,
                author,
                tags != null ? tags : List.of(),
                p.getLikeCount() != null ? p.getLikeCount() : 0,
                p.getCommentCount() != null ? p.getCommentCount() : 0,
                score,
                p.getCreatedAt());
    }
}
