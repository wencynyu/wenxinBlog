package com.wenxin.blog.controller;

import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostService postService;

    @InjectMocks
    private PostController postController;

    private WebTestClient client;

    private UUID userId;
    private UUID postId;
    private Post post;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(postController).build();
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();

        post = new Post();
        post.setId(postId);
        post.setAuthorId(userId);
        post.setTitle("Test Post");
        post.setContent("Test Content");
        post.setStatus("PUBLISHED");
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testCreatePost() {
        PostRequest request = new PostRequest();
        request.setTitle("New Post");
        request.setContent("New Content");

        when(postService.createPost(eq(userId), any(PostRequest.class)))
                .thenReturn(Mono.just(post));

        client.post()
                .uri("/api/v1/posts")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").exists();
    }

    @Test
    void testGetPost() {
        when(postService.getPost(postId)).thenReturn(Mono.just(post));

        client.get()
                .uri("/api/v1/posts/{id}", postId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").isEqualTo(postId.toString())
                .jsonPath("$.data.title").isEqualTo("Test Post");
    }

    @Test
    void testGetPost_NotFound() {
        when(postService.getPost(postId)).thenReturn(Mono.empty());

        client.get()
                .uri("/api/v1/posts/{id}", postId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(404)
                .jsonPath("$.message").isEqualTo("Post not found");
    }

    @Test
    void testUpdatePost() {
        PostRequest request = new PostRequest();
        request.setTitle("Updated Post");

        when(postService.updatePost(eq(userId), eq(postId), any(PostRequest.class)))
                .thenReturn(Mono.just(post));

        client.put()
                .uri("/api/v1/posts/{id}", postId)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").exists();
    }

    @Test
    void testDeletePost() {
        when(postService.deletePost(userId, postId)).thenReturn(Mono.empty());

        client.delete()
                .uri("/api/v1/posts/{id}", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("deleted");
    }

    @Test
    void testListPosts_WithPagination() {
        when(postService.listPublishedPosts(eq(0), eq(20), any(), any(), any()))
                .thenReturn(Mono.just(new PostService.PostListResult(List.of(post), 1L)));

        client.get()
                .uri("/api/v1/posts?page=1&pageSize=20")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].id").exists()
                .jsonPath("$.data.total").isEqualTo(1)
                .jsonPath("$.data.pageSize").isEqualTo(20);
    }

    @Test
    void testListPosts_PassesSortByAndTag() {
        when(postService.listPublishedPosts(eq(0), eq(5), eq("likeCount"), eq("desc"), eq("Go")))
                .thenReturn(Mono.just(new PostService.PostListResult(List.of(post), 1L)));

        client.get()
                .uri("/api/v1/posts?page=1&pageSize=5&sortBy=likeCount&sortOrder=desc&tag=Go")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].id").exists();

        verify(postService).listPublishedPosts(eq(0), eq(5), eq("likeCount"), eq("desc"), eq("Go"));
    }

    @Test
    void testListPosts_WithAuthorId() {
        when(postService.listPostsByAuthor(eq(userId), eq(0), eq(20)))
                .thenReturn(Flux.just(post));

        client.get()
                .uri("/api/v1/posts?authorId={authorId}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.items[0].authorId").isEqualTo(userId.toString());
    }

    @Test
    void testPublishPost() {
        when(postService.publishPost(userId, postId)).thenReturn(Mono.empty());
        when(postService.getPost(postId)).thenReturn(Mono.just(post));

        client.post()
                .uri("/api/v1/posts/{id}/publish", postId)
                .header("X-User-Id", userId.toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").exists();
    }
}
