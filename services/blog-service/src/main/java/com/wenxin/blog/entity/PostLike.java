package com.wenxin.blog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("post_likes")
public class PostLike {
    @Id
    private UUID userId;
    private UUID postId;
    private LocalDateTime createdAt;
}
