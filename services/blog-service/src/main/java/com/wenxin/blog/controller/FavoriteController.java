package com.wenxin.blog.controller;

import com.wenxin.blog.dto.Result;
import com.wenxin.blog.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/posts/{postId}/favorite")
    public Mono<Result<Boolean>> toggleFavorite(@PathVariable UUID postId,
                                                @RequestHeader("X-User-Id") UUID userId) {
        return favoriteService.toggleFavorite(userId, postId).map(Result::success);
    }

    @GetMapping("/posts/{postId}/favorited")
    public Mono<Result<Boolean>> isFavorited(@PathVariable UUID postId,
                                             @RequestHeader("X-User-Id") UUID userId) {
        return favoriteService.isFavorited(userId, postId).map(Result::success);
    }
}
