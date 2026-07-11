package com.wenxin.blog.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CommentRequest {
    private String content;
    private UUID parentId;
}
