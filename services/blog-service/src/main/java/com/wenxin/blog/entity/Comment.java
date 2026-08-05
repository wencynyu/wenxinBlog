package com.wenxin.blog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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
    private String status;
    private Integer likeCount = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // join authors 表填充（CommentService.batchFillAuthors），对齐前端 Comment.author
    @Transient
    private Post.AuthorInfo author;
}
