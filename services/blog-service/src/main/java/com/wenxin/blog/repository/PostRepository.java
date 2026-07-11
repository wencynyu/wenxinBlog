package com.wenxin.blog.repository;

import com.wenxin.blog.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PostRepository extends ReactiveCrudRepository<Post, UUID> {

    @Query("SELECT * FROM posts WHERE author_id = :authorId ORDER BY created_at DESC")
    Flux<Post> findByAuthorId(UUID authorId, Pageable pageable);

    @Query("SELECT * FROM posts WHERE status = 'published' ORDER BY published_at DESC, created_at DESC")
    Flux<Post> findPublished(Pageable pageable);

    @Query("UPDATE posts SET view_count = view_count + 1 WHERE id = :id")
    Mono<Void> incrementViewCount(UUID id);

    Flux<Post> findByStatus(String status, Pageable pageable);

    @Query("SELECT * FROM posts WHERE status = 'published' AND title ILIKE '%' || :keyword || '%' ORDER BY published_at DESC, created_at DESC")
    Flux<Post> searchByKeyword(String keyword, Pageable pageable);
}
