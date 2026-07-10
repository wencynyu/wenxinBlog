package com.wenxinblog.content.service;

import com.wenxinblog.content.entity.MediaAsset;
import com.wenxinblog.content.repository.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private MediaAssetRepository mediaRepo;

    private ContentService contentService;

    @BeforeEach
    void setUp() {
        contentService = new ContentService(mediaRepo);
    }

    @Test
    void testUpload_ImageType() {
        UUID userId = UUID.randomUUID();
        MediaAsset savedAsset = createMockAsset(userId, "IMAGE");

        when(mediaRepo.save(any(MediaAsset.class))).thenReturn(Mono.just(savedAsset));

        StepVerifier.create(contentService.upload(
            userId,
            "test.png",
            "image/png",
            1024L,
            "uploads/test.png",
            "http://cdn.example.com/test.png"
        ))
        .expectNextMatches(asset ->
            "IMAGE".equals(asset.getType()) &&
            userId.equals(asset.getUserId()) &&
            "test.png".equals(asset.getOriginalFilename())
        )
        .verifyComplete();
    }

    @Test
    void testUpload_VideoType() {
        UUID userId = UUID.randomUUID();

        when(mediaRepo.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            asset.setId(UUID.randomUUID());
            asset.setStatus("READY");
            return Mono.just(asset);
        });

        StepVerifier.create(contentService.upload(
            userId,
            "test.mp4",
            "video/mp4",
            1024000L,
            "uploads/test.mp4",
            "http://cdn.example.com/test.mp4"
        ))
        .expectNextMatches(asset ->
            "VIDEO".equals(asset.getType()) &&
            userId.equals(asset.getUserId()) &&
            "test.mp4".equals(asset.getOriginalFilename())
        )
        .verifyComplete();
    }

    @Test
    void testUpload_DefaultType_WhenMimeTypeUnknown() {
        UUID userId = UUID.randomUUID();
        MediaAsset savedAsset = createMockAsset(userId, "IMAGE");

        when(mediaRepo.save(any(MediaAsset.class))).thenReturn(Mono.just(savedAsset));

        StepVerifier.create(contentService.upload(
            userId,
            "test.unknown",
            "application/octet-stream",
            512L,
            "uploads/test.unknown",
            "http://cdn.example.com/test.unknown"
        ))
        .expectNextMatches(asset ->
            "IMAGE".equals(asset.getType())
        )
        .verifyComplete();
    }

    @Test
    void testGetFile() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = createMockAsset(UUID.randomUUID(), "IMAGE");
        asset.setId(assetId);

        when(mediaRepo.findById(assetId)).thenReturn(Mono.just(asset));

        StepVerifier.create(contentService.getFile(assetId))
            .expectNext(asset)
            .verifyComplete();
    }

    @Test
    void testGetFile_NotFound() {
        UUID assetId = UUID.randomUUID();

        when(mediaRepo.findById(assetId)).thenReturn(Mono.empty());

        StepVerifier.create(contentService.getFile(assetId))
            .verifyComplete();
    }

    @Test
    void testDeleteFile() {
        UUID assetId = UUID.randomUUID();

        when(mediaRepo.deleteById(assetId)).thenReturn(Mono.empty());

        StepVerifier.create(contentService.deleteFile(assetId))
            .verifyComplete();
    }

    @Test
    void testGetFilesByPost() {
        UUID postId = UUID.randomUUID();
        MediaAsset asset1 = createMockAsset(UUID.randomUUID(), "IMAGE");
        MediaAsset asset2 = createMockAsset(UUID.randomUUID(), "IMAGE");

        when(mediaRepo.findByPostId(postId)).thenReturn(Flux.just(asset1, asset2));

        StepVerifier.create(contentService.getFilesByPost(postId))
            .expectNext(asset1)
            .expectNext(asset2)
            .verifyComplete();
    }

    @Test
    void testGetFilesByPost_Empty() {
        UUID postId = UUID.randomUUID();

        when(mediaRepo.findByPostId(postId)).thenReturn(Flux.empty());

        StepVerifier.create(contentService.getFilesByPost(postId))
            .verifyComplete();
    }

    private MediaAsset createMockAsset(UUID userId, String type) {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setUserId(userId);
        asset.setType(type);
        asset.setOriginalFilename("test.png");
        asset.setMimeType("image/png");
        asset.setSizeBytes(1024L);
        asset.setObjectKey("uploads/test.png");
        asset.setCdnUrl("http://cdn.example.com/test.png");
        asset.setStatus("READY");
        asset.setStorageProvider("LOCAL");
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        return asset;
    }
}
