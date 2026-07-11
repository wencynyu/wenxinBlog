package com.wenxin.blog.service;

import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.repository.PostRepository;
import com.wenxin.blog.repository.TagRepository;
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
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private PostService postService;

    private UUID authorId;
    private UUID postId;
    private PostRequest postRequest;

    @BeforeEach
    void setUp() {
        authorId = UUID.randomUUID();
        postId = UUID.randomUUID();
        postRequest = new PostRequest();
        postRequest.setTitle("Test Title");
        postRequest.setContent("Test Content");
        postRequest.setSummary("Test Summary");
    }

    @Test
    void testCreatePost_DefaultStatus() {
        // status=null should default to "DRAFT"
        postRequest.setStatus(null);

        Post savedPost = new Post();
        savedPost.setId(postId);
        savedPost.setAuthorId(authorId);
        savedPost.setTitle("Test Title");
        savedPost.setStatus("DRAFT");
        savedPost.setCreatedAt(LocalDateTime.now());

        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(savedPost));

        StepVerifier.create(postService.createPost(authorId, postRequest))
                .expectNextMatches(post -> {
                    return "DRAFT".equals(post.getStatus()) &&
                            authorId.equals(post.getAuthorId()) &&
                            "Test Title".equals(post.getTitle());
                })
                .verifyComplete();

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testCreatePost_WithPublishedStatus() {
        postRequest.setStatus("PUBLISHED");

        Post savedPost = new Post();
        savedPost.setId(postId);
        savedPost.setAuthorId(authorId);
        savedPost.setTitle("Test Title");
        savedPost.setStatus("PUBLISHED");
        savedPost.setPublishedAt(LocalDateTime.now());
        savedPost.setCreatedAt(LocalDateTime.now());

        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(savedPost));

        StepVerifier.create(postService.createPost(authorId, postRequest))
                .expectNextMatches(post -> {
                    return "PUBLISHED".equals(post.getStatus()) &&
                            post.getPublishedAt() != null;
                })
                .verifyComplete();

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testUpdatePost_FullUpdate() {
        postRequest.setTitle("Updated Title");
        postRequest.setContent("Updated Content");
        postRequest.setSummary("Updated Summary");
        postRequest.setStatus("PUBLISHED");

        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setTitle("Old Title");
        existingPost.setContent("Old Content");
        existingPost.setSummary("Old Summary");
        existingPost.setStatus("DRAFT");
        existingPost.setCreatedAt(LocalDateTime.now());

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setTitle("Updated Title");
        updatedPost.setContent("Updated Content");
        updatedPost.setSummary("Updated Summary");
        updatedPost.setStatus("PUBLISHED");
        updatedPost.setPublishedAt(LocalDateTime.now());
        updatedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(postService.updatePost(postId, postRequest))
                .expectNextMatches(post -> {
                    return "Updated Title".equals(post.getTitle()) &&
                            "Updated Content".equals(post.getContent()) &&
                            "Updated Summary".equals(post.getSummary()) &&
                            "PUBLISHED".equals(post.getStatus()) &&
                            post.getPublishedAt() != null;
                })
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testUpdatePost_PartialUpdate() {
        postRequest.setTitle("Updated Title Only");

        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setTitle("Old Title");
        existingPost.setContent("Old Content");
        existingPost.setSummary("Old Summary");
        existingPost.setStatus("DRAFT");
        existingPost.setCreatedAt(LocalDateTime.now());

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setTitle("Updated Title Only");
        updatedPost.setContent("Old Content");
        updatedPost.setSummary("Old Summary");
        updatedPost.setStatus("DRAFT");
        updatedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(postService.updatePost(postId, postRequest))
                .expectNextMatches(post -> {
                    return "Updated Title Only".equals(post.getTitle()) &&
                            "Old Content".equals(post.getContent());
                })
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testUpdatePost_PublishSetsPublishedAt() {
        postRequest.setStatus("PUBLISHED");

        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setStatus("DRAFT");
        existingPost.setPublishedAt(null);
        existingPost.setCreatedAt(LocalDateTime.now());

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setStatus("PUBLISHED");
        updatedPost.setPublishedAt(LocalDateTime.now());
        updatedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(postService.updatePost(postId, postRequest))
                .expectNextMatches(post -> {
                    return "PUBLISHED".equals(post.getStatus()) &&
                            post.getPublishedAt() != null;
                })
                .verifyComplete();
    }

    @Test
    void testGetPost_IncrementsViewCount() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setTitle("Test Title");
        existingPost.setViewCount(5);

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setTitle("Test Title");
        updatedPost.setViewCount(6);

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(postService.getPost(postId))
                .expectNextMatches(post -> post.getViewCount() == 6)
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testDeletePost() {
        when(postRepository.deleteById(postId)).thenReturn(Mono.empty());

        StepVerifier.create(postService.deletePost(postId))
                .verifyComplete();

        verify(postRepository, times(1)).deleteById(postId);
    }

    @Test
    void testListPublishedPosts() {
        Post post1 = new Post();
        post1.setId(UUID.randomUUID());
        post1.setStatus("PUBLISHED");

        Post post2 = new Post();
        post2.setId(UUID.randomUUID());
        post2.setStatus("PUBLISHED");

        when(postRepository.findPublished(any())).thenReturn(Flux.just(post1, post2));

        StepVerifier.create(postService.listPublishedPosts(0, 20))
                .expectNextCount(2)
                .verifyComplete();

        verify(postRepository, times(1)).findPublished(any());
    }

    @Test
    void testListPostsByAuthor() {
        Post post1 = new Post();
        post1.setId(UUID.randomUUID());
        post1.setAuthorId(authorId);

        Post post2 = new Post();
        post2.setId(UUID.randomUUID());
        post2.setAuthorId(authorId);

        when(postRepository.findByAuthorId(eq(authorId), any())).thenReturn(Flux.just(post1, post2));

        StepVerifier.create(postService.listPostsByAuthor(authorId, 0, 20))
                .expectNextCount(2)
                .verifyComplete();

        verify(postRepository, times(1)).findByAuthorId(eq(authorId), any());
    }

    @Test
    void testPublishPost() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setStatus("DRAFT");
        existingPost.setPublishedAt(null);

        Post publishedPost = new Post();
        publishedPost.setId(postId);
        publishedPost.setStatus("PUBLISHED");
        publishedPost.setPublishedAt(LocalDateTime.now());
        publishedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(publishedPost));

        StepVerifier.create(postService.publishPost(postId))
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testUpdatePost_PostNotFound() {
        when(postRepository.findById(postId)).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(postId, postRequest))
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, never()).save(any(Post.class));
    }
}
