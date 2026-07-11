package com.wenxin.blog.controller;

import com.wenxin.blog.dto.Result;
import com.wenxin.blog.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/posts/{postId}/like")
    public Mono<Result<Boolean>> toggleLike(@PathVariable UUID postId,
                                             @RequestHeader("X-User-Id") UUID userId) {
        return likeService.toggleLike(userId, postId).map(Result::success);
    }

    @GetMapping("/posts/{postId}/liked")
    public Mono<Result<Boolean>> isLiked(@PathVariable UUID postId,
                                         @RequestHeader("X-User-Id") UUID userId) {
        return likeService.isLiked(userId, postId).map(Result::success);
    }
}
