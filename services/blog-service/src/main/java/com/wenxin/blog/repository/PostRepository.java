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

    // --- 带 author 信息的查询（join authors 缓存表）---

    @Query("SELECT p.*, a.username AS author_username, a.display_name AS author_display_name, a.avatar_url AS author_avatar_url " +
           "FROM posts p LEFT JOIN authors a ON p.author_id = a.id " +
           "WHERE p.status = 'published' ORDER BY p.published_at DESC NULLS LAST, p.created_at DESC")
    Flux<Post> findPublishedWithAuthor(Pageable pageable);

    @Query("SELECT p.*, a.username AS author_username, a.display_name AS author_display_name, a.avatar_url AS author_avatar_url " +
           "FROM posts p LEFT JOIN authors a ON p.author_id = a.id " +
           "WHERE p.id = :id")
    Mono<Post> findByIdWithAuthor(UUID id);

    @Query("SELECT p.*, a.username AS author_username, a.display_name AS author_display_name, a.avatar_url AS author_avatar_url " +
           "FROM posts p LEFT JOIN authors a ON p.author_id = a.id " +
           "WHERE p.author_id = :authorId ORDER BY p.created_at DESC")
    Flux<Post> findByAuthorIdWithAuthor(UUID authorId, Pageable pageable);
}
