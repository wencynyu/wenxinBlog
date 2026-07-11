package com.wenxin.blog.controller;

import com.wenxin.blog.dto.PaginatedResponse;
import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.dto.Result;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public Mono<Result<Post>> createPost(@RequestHeader("X-User-Id") UUID userId,
                                         @RequestBody PostRequest req) {
        return postService.createPost(userId, req).map(post -> Result.success(post));
    }

    @GetMapping("/{id}")
    public Mono<Result<Post>> getPost(@PathVariable UUID id) {
        return postService.getPost(id).map(post -> Result.success(post))
            .switchIfEmpty(Mono.just(Result.error(404, "Post not found")));
    }

    @PutMapping("/{id}")
    public Mono<Result<Post>> updatePost(@PathVariable UUID id, @RequestBody PostRequest req) {
        return postService.updatePost(id, req).map(post -> Result.success(post))
            .switchIfEmpty(Mono.just(Result.error(404, "Post not found")));
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> deletePost(@PathVariable UUID id) {
        return postService.deletePost(id).thenReturn(Result.success("deleted", null));
    }

    @GetMapping
    public Mono<Result<PaginatedResponse<Post>>> listPosts(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String authorId,
        @RequestParam(defaultValue = "published") String status) {
        // 前端发 1-based page，Spring Data PageRequest 是 0-based
        int zeroPage = Math.max(0, page - 1);
        Flux<Post> posts = (authorId != null)
            ? postService.listPostsByAuthor(UUID.fromString(authorId), zeroPage, size)
            : postService.listPublishedPosts(zeroPage, size);
        return posts.collectList()
            .map(list -> Result.success(PaginatedResponse.of(list, page, size, list.size())));
    }

    @PostMapping("/{id}/publish")
    public Mono<Result<Post>> publishPost(@PathVariable UUID id) {
        return postService.publishPost(id).then(postService.getPost(id)).map(Result::success);
    }
}
