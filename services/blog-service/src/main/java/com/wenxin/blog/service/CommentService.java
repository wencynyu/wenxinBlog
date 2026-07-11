package com.wenxin.blog.service;

import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.repository.CommentRepository;
import com.wenxin.blog.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public Mono<Comment> createComment(UUID postId, UUID authorId, CommentRequest req) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setParentId(req.getParentId());
        comment.setContent(req.getContent());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        return commentRepository.save(comment).flatMap(saved ->
            postRepository.findById(postId).flatMap(post -> {
                post.setCommentCount(post.getCommentCount() + 1);
                return postRepository.save(post).thenReturn(saved);
            })
        );
    }

    public Flux<Comment> listComments(UUID postId) {
        return commentRepository.findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(postId);
    }

    public Flux<Comment> listReplies(UUID parentId) {
        return commentRepository.findByParentIdOrderByCreatedAtAsc(parentId);
    }

    public Mono<Void> deleteComment(UUID id) {
        return commentRepository.findById(id).flatMap(comment ->
            commentRepository.delete(comment).then()
        );
    }
}
