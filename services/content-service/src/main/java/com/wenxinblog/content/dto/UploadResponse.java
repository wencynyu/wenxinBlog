package com.wenxinblog.content.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class UploadResponse {
    private UUID id;
    private String objectKey;
    private String cdnUrl;
    private String status;
    private LocalDateTime createdAt;
}
