package com.wenxin.blog.controller;

import com.wenxin.blog.dto.PaginatedResponse;
import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.dto.Result;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public Mono<Result<Post>> createPost(@RequestHeader("X-User-Id") UUID userId,
                                         @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
                                         @RequestBody PostRequest req) {
        return postService.createPost(userId, req, permissions).map(post -> Result.success(post));
    }

    @GetMapping("/{id}")
    public Mono<Result<Post>> getPost(@PathVariable UUID id) {
        return postService.getPost(id).map(post -> Result.success(post))
            .switchIfEmpty(Mono.just(Result.error(404, "Post not found")));
    }

    @PutMapping("/{id}")
    public Mono<Result<Post>> updatePost(@RequestHeader("X-User-Id") UUID userId,
                                         @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
                                         @PathVariable UUID id, @RequestBody PostRequest req) {
        return postService.updatePost(userId, id, req, permissions).map(post -> Result.success(post))
            .switchIfEmpty(Mono.just(Result.error(404, "Post not found")));
    }

    @DeleteMapping("/{id}")
    public Mono<Result<Void>> deletePost(@RequestHeader("X-User-Id") UUID userId,
                                         @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
                                         @PathVariable UUID id) {
        return postService.deletePost(userId, id, permissions).thenReturn(Result.success("deleted", null));
    }

    @GetMapping
    public Mono<Result<PaginatedResponse<Post>>> listPosts(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String authorId,
        @RequestParam(required = false) String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder,
        @RequestParam(required = false) String tag) {
        // 前端发 1-based page，Spring Data PageRequest 是 0-based
        int zeroPage = Math.max(0, page - 1);
        if (authorId != null) {
            // 作者主页列表：保持 created_at 倒序（前端恒请求 createdAt），单页展示无需精确 total
            return postService.listPostsByAuthor(UUID.fromString(authorId), zeroPage, pageSize)
                .collectList()
                .map(list -> Result.success(PaginatedResponse.of(list, page, pageSize, list.size())));
        }
        // 公开列表：支持 sortBy(likeCount/commentCount/createdAt...) / sortOrder / tag，返回真实 total
        return postService.listPublishedPosts(zeroPage, pageSize, sortBy, sortOrder, tag)
            .map(r -> Result.success(PaginatedResponse.of(r.items(), page, pageSize, r.total())));
    }

    @PostMapping("/{id}/publish")
    public Mono<Result<Post>> publishPost(@RequestHeader("X-User-Id") UUID userId,
                                          @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
                                          @PathVariable UUID id) {
        return postService.publishPost(userId, id, permissions).then(postService.getPost(id)).map(Result::success);
    }

    @PostMapping("/{id}/feature")
    public Mono<Result<Post>> featurePost(@RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions,
                                          @PathVariable UUID id) {
        return postService.featurePost(id, permissions).map(Result::success)
            .switchIfEmpty(Mono.just(Result.error(404, "Post not found")));
    }
}
