package com.wenxin.blog.service;

import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.repository.PostRepository;
import com.wenxin.blog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final R2dbcEntityTemplate r2dbc;
    private final SearchIndexService searchIndexService;

    public Mono<Post> createPost(UUID authorId, PostRequest req) {
        Post post = new Post();
        post.setAuthorId(authorId);
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setSummary(req.getSummary());
        post.setCoverImage(req.getCoverImage());
        post.setStatus(req.getStatus() != null ? req.getStatus() : "draft");
        if ("published".equalsIgnoreCase(post.getStatus())) {
            post.setPublishedAt(LocalDateTime.now());
        }
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.save(post)
                .flatMap(saved -> {
                    // 保存标签到 post_tags 表
                    Mono<Void> saveTags = (req.getTags() != null && !req.getTags().isEmpty())
                            ? saveTagsForPost(saved.getId(), req.getTags())
                            : Mono.empty();
                    return saveTags.thenReturn(saved);
                })
                .doOnNext(saved -> {
                    if ("published".equalsIgnoreCase(saved.getStatus())) {
                        searchIndexService.indexPost(saved);
                    }
                });
    }

    public Mono<Post> updatePost(UUID id, PostRequest req) {
        return postRepository.findById(id).flatMap(post -> {
            if (req.getTitle() != null) post.setTitle(req.getTitle());
            if (req.getContent() != null) post.setContent(req.getContent());
            if (req.getSummary() != null) post.setSummary(req.getSummary());
            if (req.getCoverImage() != null) post.setCoverImage(req.getCoverImage());
            if (req.getStatus() != null) {
                post.setStatus(req.getStatus());
                if ("published".equalsIgnoreCase(req.getStatus()) && post.getPublishedAt() == null) {
                    post.setPublishedAt(LocalDateTime.now());
                }
            }
            post.setUpdatedAt(LocalDateTime.now());
            return postRepository.save(post)
                    .flatMap(saved -> {
                        Mono<Void> updateTags = (req.getTags() != null)
                                ? replaceTagsForPost(saved.getId(), req.getTags())
                                : Mono.empty();
                        return updateTags.thenReturn(saved);
                    })
                    .doOnNext(saved -> {
                        if ("published".equalsIgnoreCase(saved.getStatus())) {
                            searchIndexService.indexPost(saved);
                        }
                    });
        });
    }

    public Mono<Post> getPost(UUID id) {
        return postRepository.findById(id)
                .flatMap(post -> {
                    post.setViewCount(post.getViewCount() + 1);
                    return postRepository.save(post).thenReturn(post);
                })
                .flatMap(this::fillAuthorAndTags);
    }

    public Mono<Void> deletePost(UUID id) {
        return postRepository.deleteById(id)
                .doOnSuccess(v -> searchIndexService.deletePost(id));
    }

    public Flux<Post> listPublishedPosts(int page, int size) {
        return postRepository.findPublished(PageRequest.of(page, size))
                .flatMap(this::fillAuthorAndTags);
    }

    public Flux<Post> listPostsByAuthor(UUID authorId, int page, int size) {
        return postRepository.findByAuthorId(authorId, PageRequest.of(page, size))
                .flatMap(this::fillAuthorAndTags);
    }

    public Mono<Void> publishPost(UUID id) {
        return postRepository.findById(id).flatMap(post -> {
            post.setStatus("published");
            post.setPublishedAt(LocalDateTime.now());
            post.setUpdatedAt(LocalDateTime.now());
            return postRepository.save(post).then();
        });
    }

    /**
     * 查 authors 表填充 author 对象 + 查 post_tags/tags 表填充 tags 数组。
     * 用 R2dbcEntityTemplate 手动映射（@Transient 字段不会被自动映射）。
     */
    private Mono<Post> fillAuthorAndTags(Post post) {
        if (post == null) return Mono.empty();

        // 查 author 信息
        Mono<Post.AuthorInfo> authorMono = r2dbc.getDatabaseClient()
                .sql("SELECT username, display_name, avatar_url FROM authors WHERE id = :authorId")
                .bind("authorId", post.getAuthorId())
                .map((row) -> new Post.AuthorInfo(
                        post.getAuthorId().toString(),
                        row.get("username", String.class),
                        row.get("display_name", String.class),
                        row.get("avatar_url", String.class)
                ))
                .one()
                .onErrorResume(e -> {
                    log.warn("Failed to fetch author for {}: {}", post.getAuthorId(), e.getMessage());
                    return Mono.just(new Post.AuthorInfo(
                            post.getAuthorId().toString(),
                            post.getAuthorId().toString().substring(0, 8),
                            post.getAuthorId().toString().substring(0, 8),
                            null
                    ));
                });

        // 查 tags
        Mono<List<String>> tagsMono = r2dbc.getDatabaseClient()
                .sql("SELECT t.name FROM tags t " +
                     "JOIN post_tags pt ON pt.tag_id = t.id " +
                     "WHERE pt.post_id = :postId")
                .bind("postId", post.getId())
                .map((row) -> row.get("name", String.class))
                .all()
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Failed to fetch tags for post {}: {}", post.getId(), e.getMessage());
                    return Mono.just(List.of());
                });

        return Mono.zip(authorMono, tagsMono)
                .doOnNext(tuple -> {
                    post.setAuthor(tuple.getT1());
                    post.setTags(tuple.getT2());
                })
                .thenReturn(post);
    }

    /**
     * 为帖子保存标签（createPost 时调用）。
     * 查找或创建 tag → 关联 post_tags → 更新 tag.post_count
     */
    private Mono<Void> saveTagsForPost(UUID postId, List<String> tagNames) {
        return r2dbc.getDatabaseClient()
                .sql("DELETE FROM post_tags WHERE post_id = :postId")
                .bind("postId", postId)
                .fetch().rowsUpdated()
                .then(flattenTagInserts(postId, tagNames));
    }

    /**
     * 替换帖子的标签（updatePost 时调用）。
     */
    private Mono<Void> replaceTagsForPost(UUID postId, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return r2dbc.getDatabaseClient()
                    .sql("DELETE FROM post_tags WHERE post_id = :postId")
                    .bind("postId", postId)
                    .fetch().rowsUpdated().then();
        }
        return saveTagsForPost(postId, tagNames);
    }

    private Mono<Void> flattenTagInserts(UUID postId, List<String> tagNames) {
        return Flux.fromIterable(tagNames)
                .flatMap(tagName -> insertSingleTag(postId, tagName.trim()))
                .then();
    }

    private Mono<Void> insertSingleTag(UUID postId, String tagName) {
        if (tagName.isEmpty()) return Mono.empty();
        return r2dbc.getDatabaseClient()
                .sql("INSERT INTO tags (name, slug, description, post_count) " +
                     "VALUES (:name, :slug, '', 0) ON CONFLICT (name) DO NOTHING")
                .bind("name", tagName)
                .bind("slug", tagName.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "-"))
                .fetch().rowsUpdated()
                .then(
                    r2dbc.getDatabaseClient()
                        .sql("INSERT INTO post_tags (post_id, tag_id) " +
                             "SELECT :postId, id FROM tags WHERE name = :tagName " +
                             "ON CONFLICT (post_id, tag_id) DO NOTHING")
                        .bind("postId", postId)
                        .bind("tagName", tagName)
                        .fetch().rowsUpdated().then()
                )
                .onErrorResume(e -> {
                    log.warn("Failed to save tag '{}' for post {}: {}", tagName, postId, e.getMessage());
                    return Mono.empty();
                });
    }
}
