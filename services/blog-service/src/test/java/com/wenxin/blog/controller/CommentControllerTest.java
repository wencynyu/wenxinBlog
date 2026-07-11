package com.wenxin.blog.controller;

import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.service.CommentService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock
    private CommentService commentService;

    @InjectMocks
    private CommentController commentController;

    private WebTestClient client;

    private UUID userId;
    private UUID postId;
    private UUID commentId;
    private Comment comment;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(commentController).build();
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        commentId = UUID.randomUUID();

        comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setAuthorId(userId);
        comment.setContent("Test comment");
        comment.setParentId(null);
        comment.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testCreateComment() {
        CommentRequest request = new CommentRequest();
        request.setContent("New comment");
        request.setParentId(null);

        when(commentService.createComment(eq(postId), eq(userId), any(CommentRequest.class)))
                .thenReturn(Mono.just(comment));

        client.post()
                .uri("/api/v1/posts/{postId}/comments", postId)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.id").exists()
                .jsonPath("$.data.content").isEqualTo("Test comment");
    }

    @Test
    void testCreateReply() {
        UUID parentId = UUID.randomUUID();
        CommentRequest request = new CommentRequest();
        request.setContent("Reply");
        request.setParentId(parentId);

        comment.setParentId(parentId);
        comment.setContent("Reply");

        when(commentService.createComment(eq(postId), eq(userId), any(CommentRequest.class)))
                .thenReturn(Mono.just(comment));

        client.post()
                .uri("/api/v1/posts/{postId}/comments", postId)
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.data.parentId").isEqualTo(parentId.toString());
    }

    @Test
    void testListComments() {
        Comment comment2 = new Comment();
        comment2.setId(UUID.randomUUID());
        comment2.setPostId(postId);
        comment2.setContent("Second comment");

        when(commentService.listComments(postId))
                .thenReturn(Flux.just(comment, comment2));

        client.get()
                .uri("/api/v1/posts/{postId}/comments", postId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.[0].id").exists()
                .jsonPath("$.[1].id").exists();
    }

    @Test
    void testDeleteComment() {
        when(commentService.deleteComment(commentId)).thenReturn(Mono.empty());

        client.delete()
                .uri("/api/v1/comments/{id}", commentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("deleted");
    }
}
