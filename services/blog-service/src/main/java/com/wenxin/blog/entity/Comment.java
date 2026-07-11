package com.wenxin.blog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("comments")
public class Comment {
    @Id
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private UUID parentId;
    private String content;
    private Integer likeCount = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
