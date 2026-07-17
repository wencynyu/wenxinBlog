package com.wenxinblog.recommendation.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * blog_db.posts 的只读视图（recommendation-service 不写 posts，只读取做热门/推荐）。
 * author 字段经 LEFT JOIN authors 缓存表填充（同 blog-service fillAuthorAndTags 的来源）；
 * tags 由 post_tags+tags 填充（Phase 2 内容相似用到）。
 */
@Data
public class Post {
    private UUID id;
    private UUID authorId;
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // LEFT JOIN authors 填充（可能为 null —— 作者缓存表无该行）
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;

    // post_tags + tags 填充（Phase 2）
    private List<String> tags;

    @Data
    public static class Author {
        private String id;
        private String username;
        private String displayName;
        private String avatar;
    }
}
