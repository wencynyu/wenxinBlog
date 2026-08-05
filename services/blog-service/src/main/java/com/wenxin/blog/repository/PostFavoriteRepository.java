package com.wenxin.blog.repository;

import com.wenxin.blog.entity.PostFavorite;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PostFavoriteRepository extends ReactiveCrudRepository<PostFavorite, UUID> {
    @Query("SELECT COUNT(*) FROM post_favorites WHERE user_id = :userId AND post_id = :postId")
    Mono<Long> existsByUserIdAndPostId(UUID userId, UUID postId);

    Mono<Void> deleteByUserIdAndPostId(UUID userId, UUID postId);

    @Query("INSERT INTO post_favorites (user_id, post_id) VALUES (:userId, :postId) ON CONFLICT (user_id, post_id) DO NOTHING")
    Mono<Integer> addFavorite(UUID userId, UUID postId);
}
