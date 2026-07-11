package com.wenxin.blog.repository;

import com.wenxin.blog.entity.Comment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface CommentRepository extends ReactiveCrudRepository<Comment, UUID> {

    Flux<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    Flux<Comment> findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(UUID postId);

    Flux<Comment> findByParentIdOrderByCreatedAtAsc(UUID parentId);

    @Query("SELECT COUNT(*) FROM comments WHERE post_id = :postId")
    Mono<Long> countByPostId(UUID postId);
}
