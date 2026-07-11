package com.wenxin.blog.dto;

import lombok.Data;
import java.util.List;

@Data
public class PostRequest {
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private List<String> tags;
    private String status; // DRAFT, PUBLISHED
}
