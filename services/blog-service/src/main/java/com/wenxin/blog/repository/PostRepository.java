package com.wenxin.blog.repository;

import com.wenxin.blog.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PostRepository extends ReactiveCrudRepository<Post, UUID> {

    @Query("SELECT p.* FROM posts p WHERE p.author_id = :authorId ORDER BY p.created_at DESC")
    Flux<Post> findByAuthorId(UUID authorId, Pageable pageable);

    @Query("SELECT p.* FROM posts p WHERE p.status = 'published' ORDER BY p.published_at DESC NULLS LAST, p.created_at DESC")
    Flux<Post> findPublished(Pageable pageable);

    @Query("UPDATE posts SET view_count = view_count + 1 WHERE id = :id")
    Mono<Void> incrementViewCount(UUID id);

    @Query("UPDATE posts SET like_count = like_count + 1 WHERE id = :id")
    Mono<Void> incrementLikeCount(UUID id);

    @Query("UPDATE posts SET like_count = GREATEST(like_count - 1, 0) WHERE id = :id")
    Mono<Void> decrementLikeCount(UUID id);

    @Query("UPDATE posts SET comment_count = comment_count + 1 WHERE id = :id")
    Mono<Void> incrementCommentCount(UUID id);

    Flux<Post> findByStatus(String status, Pageable pageable);

    @Query("SELECT p.* FROM posts p WHERE p.status = 'published' AND p.title ILIKE '%' || :keyword || '%' ORDER BY p.published_at DESC NULLS LAST, p.created_at DESC")
    Flux<Post> searchByKeyword(String keyword, Pageable pageable);
}
