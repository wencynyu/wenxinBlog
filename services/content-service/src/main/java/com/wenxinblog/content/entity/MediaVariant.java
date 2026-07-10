package com.wenxinblog.content.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Table("media_variants")
public class MediaVariant {
    @Id
    private UUID id;
    private UUID assetId;
    private String variantType; // THUMB, SMALL, MEDIUM, LARGE
    private Integer width;
    private Integer height;
    private Integer sizeBytes;
    private String objectKey;
    private String cdnUrl;
    private LocalDateTime createdAt;
}
