package com.wenxin.blog.service;

import com.wenxin.blog.common.Permissions;
import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.repository.CommentRepository;
import com.wenxin.blog.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public Mono<Comment> createComment(UUID postId, UUID authorId, CommentRequest req, String permissions) {
        if (!Permissions.has(permissions, "comment:create")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need comment:create"));
        }
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setParentId(req.getParentId());
        comment.setContent(req.getContent());
        comment.setStatus("APPROVED");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        return commentRepository.save(comment)
                .flatMap(saved -> postRepository.incrementCommentCount(postId).thenReturn(saved));
    }

    public Flux<Comment> listComments(UUID postId) {
        return commentRepository.findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(postId);
    }

    public Flux<Comment> listReplies(UUID parentId) {
        return commentRepository.findByParentIdOrderByCreatedAtAsc(parentId);
    }

    public Mono<Void> deleteComment(UUID userId, UUID id, String permissions) {
        return commentRepository.findById(id).flatMap(comment -> {
            boolean owner = comment.getAuthorId().equals(userId);
            boolean allowed = owner
                    ? Permissions.has(permissions, "comment:delete:own")
                    : Permissions.has(permissions, "comment:moderate");
            if (!allowed) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need comment:delete:own or comment:moderate"));
            }
            return commentRepository.deleteCommentSubtreeAndDecrementCount(id, comment.getPostId());
        });
    }

    /** 审核评论：moderator 可将评论状态改为 HIDDEN/APPROVED。 */
    public Mono<Comment> moderateComment(UUID id, String status, String permissions) {
        if (!Permissions.has(permissions, "comment:moderate")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need comment:moderate"));
        }
        String target = status != null ? status.toUpperCase() : "HIDDEN";
        if (!"HIDDEN".equals(target) && !"APPROVED".equals(target)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be HIDDEN or APPROVED"));
        }
        return commentRepository.findById(id)
                .flatMap(comment -> {
                    comment.setStatus(target);
                    comment.setUpdatedAt(LocalDateTime.now());
                    return commentRepository.save(comment);
                });
    }
}
