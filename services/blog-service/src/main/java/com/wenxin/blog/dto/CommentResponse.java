package com.wenxin.blog.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CommentResponse {
    private UUID id;
    private UUID postId;
    private UUID authorId;
    private String authorName;
    private UUID parentId;
    private String content;
    private Integer likeCount;
    private boolean isLiked;
    private LocalDateTime createdAt;
    private List<CommentResponse> replies;
}
