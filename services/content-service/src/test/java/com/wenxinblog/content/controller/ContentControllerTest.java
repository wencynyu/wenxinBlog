package com.wenxinblog.content.controller;

import com.wenxinblog.content.dto.Result;
import com.wenxinblog.content.entity.MediaAsset;
import com.wenxinblog.content.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    @Mock
    private ContentService contentService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        ContentController controller = new ContentController(contentService);
        client = WebTestClient.bindToController(controller)
            .configureClient()
            .build();
    }

    @Test
    void testUpload() {
        UUID userId = UUID.randomUUID();
        MediaAsset asset = createMockAsset(userId);

        when(contentService.upload(eq(userId), any()))
            .thenReturn(Mono.just(asset));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", "test.png")
            .contentType(MediaType.IMAGE_PNG)
            .filename("test.png");

        client.post().uri("/api/v1/content/upload")
            .header("X-User-Id", userId.toString())
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk()
            .expectBody(Result.class)
            .value(result -> {
                assertEquals(200, result.getCode());
                assertNotNull(result.getData());
            });
    }

    @Test
    void testUpload_VideoFile() {
        UUID userId = UUID.randomUUID();
        MediaAsset asset = createMockAsset(userId);
        asset.setType("VIDEO");
        asset.setMimeType("video/mp4");

        when(contentService.upload(eq(userId), any()))
            .thenReturn(Mono.just(asset));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", "test.mp4")
            .contentType(MediaType.valueOf("video/mp4"))
            .filename("test.mp4");

        client.post().uri("/api/v1/content/upload")
            .header("X-User-Id", userId.toString())
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.data.status").isEqualTo("READY");
    }

    @Test
    void testGetFile() {
        UUID assetId = UUID.randomUUID();
        MediaAsset asset = createMockAsset(UUID.randomUUID());
        asset.setId(assetId);

        when(contentService.getFile(assetId)).thenReturn(Mono.just(asset));

        client.get().uri("/api/v1/content/" + assetId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Result.class)
            .value(result -> {
                assertEquals(200, result.getCode());
                assertNotNull(result.getData());
            });
    }

    @Test
    void testGetFile_NotFound() {
        UUID assetId = UUID.randomUUID();

        when(contentService.getFile(assetId)).thenReturn(Mono.empty());

        client.get().uri("/api/v1/content/" + assetId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Result.class)
            .value(result -> {
                assertEquals(404, result.getCode());
                assertEquals("File not found", result.getMessage());
            });
    }

    @Test
    void testDeleteFile() {
        UUID assetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(contentService.deleteFile(userId, assetId)).thenReturn(Mono.empty());

        client.delete().uri("/api/v1/content/" + assetId)
            .header("X-User-Id", userId.toString())
            .exchange()
            .expectStatus().isOk()
            .expectBody(Result.class)
            .value(result -> {
                assertEquals(200, result.getCode());
            });
    }

    @Test
    void testGetFilesByPost() {
        UUID postId = UUID.randomUUID();
        MediaAsset asset1 = createMockAsset(UUID.randomUUID());
        MediaAsset asset2 = createMockAsset(UUID.randomUUID());

        when(contentService.getFilesByPost(postId)).thenReturn(Flux.just(asset1, asset2));

        client.get().uri("/api/v1/content/post/" + postId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.data").isArray()
            .jsonPath("$.data.length()").isEqualTo(2);
    }

    private MediaAsset createMockAsset(UUID userId) {
        MediaAsset asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setUserId(userId);
        asset.setType("IMAGE");
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
