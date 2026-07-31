package com.wenxinblog.content.service;

import com.wenxinblog.content.entity.MediaAsset;
import com.wenxinblog.content.repository.MediaAssetRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private MediaAssetRepository mediaRepo;

    @Mock
    private MinioClient minioClient;

    @Mock
    private FilePart filePart;

    private ContentService contentService;

    @BeforeEach
    void setUp() {
        contentService = new ContentService(mediaRepo, minioClient);
        org.springframework.test.util.ReflectionTestUtils.setField(contentService, "bucket", "wenxinblog-content");
        org.springframework.test.util.ReflectionTestUtils.setField(contentService, "endpoint", "http://localhost:9000");
    }

    private void stubFilePart(String filename, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setContentLength(4L);
        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(headers);
        when(filePart.content()).thenReturn(
            Flux.just(DefaultDataBufferFactory.sharedInstance.wrap("test".getBytes())));
    }

    @Test
    void testUpload_ImageType() throws Exception {
        UUID userId = UUID.randomUUID();
        stubFilePart("test.png", MediaType.IMAGE_PNG);

        when(mediaRepo.save(any(MediaAsset.class))).thenAnswer(inv -> {
            MediaAsset a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return Mono.just(a);
        });

        StepVerifier.create(contentService.upload(userId, filePart))
            .expectNextMatches(asset ->
                "IMAGE".equals(asset.getType()) &&
                userId.equals(asset.getUserId()) &&
                "test.png".equals(asset.getOriginalFilename()) &&
                "MINIO".equals(asset.getStorageProvider())
            )
            .verifyComplete();

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void testUpload_VideoType() throws Exception {
        UUID userId = UUID.randomUUID();
        stubFilePart("test.mp4", MediaType.valueOf("video/mp4"));

        when(mediaRepo.save(any(MediaAsset.class))).thenAnswer(inv -> {
            MediaAsset a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setStatus("READY");
            return Mono.just(a);
        });

        StepVerifier.create(contentService.upload(userId, filePart))
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
        stubFilePart("test.unknown", MediaType.APPLICATION_OCTET_STREAM);

        when(mediaRepo.save(any(MediaAsset.class))).thenAnswer(inv -> {
            MediaAsset a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return Mono.just(a);
        });

        StepVerifier.create(contentService.upload(userId, filePart))
            .expectNextMatches(asset -> "IMAGE".equals(asset.getType()))
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
        UUID userId = UUID.randomUUID();
        MediaAsset asset = createMockAsset(userId, "IMAGE");
        UUID assetId = asset.getId();
        when(mediaRepo.findById(assetId)).thenReturn(Mono.just(asset));
        when(mediaRepo.deleteById(assetId)).thenReturn(Mono.empty());

        StepVerifier.create(contentService.deleteFile(userId, assetId))
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
        asset.setStorageProvider("MINIO");
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        return asset;
    }
}
