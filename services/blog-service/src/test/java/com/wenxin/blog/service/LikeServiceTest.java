package com.wenxin.blog.service;

import com.wenxin.blog.entity.Post;
import com.wenxin.blog.repository.PostLikeRepository;
import com.wenxin.blog.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private PostLikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private LikeService likeService;

    private UUID userId;
    private UUID postId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
    }

    @Test
    void testToggleLike_NotLiked_ToLiked() {
        Post post = new Post();
        post.setId(postId);
        post.setLikeCount(5);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setLikeCount(6);

        when(likeRepository.existsByUserIdAndPostId(userId, postId)).thenReturn(Mono.just(0L));
        when(likeRepository.addLike(userId, postId)).thenReturn(Mono.empty());
        when(postRepository.findById(postId)).thenReturn(Mono.just(post));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(likeService.toggleLike(userId, postId))
                .expectNext(true)
                .verifyComplete();

        verify(likeRepository, times(1)).existsByUserIdAndPostId(userId, postId);
        verify(likeRepository, times(1)).addLike(userId, postId);
        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testToggleLike_Liked_ToUnliked() {
        Post post = new Post();
        post.setId(postId);
        post.setLikeCount(5);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setLikeCount(4);

        when(likeRepository.existsByUserIdAndPostId(userId, postId)).thenReturn(Mono.just(1L));
        when(likeRepository.deleteByUserIdAndPostId(userId, postId)).thenReturn(Mono.empty());
        when(postRepository.findById(postId)).thenReturn(Mono.just(post));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(likeService.toggleLike(userId, postId))
                .expectNext(false)
                .verifyComplete();

        verify(likeRepository, times(1)).existsByUserIdAndPostId(userId, postId);
        verify(likeRepository, times(1)).deleteByUserIdAndPostId(userId, postId);
        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testToggleLike_LikeCountNeverNegative() {
        Post post = new Post();
        post.setId(postId);
        post.setLikeCount(0);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setLikeCount(0);

        when(likeRepository.existsByUserIdAndPostId(userId, postId)).thenReturn(Mono.just(1L));
        when(likeRepository.deleteByUserIdAndPostId(userId, postId)).thenReturn(Mono.empty());
        when(postRepository.findById(postId)).thenReturn(Mono.just(post));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(likeService.toggleLike(userId, postId))
                .expectNext(false)
                .verifyComplete();

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testIsLiked_True() {
        when(likeRepository.existsByUserIdAndPostId(userId, postId)).thenReturn(Mono.just(1L));

        StepVerifier.create(likeService.isLiked(userId, postId))
                .expectNext(true)
                .verifyComplete();

        verify(likeRepository, times(1)).existsByUserIdAndPostId(userId, postId);
    }

    @Test
    void testIsLiked_False() {
        when(likeRepository.existsByUserIdAndPostId(userId, postId)).thenReturn(Mono.just(0L));

        StepVerifier.create(likeService.isLiked(userId, postId))
                .expectNext(false)
                .verifyComplete();

        verify(likeRepository, times(1)).existsByUserIdAndPostId(userId, postId);
    }
}
