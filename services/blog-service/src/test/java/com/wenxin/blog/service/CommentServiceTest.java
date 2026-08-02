package com.wenxin.blog.service;

import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.repository.CommentRepository;
import com.wenxin.blog.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private CommentService commentService;

    private UUID postId;
    private UUID authorId;
    private CommentRequest commentRequest;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        authorId = UUID.randomUUID();
        commentRequest = new CommentRequest();
        commentRequest.setContent("Test comment content");
        commentRequest.setParentId(null);
    }

    @Test
    void testCreateComment_TopLevel() {
        Comment savedComment = new Comment();
        savedComment.setId(UUID.randomUUID());
        savedComment.setPostId(postId);
        savedComment.setAuthorId(authorId);
        savedComment.setContent("Test comment content");
        savedComment.setParentId(null);
        savedComment.setCreatedAt(LocalDateTime.now());

        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(savedComment));
        when(postRepository.incrementCommentCount(postId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest, "comment:create"))
                .expectNextMatches(comment -> {
                    return postId.equals(comment.getPostId()) &&
                            authorId.equals(comment.getAuthorId()) &&
                            "Test comment content".equals(comment.getContent()) &&
                            comment.getParentId() == null;
                })
                .verifyComplete();

        verify(commentRepository, times(1)).save(any(Comment.class));
        verify(postRepository, times(1)).incrementCommentCount(postId);
    }

    @Test
    void testCreateComment_Reply() {
        UUID parentId = UUID.randomUUID();
        commentRequest.setParentId(parentId);

        Comment savedComment = new Comment();
        savedComment.setId(UUID.randomUUID());
        savedComment.setPostId(postId);
        savedComment.setAuthorId(authorId);
        savedComment.setContent("Test reply");
        savedComment.setParentId(parentId);
        savedComment.setCreatedAt(LocalDateTime.now());

        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(savedComment));
        when(postRepository.incrementCommentCount(postId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest, "comment:create"))
                .expectNextMatches(comment -> {
                    return parentId.equals(comment.getParentId());
                })
                .verifyComplete();

        verify(commentRepository, times(1)).save(any(Comment.class));
        verify(postRepository, times(1)).incrementCommentCount(postId);
    }

    @Test
    void testCreateComment_IncrementsPostCommentCount() {
        Comment savedComment = new Comment();
        savedComment.setId(UUID.randomUUID());
        savedComment.setPostId(postId);
        savedComment.setCreatedAt(LocalDateTime.now());

        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(savedComment));
        when(postRepository.incrementCommentCount(postId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest, "comment:create"))
                .expectNextCount(1)
                .verifyComplete();

        verify(postRepository, times(1)).incrementCommentCount(postId);
    }

    @Test
    void testListComments() {
        Comment comment1 = new Comment();
        comment1.setId(UUID.randomUUID());
        comment1.setPostId(postId);
        comment1.setParentId(null);

        Comment comment2 = new Comment();
        comment2.setId(UUID.randomUUID());
        comment2.setPostId(postId);
        comment2.setParentId(null);

        when(commentRepository.findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(postId))
                .thenReturn(Flux.just(comment1, comment2));

        StepVerifier.create(commentService.listComments(postId))
                .expectNextCount(2)
                .verifyComplete();

        verify(commentRepository, times(1)).findByPostIdAndParentIdIsNullOrderByCreatedAtAsc(postId);
    }

    @Test
    void testListReplies() {
        UUID parentId = UUID.randomUUID();

        Comment reply1 = new Comment();
        reply1.setId(UUID.randomUUID());
        reply1.setParentId(parentId);

        Comment reply2 = new Comment();
        reply2.setId(UUID.randomUUID());
        reply2.setParentId(parentId);

        when(commentRepository.findByParentIdOrderByCreatedAtAsc(parentId))
                .thenReturn(Flux.just(reply1, reply2));

        StepVerifier.create(commentService.listReplies(parentId))
                .expectNextCount(2)
                .verifyComplete();

        verify(commentRepository, times(1)).findByParentIdOrderByCreatedAtAsc(parentId);
    }

    @Test
    void testDeleteComment() {
        UUID commentId = UUID.randomUUID();

        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setContent("Test comment");

        when(commentRepository.findById(commentId)).thenReturn(Mono.just(comment));
        when(commentRepository.deleteCommentSubtreeAndDecrementCount(commentId, postId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(authorId, commentId, "comment:delete:own"))
                .verifyComplete();

        verify(commentRepository, times(1)).findById(commentId);
        verify(commentRepository, times(1)).deleteCommentSubtreeAndDecrementCount(commentId, postId);
    }

    @Test
    void testDeleteComment_NotFound() {
        UUID commentId = UUID.randomUUID();

        when(commentRepository.findById(commentId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(authorId, commentId, "comment:delete:own"))
                .verifyComplete();

        verify(commentRepository, times(1)).findById(commentId);
        verify(commentRepository, never()).deleteCommentSubtreeAndDecrementCount(any(), any());
    }

    @Test
    void testCreateComment_WithoutPermission_Forbidden() {
        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testDeleteComment_NonOwnerWithoutModerate_Forbidden() {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setAuthorId(UUID.randomUUID());

        when(commentRepository.findById(commentId)).thenReturn(Mono.just(comment));

        StepVerifier.create(commentService.deleteComment(UUID.randomUUID(), commentId, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(commentRepository, never()).deleteCommentSubtreeAndDecrementCount(any(), any());
    }

    @Test
    void testDeleteComment_NonOwnerWithModerate_Allowed() {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setAuthorId(UUID.randomUUID());

        when(commentRepository.findById(commentId)).thenReturn(Mono.just(comment));
        when(commentRepository.deleteCommentSubtreeAndDecrementCount(commentId, postId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(UUID.randomUUID(), commentId, "comment:moderate"))
                .verifyComplete();

        verify(commentRepository, times(1)).deleteCommentSubtreeAndDecrementCount(commentId, postId);
    }

    @Test
    void testModerateComment_WithoutPermission_Forbidden() {
        UUID commentId = UUID.randomUUID();

        StepVerifier.create(commentService.moderateComment(commentId, "HIDDEN", ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testModerateComment_Allowed() {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setStatus("APPROVED");

        Comment hiddenComment = new Comment();
        hiddenComment.setId(commentId);
        hiddenComment.setPostId(postId);
        hiddenComment.setStatus("HIDDEN");

        when(commentRepository.findById(commentId)).thenReturn(Mono.just(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(hiddenComment));

        StepVerifier.create(commentService.moderateComment(commentId, "HIDDEN", "comment:moderate"))
                .expectNextMatches(c -> "HIDDEN".equals(c.getStatus()))
                .verifyComplete();
    }

    @Test
    void testModerateComment_InvalidStatus_Rejected() {
        UUID commentId = UUID.randomUUID();

        StepVerifier.create(commentService.moderateComment(commentId, "SPAM", "comment:moderate"))
                .expectError(ResponseStatusException.class)
                .verify();
    }
}
