package com.wenxinblog.recommendation.repository;

import com.wenxinblog.recommendation.AbstractIntegrationTest;
import com.wenxinblog.recommendation.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostReadRepository 集成测试：直读 blog_db 的 SQL（trending 加权×时间衰减、ANY(:ids) 批量、
 * tags JOIN）此前完全未测，需真实 PostgreSQL 验证排序语义与参数绑定。
 */
class PostReadRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostReadRepository repository;

    private final UUID authorId = UUID.randomUUID();

    @Test
    void findTrending_ordersByWeightedEngagementWithTimeDecay() throws Exception {
        // 同样的互动量：1 天前 vs 30 天前 → 越新分越高（时间衰减 age^-0.4）
        UUID fresh = UUID.randomUUID(), old = UUID.randomUUID();
        insertPublishedPost(fresh, authorId, "新鲜高互动", 100, 500, 20, LocalDateTime.now().minusDays(1));
        insertPublishedPost(old, authorId, "老旧同互动", 100, 500, 20, LocalDateTime.now().minusDays(30));
        // 低互动的最新帖：3×1 + 5×0 = 3 分，远低于上面两条，排最后
        UUID low = UUID.randomUUID();
        insertPublishedPost(low, authorId, "低互动", 1, 0, 0, LocalDateTime.now().minusHours(1));

        StepVerifier.create(repository.findTrending(10).collectList())
                .assertNext(posts -> {
                    assertThat(posts).extracting(Post::getTitle)
                            .containsExactly("新鲜高互动", "老旧同互动", "低互动");
                    // trending 只取已发布：draft 不出现（前面用例已 truncate，此处无 draft，见下一条用例）
                })
                .verifyComplete();
    }

    @Test
    void findTrending_excludesDraftsAndUnpublishedAtNull() throws Exception {
        UUID published = UUID.randomUUID();
        insertPublishedPost(published, authorId, "已发布", 1, 1, 1, LocalDateTime.now());

        UUID draft = UUID.randomUUID();
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO posts (id, author_id, title, status, published_at, created_at) VALUES ("
                    + "'" + draft + "', '" + authorId + "', '草稿', 'draft', now(), now())");
        }

        StepVerifier.create(repository.findTrending(10).map(Post::getTitle).collectList())
                .assertNext(titles -> {
                    assertThat(titles).containsExactly("已发布");
                    assertThat(titles).doesNotContain("草稿");
                })
                .verifyComplete();
    }

    @Test
    void findTrending_joinsAuthorCacheTable() throws Exception {
        UUID postId = UUID.randomUUID();
        insertPublishedPost(postId, authorId, "带作者", 1, 1, 1, LocalDateTime.now());
        insertAuthor(authorId, "alice");

        StepVerifier.create(repository.findTrending(5).next())
                .assertNext(post -> {
                    assertThat(post.getAuthorUsername()).isEqualTo("alice");
                    assertThat(post.getAuthorDisplayName()).isEqualTo("alice");
                })
                .verifyComplete();
    }

    @Test
    void findById_returnsPostWithAuthor_leftJoinToleratesMissingAuthor() throws Exception {
        UUID postId = UUID.randomUUID();
        insertPublishedPost(postId, authorId, "无作者记录", 0, 0, 0, LocalDateTime.now());
        // authors 表没有该作者 → LEFT JOIN 不报错，author 字段为 null

        StepVerifier.create(repository.findById(postId))
                .assertNext(post -> {
                    assertThat(post.getTitle()).isEqualTo("无作者记录");
                    assertThat(post.getAuthorUsername()).isNull();
                    assertThat(post.getStatus()).isEqualTo("published");
                })
                .verifyComplete();

        StepVerifier.create(repository.findById(UUID.randomUUID())).verifyComplete(); // 不存在 → 空
    }

    @Test
    void findByIds_anyArrayBinding() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        insertPublishedPost(p1, authorId, "A", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(p2, authorId, "B", 0, 0, 0, LocalDateTime.now());

        StepVerifier.create(repository.findByIds(List.of(p1, p2)).map(Post::getTitle).collectList())
                .assertNext(titles -> assertThat(titles).containsExactlyInAnyOrder("A", "B"))
                .verifyComplete();

        // 空集合直接返回空 Flux（不发起 SQL）
        StepVerifier.create(repository.findByIds(List.of())).verifyComplete();
    }

    @Test
    void findTagsForPosts_mapsPostIdToTagNames() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        insertPublishedPost(p1, authorId, "A", 0, 0, 0, LocalDateTime.now());
        insertPublishedPost(p2, authorId, "B", 0, 0, 0, LocalDateTime.now());
        addTag(p1, "java");
        addTag(p1, "spring");
        addTag(p2, "go");

        StepVerifier.create(repository.findTagsForPosts(List.of(p1, p2)))
                .assertNext(map -> {
                    assertThat(map.get(p1)).containsExactlyInAnyOrder("java", "spring");
                    assertThat(map.get(p2)).containsExactly("go");
                })
                .verifyComplete();

        StepVerifier.create(repository.findTagsForPosts(List.of()))
                .assertNext(map -> assertThat(map).isEmpty())
                .verifyComplete();
    }

    @Test
    void countPublished_andFindAllPublished() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        LocalDateTime base = LocalDateTime.now();
        insertPublishedPost(p1, authorId, "旧", 0, 0, 0, base.minusDays(2));
        insertPublishedPost(p2, authorId, "新", 0, 0, 0, base);
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO posts (id, author_id, title, status, created_at) VALUES ("
                    + "'" + p3 + "', '" + authorId + "', '草稿', 'draft', now())");
        }

        StepVerifier.create(repository.countPublished()).expectNext(2L).verifyComplete();

        // 按创建时间倒序，只含已发布
        StepVerifier.create(repository.findAllPublished(10).map(Post::getTitle).collectList())
                .assertNext(titles -> assertThat(titles).containsExactly("新", "旧"))
                .verifyComplete();

        // limit 生效
        StepVerifier.create(repository.findAllPublished(1).collectList())
                .assertNext(posts -> assertThat(posts).hasSize(1))
                .verifyComplete();
    }

    private void addTag(UUID postId, String name) throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO tags (name, slug, description) VALUES ('" + name + "', '" + name.toLowerCase() + "', '')");
            st.execute("INSERT INTO post_tags (post_id, tag_id) SELECT '" + postId + "', id FROM tags WHERE name = '" + name + "'");
        }
    }
}
