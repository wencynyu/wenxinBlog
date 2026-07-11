package com.wenxin.blog.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PostResponse {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String title;
    private String summary;
    private String coverImage;
    private String status;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private List<String> tags;
    private boolean isLiked;
    private boolean isFavorited;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
