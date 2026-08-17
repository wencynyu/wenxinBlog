package com.wenxin.blog.controller;

import com.wenxin.blog.service.FavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteControllerTest {

    @Mock
    private FavoriteService favoriteService;

    @InjectMocks
    private FavoriteController favoriteController;

    private WebTestClient client;

    private UUID userId;
    private UUID postId;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(favoriteController).build();
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
    }

    @Test
    void testToggleFavorite_ReturnsTrue() {
        when(favoriteService.toggleFavorite(userId, postId)).thenReturn(Mono.just(true));

        client.post()
                .uri("/api/v1/posts/{postId}/favorite", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(true);
    }

    @Test
    void testToggleFavorite_ReturnsFalse() {
        when(favoriteService.toggleFavorite(userId, postId)).thenReturn(Mono.just(false));

        client.post()
                .uri("/api/v1/posts/{postId}/favorite", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(false);
    }

    @Test
    void testIsFavorited() {
        when(favoriteService.isFavorited(userId, postId)).thenReturn(Mono.just(false));

        client.get()
                .uri("/api/v1/posts/{postId}/favorited", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data").isEqualTo(false);
    }
}
