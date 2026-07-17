package com.wenxin.blog.service;

import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.entity.Post;
import com.wenxin.blog.repository.PostRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
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

    /**
     * 列出已发布帖子：支持排序（sortBy）、标签过滤（tag）、真实分页总数（total）。
     * 排序列由 {@link #sortColumnOf(String)} 白名单映射，方向只接受 ASC/DESC，杜绝 SQL 注入。
     * pageSize/page 由 controller 传入（前端契约），此处只做防御性规整。
     */
    public Mono<PostListResult> listPublishedPosts(int page, int size, String sortBy, String sortOrder, String tag) {
        String sortColumn = sortColumnOf(sortBy);
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(0, page);
        int offset = safePage * safeSize;
        String tagFilter = (tag != null && !tag.isBlank()) ? tag.trim() : null;
        boolean byTag = tagFilter != null;

        // 标签存在性子查询（参数化 :tag，非拼接）
        String tagExists = " AND EXISTS (SELECT 1 FROM post_tags pt JOIN tags t ON pt.tag_id = t.id "
                + "WHERE pt.post_id = p.id AND t.name = :tag)";
        String listSql = "SELECT p.* FROM posts p WHERE p.status = 'published'"
                + (byTag ? tagExists : "")
                + " ORDER BY p." + sortColumn + " " + direction + " NULLS LAST, p.created_at DESC"
                + " LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) AS c FROM posts p WHERE p.status = 'published'"
                + (byTag ? tagExists : "");

        DatabaseClient db = r2dbc.getDatabaseClient();
        DatabaseClient.GenericExecuteSpec listSpec =
                db.sql(listSql).bind("limit", safeSize).bind("offset", offset);
        DatabaseClient.GenericExecuteSpec countSpec = db.sql(countSql);
        if (byTag) {
            listSpec = listSpec.bind("tag", tagFilter);
            countSpec = countSpec.bind("tag", tagFilter);
        }

        Mono<Long> total = countSpec.map(row -> row.get("c", Long.class)).one().defaultIfEmpty(0L);
        // flatMapSequential：并发跑 fillAuthorAndTags 但按 DB 返回顺序回放，保住 ORDER BY 结果
        Flux<Post> posts =
                listSpec.map((row, meta) -> mapPost(row)).all().flatMapSequential(this::fillAuthorAndTags);

        return Mono.zip(posts.collectList(), total)
                .map(t -> new PostListResult(t.getT1(), t.getT2()));
    }

    /** 前端 sortBy → 安全 SQL 列名白名单；未知值回落到 published_at，绝不直接拼接用户输入。 */
    public static String sortColumnOf(String sortBy) {
        if (sortBy == null) return "published_at";
        switch (sortBy.toLowerCase()) {
            case "likecount": case "like_count": return "like_count";
            case "commentcount": case "comment_count": return "comment_count";
            case "createdat": case "created_at": return "created_at";
            case "updatedat": case "updated_at": return "updated_at";
            case "viewcount": case "view_count": return "view_count";
            default: return "published_at";
        }
    }

    /** DatabaseClient 原生 SQL 不走实体自动映射，手动把 Row → Post。 */
    private Post mapPost(Row row) {
        Post p = new Post();
        p.setId(row.get("id", UUID.class));
        p.setAuthorId(row.get("author_id", UUID.class));
        p.setTitle(row.get("title", String.class));
        p.setContent(row.get("content", String.class));
        p.setSummary(row.get("summary", String.class));
        p.setCoverImage(row.get("cover_image", String.class));
        p.setStatus(row.get("status", String.class));
        p.setViewCount(row.get("view_count", Integer.class));
        p.setLikeCount(row.get("like_count", Integer.class));
        p.setCommentCount(row.get("comment_count", Integer.class));
        p.setPublishedAt(row.get("published_at", LocalDateTime.class));
        p.setCreatedAt(row.get("created_at", LocalDateTime.class));
        p.setUpdatedAt(row.get("updated_at", LocalDateTime.class));
        return p;
    }

    /** listPublishedPosts 结果：帖子列表 + 真实总数（供 PaginatedResponse.totalPages 计算）。 */
    public record PostListResult(List<Post> items, long total) {}

    public Flux<Post> listPostsByAuthor(UUID authorId, int page, int size) {
        return postRepository.findByAuthorId(authorId, PageRequest.of(page, size))
                .flatMapSequential(this::fillAuthorAndTags);
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
