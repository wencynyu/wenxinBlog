package com.wenxin.blog.controller;

import com.wenxin.blog.service.LikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeControllerTest {

    @Mock
    private LikeService likeService;

    @InjectMocks
    private LikeController likeController;

    private WebTestClient client;

    private UUID userId;
    private UUID postId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(likeController).build();
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
    }

    @Test
    void testToggleLike_ReturnsTrue() {
        when(likeService.toggleLike(userId, postId)).thenReturn(Mono.just(true));

        client.post()
                .uri("/api/v1/posts/{postId}/like", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(true);
    }

    @Test
    void testToggleLike_ReturnsFalse() {
        when(likeService.toggleLike(userId, postId)).thenReturn(Mono.just(false));

        client.post()
                .uri("/api/v1/posts/{postId}/like", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(false);
    }

    @Test
    void testIsLiked() {
        when(likeService.isLiked(userId, postId)).thenReturn(Mono.just(true));

        client.get()
                .uri("/api/v1/posts/{postId}/liked", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(true);
    }
}
