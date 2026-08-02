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
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private R2dbcEntityTemplate r2dbc;


    @Mock
    private BlogEventPublisher blogEventPublisher;

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

    /**
     * 桩 PostService.fillAuthorAndTags 内部的 r2dbc DatabaseClient 链（authors 查询 + tags 查询），
     * 让依赖 DB 的 getPost / listPostsByAuthor 单测不必起真实数据库。
     */
    @SuppressWarnings("unchecked")
    private void stubFillAuthorAndTags() {
        DatabaseClient db = mock(DatabaseClient.class);
        when(r2dbc.getDatabaseClient()).thenReturn(db);

        DatabaseClient.GenericExecuteSpec authorSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        DatabaseClient.GenericExecuteSpec tagsSpec = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<Post.AuthorInfo> authorRows = mock(RowsFetchSpec.class);
        RowsFetchSpec<String> tagRows = mock(RowsFetchSpec.class);

        when(db.sql(contains("FROM authors"))).thenReturn(authorSpec);
        when(authorSpec.bind(eq("authorId"), any())).thenReturn(authorSpec);
        when(authorSpec.map(any(Function.class))).thenReturn(authorRows);
        when(authorRows.one()).thenReturn(Mono.just(new Post.AuthorInfo("a", "user", "user", null)));

        when(db.sql(contains("JOIN post_tags"))).thenReturn(tagsSpec);
        when(tagsSpec.bind(eq("postId"), any())).thenReturn(tagsSpec);
        when(tagsSpec.map(any(Function.class))).thenReturn(tagRows);
        when(tagRows.all()).thenReturn(Flux.empty());
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

        StepVerifier.create(postService.createPost(authorId, postRequest, "post:create"))
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

        StepVerifier.create(postService.createPost(authorId, postRequest, "post:create"))
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
        existingPost.setAuthorId(authorId);
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

        StepVerifier.create(postService.updatePost(authorId, postId, postRequest, "post:update:own"))
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
        existingPost.setAuthorId(authorId);
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

        StepVerifier.create(postService.updatePost(authorId, postId, postRequest, "post:update:own"))
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
        existingPost.setAuthorId(authorId);
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

        StepVerifier.create(postService.updatePost(authorId, postId, postRequest, "post:update:own"))
                .expectNextMatches(post -> {
                    return "PUBLISHED".equals(post.getStatus()) &&
                            post.getPublishedAt() != null;
                })
                .verifyComplete();
    }

    @Test
    void testGetPost_IncrementsViewCount() {
        stubFillAuthorAndTags();
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setTitle("Test Title");
        existingPost.setViewCount(5);

        when(postRepository.incrementViewCount(postId)).thenReturn(Mono.empty());
        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));

        StepVerifier.create(postService.getPost(postId))
                .expectNextMatches(post -> post.getViewCount() == 5)
                .verifyComplete();

        verify(postRepository, times(1)).incrementViewCount(postId);
        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void testDeletePost() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(authorId);

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.deleteById(postId)).thenReturn(Mono.empty());

        StepVerifier.create(postService.deletePost(authorId, postId, "post:delete:own"))
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).deleteById(postId);
    }

    @Test
    void testSortColumnOf_whitelistAndDefault() {
        // 已知 sortBy → 对应列名
        assertEquals("like_count", PostService.sortColumnOf("likeCount"));
        assertEquals("comment_count", PostService.sortColumnOf("commentCount"));
        assertEquals("created_at", PostService.sortColumnOf("createdAt"));
        assertEquals("updated_at", PostService.sortColumnOf("updatedAt"));
        assertEquals("view_count", PostService.sortColumnOf("viewCount"));
        // null / 未知值（含注入企图）→ 安全回落 published_at，绝不原样拼接
        assertEquals("published_at", PostService.sortColumnOf(null));
        assertEquals("published_at", PostService.sortColumnOf("like_count; DROP TABLE posts; --"));
    }

    @Test
    void testListPostsByAuthor() {
        stubFillAuthorAndTags();
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
        existingPost.setAuthorId(authorId);
        existingPost.setStatus("DRAFT");
        existingPost.setPublishedAt(null);

        Post publishedPost = new Post();
        publishedPost.setId(postId);
        publishedPost.setStatus("PUBLISHED");
        publishedPost.setPublishedAt(LocalDateTime.now());
        publishedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(publishedPost));

        StepVerifier.create(postService.publishPost(authorId, postId, "post:publish"))
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testUpdatePost_PostNotFound() {
        when(postRepository.findById(postId)).thenReturn(Mono.empty());

        StepVerifier.create(postService.updatePost(authorId, postId, postRequest, "post:update:own"))
                .verifyComplete();

        verify(postRepository, times(1)).findById(postId);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void testCreatePost_WithoutPermission_Forbidden() {
        StepVerifier.create(postService.createPost(authorId, postRequest, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void testUpdatePost_OwnerWithoutOwnPermission_Forbidden() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(authorId);
        existingPost.setStatus("DRAFT");
        existingPost.setCreatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));

        StepVerifier.create(postService.updatePost(authorId, postId, postRequest, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void testUpdatePost_NonOwnerWithoutPermission_Forbidden() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(UUID.randomUUID());
        existingPost.setStatus("DRAFT");
        existingPost.setCreatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));

        StepVerifier.create(postService.updatePost(UUID.randomUUID(), postId, postRequest, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void testUpdatePost_NonOwnerWithAnyPermission_Allowed() {
        UUID other = UUID.randomUUID();
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(other);
        existingPost.setTitle("Old");
        existingPost.setStatus("DRAFT");
        existingPost.setCreatedAt(LocalDateTime.now());

        Post updatedPost = new Post();
        updatedPost.setId(postId);
        updatedPost.setAuthorId(other);
        updatedPost.setTitle("Updated Title");
        updatedPost.setStatus("DRAFT");
        updatedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(updatedPost));

        StepVerifier.create(postService.updatePost(UUID.randomUUID(), postId, postRequest, "post:update:any"))
                .expectNextMatches(p -> "Updated Title".equals(p.getTitle()))
                .verifyComplete();

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testDeletePost_NonOwnerWithoutPermission_Forbidden() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(UUID.randomUUID());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));

        StepVerifier.create(postService.deletePost(UUID.randomUUID(), postId, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(postRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void testDeletePost_NonOwnerWithAnyPermission_Allowed() {
        UUID other = UUID.randomUUID();
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(other);

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.deleteById(postId)).thenReturn(Mono.empty());

        StepVerifier.create(postService.deletePost(UUID.randomUUID(), postId, "post:delete:any"))
                .verifyComplete();

        verify(postRepository, times(1)).deleteById(postId);
    }

    @Test
    void testPublishPost_NonOwnerWithoutUpdateAny_Forbidden() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(UUID.randomUUID());
        existingPost.setStatus("DRAFT");

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));

        // 只有 post:publish、缺 post:update:any → 非 owner 仍 403
        StepVerifier.create(postService.publishPost(UUID.randomUUID(), postId, "post:publish"))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void testPublishPost_NonOwnerWithPublishAndUpdateAny_Allowed() {
        UUID other = UUID.randomUUID();
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setAuthorId(other);
        existingPost.setStatus("DRAFT");

        Post publishedPost = new Post();
        publishedPost.setId(postId);
        publishedPost.setStatus("PUBLISHED");
        publishedPost.setPublishedAt(LocalDateTime.now());
        publishedPost.setUpdatedAt(LocalDateTime.now());

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(publishedPost));

        StepVerifier.create(postService.publishPost(UUID.randomUUID(), postId, "post:publish,post:update:any"))
                .verifyComplete();

        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void testFeaturePost_WithoutPermission_Forbidden() {
        StepVerifier.create(postService.featurePost(postId, ""))
                .expectError(ResponseStatusException.class)
                .verify();

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void testFeaturePost_Allowed() {
        Post existingPost = new Post();
        existingPost.setId(postId);
        existingPost.setFeatured(false);

        Post featuredPost = new Post();
        featuredPost.setId(postId);
        featuredPost.setFeatured(true);

        when(postRepository.findById(postId)).thenReturn(Mono.just(existingPost));
        when(postRepository.save(any(Post.class))).thenReturn(Mono.just(featuredPost));

        StepVerifier.create(postService.featurePost(postId, "post:feature"))
                .expectNextMatches(p -> Boolean.TRUE.equals(p.getFeatured()))
                .verifyComplete();
    }
}
