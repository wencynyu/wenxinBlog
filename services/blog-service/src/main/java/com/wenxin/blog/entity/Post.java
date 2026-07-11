package com.wenxin.blog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("posts")
public class Post {
    @Id
    private UUID id;
    private UUID authorId;
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private String status; // DRAFT, PUBLISHED, ARCHIVED
    private Integer viewCount = 0;
    private Integer likeCount = 0;
    private Integer commentCount = 0;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
