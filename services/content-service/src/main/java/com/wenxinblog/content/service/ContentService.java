package com.wenxinblog.content.service;

import com.wenxinblog.content.dto.UploadResponse;
import com.wenxinblog.content.entity.MediaAsset;
import com.wenxinblog.content.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final MediaAssetRepository mediaRepo;

    public Mono<MediaAsset> upload(UUID userId, String filename, String mimeType, Long size,
                                     String objectKey, String cdnUrl) {
        String type = mimeType.startsWith("video/") ? "VIDEO" : "IMAGE";
        MediaAsset asset = new MediaAsset();
        asset.setUserId(userId);
        asset.setType(type);
        asset.setOriginalFilename(filename);
        asset.setMimeType(mimeType);
        asset.setSizeBytes(size);
        asset.setObjectKey(objectKey);
        asset.setCdnUrl(cdnUrl);
        asset.setStatus("READY");
        asset.setStorageProvider("LOCAL");
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        return mediaRepo.save(asset);
    }

    public Mono<MediaAsset> getFile(UUID id) {
        return mediaRepo.findById(id);
    }

    public Mono<Void> deleteFile(UUID id) {
        return mediaRepo.deleteById(id);
    }

    public Flux<MediaAsset> getFilesByPost(UUID postId) {
        return mediaRepo.findByPostId(postId);
    }
}
