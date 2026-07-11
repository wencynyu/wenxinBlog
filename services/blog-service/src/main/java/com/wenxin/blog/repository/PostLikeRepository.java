package com.wenxin.blog.repository;

import com.wenxin.blog.entity.PostLike;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PostLikeRepository extends ReactiveCrudRepository<PostLike, UUID> {
    @Query("SELECT COUNT(*) FROM post_likes WHERE user_id = :userId AND post_id = :postId")
    Mono<Long> existsByUserIdAndPostId(UUID userId, UUID postId);

    Mono<Void> deleteByUserIdAndPostId(UUID userId, UUID postId);

    @Query("INSERT INTO post_likes (user_id, post_id) VALUES (:userId, :postId)")
    Mono<Void> addLike(UUID userId, UUID postId);
}
