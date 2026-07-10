package com.wenxinblog.content.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("media_assets")
public class MediaAsset {
    @Id
    private UUID id;
    private UUID userId;
    private UUID postId;
    private String type; // IMAGE, VIDEO
    private String originalFilename;
    private String mimeType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private Integer duration;
    private String storageProvider;
    private String bucket;
    private String objectKey;
    private String cdnUrl;
    private String status; // PENDING, PROCESSING, READY, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
