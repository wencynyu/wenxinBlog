package com.wenxin.blog.controller;

import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.dto.Result;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{postId}/comments")
    public Mono<Result<Comment>> createComment(@PathVariable UUID postId,
                                               @RequestHeader("X-User-Id") UUID userId,
                                               @RequestBody CommentRequest req) {
        return commentService.createComment(postId, userId, req).map(Result::success);
    }

    @GetMapping("/posts/{postId}/comments")
    public Mono<Result<List<Comment>>> listComments(@PathVariable UUID postId) {
        return commentService.listComments(postId).collectList().map(Result::success);
    }

    @DeleteMapping("/comments/{id}")
    public Mono<Result<Void>> deleteComment(@RequestHeader("X-User-Id") UUID userId,
                                            @PathVariable UUID id) {
        return commentService.deleteComment(userId, id).thenReturn(Result.success("deleted", null));
    }
}
