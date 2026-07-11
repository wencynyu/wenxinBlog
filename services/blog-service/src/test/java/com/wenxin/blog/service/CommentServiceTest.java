package com.wenxin.blog.service;

import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.repository.CommentRepository;
import com.wenxin.blog.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

        Post post = new Post();
        post.setId(postId);
        post.setCommentCount(0);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setCommentCount(1);

        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(savedComment));
        when(postRepository.findById(postId)).thenReturn(Mono.just(post));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest))
                .expectNextMatches(comment -> {
                    return postId.equals(comment.getPostId()) &&
                            authorId.equals(comment.getAuthorId()) &&
                            "Test comment content".equals(comment.getContent()) &&
                            comment.getParentId() == null;
                })
                .verifyComplete();

        verify(commentRepository, times(1)).save(any(Comment.class));
        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
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

        Post post = new Post();
        post.setId(postId);
        post.setCommentCount(0);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setCommentCount(1);

        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(savedComment));
        when(postRepository.findById(postId)).thenReturn(Mono.just(post));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest))
                .expectNextMatches(comment -> {
                    return parentId.equals(comment.getParentId());
                })
                .verifyComplete();

        verify(commentRepository, times(1)).save(any(Comment.class));
    }

    @Test
    void testCreateComment_IncrementsPostCommentCount() {
        Comment savedComment = new Comment();
        savedComment.setId(UUID.randomUUID());
        savedComment.setPostId(postId);
        savedComment.setCreatedAt(LocalDateTime.now());

        Post post = new Post();
        post.setId(postId);
        post.setCommentCount(5);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setCommentCount(6);

        when(commentRepository.save(any(Comment.class))).thenReturn(Mono.just(savedComment));
        when(postRepository.findById(postId)).thenReturn(Mono.just(post));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(commentService.createComment(postId, authorId, commentRequest))
                .expectNextCount(1)
                .verifyComplete();

        verify(postRepository, times(1)).save(any(Post.class));
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
        comment.setContent("Test comment");

        when(commentRepository.findById(commentId)).thenReturn(Mono.just(comment));
        when(commentRepository.delete(any(Comment.class))).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(commentId))
                .verifyComplete();

        verify(commentRepository, times(1)).findById(commentId);
        verify(commentRepository, times(1)).delete(any(Comment.class));
    }

    @Test
    void testDeleteComment_NotFound() {
        UUID commentId = UUID.randomUUID();

        when(commentRepository.findById(commentId)).thenReturn(Mono.empty());

        StepVerifier.create(commentService.deleteComment(commentId))
                .verifyComplete();

        verify(commentRepository, times(1)).findById(commentId);
        verify(commentRepository, never()).delete(any(Comment.class));
    }
}
