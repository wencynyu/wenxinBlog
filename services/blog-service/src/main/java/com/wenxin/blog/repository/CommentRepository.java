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

    /**
     * 原子删除评论子树（含级联回复）并按删除行数回减 posts.comment_count。
     * 递归 CTE 收集 :id 及其全部后代，一条语句内 DELETE + UPDATE，避免 read-modify-write 丢计数。
     */
    @Query("WITH RECURSIVE subtree AS ("
            + "SELECT id FROM comments WHERE id = :id "
            + "UNION ALL "
            + "SELECT c.id FROM comments c JOIN subtree s ON c.parent_id = s.id"
            + "), deleted AS ("
            + "DELETE FROM comments c USING subtree s WHERE c.id = s.id RETURNING c.id"
            + ") "
            + "UPDATE posts SET comment_count = GREATEST(comment_count - (SELECT COUNT(*) FROM deleted), 0) WHERE id = :postId")
    Mono<Void> deleteCommentSubtreeAndDecrementCount(UUID id, UUID postId);
}
