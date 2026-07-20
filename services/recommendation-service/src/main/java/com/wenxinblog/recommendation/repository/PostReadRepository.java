package com.wenxinblog.recommendation.repository;

import com.wenxinblog.recommendation.entity.Post;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 直读 blog_db.posts（+ authors 缓存表 JOIN）做热门/丰富推荐结果。
 * recommendation-service 只读不写 posts。
 */
@Repository
@RequiredArgsConstructor
public class PostReadRepository {

    private final DatabaseClient db;

    // 热门权重：评论权重最高（强互动），点赞次之，浏览最低；时间衰减指数 0.4。
    private static final int LIKE_W = 3;
    private static final int VIEW_W = 1;
    private static final int COMMENT_W = 5;
    private static final double DECAY = 0.4;

    /**
     * 热门 = 加权互动分 × 时间衰减。越新、互动越高的帖子排名越靠前。
     */
    public Flux<Post> findTrending(int limit) {
        String sql = """
                SELECT p.*, a.username AS author_username, a.display_name AS author_display_name,
                       a.avatar_url AS author_avatar_url
                FROM posts p LEFT JOIN authors a ON p.author_id = a.id
                WHERE p.status = 'published' AND p.published_at IS NOT NULL
                ORDER BY (COALESCE(p.like_count,0) * %d + COALESCE(p.view_count,0) * %d
                          + COALESCE(p.comment_count,0) * %d)
                         * POWER(EXTRACT(EPOCH FROM (NOW() - p.published_at)), %f) DESC
                LIMIT :limit
                """.formatted(LIKE_W, VIEW_W, COMMENT_W, -DECAY);
        return db.sql(sql).bind("limit", limit).map((row, meta) -> mapPostWithAuthor(row)).all();
    }

    public Mono<Post> findById(UUID id) {
        String sql = """
                SELECT p.*, a.username AS author_username, a.display_name AS author_display_name,
                       a.avatar_url AS author_avatar_url
                FROM posts p LEFT JOIN authors a ON p.author_id = a.id
                WHERE p.id = :id
                """;
        return db.sql(sql).bind("id", id).map((row, meta) -> mapPostWithAuthor(row)).one();
    }

    public Flux<Post> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Flux.empty();
        }
        String sql = """
                SELECT p.*, a.username AS author_username, a.display_name AS author_display_name,
                       a.avatar_url AS author_avatar_url
                FROM posts p LEFT JOIN authors a ON p.author_id = a.id
                WHERE p.id = ANY(:ids)
                """;
        UUID[] arr = ids.toArray(new UUID[0]);
        return db.sql(sql).bind("ids", arr).map((row, meta) -> mapPostWithAuthor(row)).all();
    }

    /** 批量取多个 post 的标签，供推荐结果丰富用。 */
    public Mono<Map<UUID, List<String>>> findTagsForPosts(Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Mono.just(Map.of());
        }
        UUID[] arr = postIds.toArray(new UUID[0]);
        return db.sql("SELECT pt.post_id AS post_id, t.name AS name FROM post_tags pt "
                + "JOIN tags t ON pt.tag_id = t.id WHERE pt.post_id = ANY(:ids)")
                .bind("ids", arr)
                .map((row, meta) -> Map.entry(row.get("post_id", UUID.class), row.get("name", String.class)))
                .all()
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                .map(m -> {
                    Map<UUID, List<String>> r = new HashMap<>();
                    m.forEach((k, v) -> r.put(k, new ArrayList<>(v)));
                    return r;
                });
    }

    public Mono<Long> countPublished() {
        return db.sql("SELECT COUNT(*) AS c FROM posts WHERE status = 'published'")
                .map(row -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    /** 所有已发布帖子（backfill 用，按创建时间倒序）。 */
    public Flux<Post> findAllPublished(int limit) {
        String sql = """
                SELECT p.*, a.username AS author_username, a.display_name AS author_display_name,
                       a.avatar_url AS author_avatar_url
                FROM posts p LEFT JOIN authors a ON p.author_id = a.id
                WHERE p.status = 'published'
                ORDER BY p.created_at DESC
                LIMIT :limit
                """;
        return db.sql(sql).bind("limit", limit).map((row, meta) -> mapPostWithAuthor(row)).all();
    }

    private Post mapPostWithAuthor(Row row) {
        Post p = new Post();
        p.setId(row.get("id", UUID.class));
        p.setAuthorId(row.get("author_id", UUID.class));
        p.setTitle(row.get("title", String.class));
        p.setContent(row.get("content", String.class));
        p.setSummary(row.get("summary", String.class));
        p.setCoverImage(row.get("cover_image", String.class));
        p.setStatus(row.get("status", String.class));
        p.setViewCount(intOrZero(row, "view_count"));
        p.setLikeCount(intOrZero(row, "like_count"));
        p.setCommentCount(intOrZero(row, "comment_count"));
        p.setPublishedAt(row.get("published_at", LocalDateTime.class));
        p.setCreatedAt(row.get("created_at", LocalDateTime.class));
        p.setUpdatedAt(row.get("updated_at", LocalDateTime.class));
        p.setAuthorUsername(row.get("author_username", String.class));
        p.setAuthorDisplayName(row.get("author_display_name", String.class));
        p.setAuthorAvatarUrl(row.get("author_avatar_url", String.class));
        return p;
    }

    private Integer intOrZero(Row row, String col) {
        Integer v = row.get(col, Integer.class);
        return v != null ? v : 0;
    }
}
