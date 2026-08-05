package com.wenxin.blog.service;

import com.wenxin.blog.repository.PostFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final PostFavoriteRepository favoriteRepository;

    public Mono<Boolean> toggleFavorite(UUID userId, UUID postId) {
        return favoriteRepository.existsByUserIdAndPostId(userId, postId).flatMap(exists -> {
            if (exists > 0) {
                return favoriteRepository.deleteByUserIdAndPostId(userId, postId).thenReturn(false);
            } else {
                return favoriteRepository.addFavorite(userId, postId).thenReturn(true);
            }
        });
    }

    public Mono<Boolean> isFavorited(UUID userId, UUID postId) {
        return favoriteRepository.existsByUserIdAndPostId(userId, postId).map(count -> count > 0);
    }
}
