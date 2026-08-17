package com.wenxin.blog.service;

import com.wenxin.blog.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FavoriteService 集成测试：toggle 幂等语义依赖 post_favorites 的
 * ON CONFLICT (user_id, post_id) DO NOTHING 与主键约束，需真实 PostgreSQL 验证。
 */
class FavoriteServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FavoriteService favoriteService;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();

    private UUID seedPost() throws Exception {
        UUID postId = UUID.randomUUID();
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO posts (id, author_id, title, content, status, published_at, created_at, updated_at) VALUES ("
                    + "'" + postId + "', '" + UUID.randomUUID() + "', '标题', '内容', 'published', now(), now(), now())");
        }
        return postId;
    }

    @Test
    void toggleFavorite_addsThenRemoves_idempotentPerUser() throws Exception {
        UUID postId = seedPost();

        // 第一次：收藏
        StepVerifier.create(favoriteService.toggleFavorite(userId, postId))
                .expectNext(true).verifyComplete();
        StepVerifier.create(favoriteService.isFavorited(userId, postId))
                .expectNext(true).verifyComplete();

        // 第二次：取消
        StepVerifier.create(favoriteService.toggleFavorite(userId, postId))
                .expectNext(false).verifyComplete();
        StepVerifier.create(favoriteService.isFavorited(userId, postId))
                .expectNext(false).verifyComplete();
    }

    @Test
    void favorites_areIsolatedPerUser() throws Exception {
        UUID postId = seedPost();
        favoriteService.toggleFavorite(userId, postId).block();

        assertThat(favoriteService.isFavorited(otherUser, postId).block()).isFalse();

        // 其他用户也能收藏同一篇（复合主键互不冲突）
        StepVerifier.create(favoriteService.toggleFavorite(otherUser, postId))
                .expectNext(true).verifyComplete();
        StepVerifier.create(favoriteService.isFavorited(userId, postId))
                .expectNext(true).verifyComplete();
    }

    @Test
    void isFavorited_falseWhenNeverFavorited() throws Exception {
        UUID postId = seedPost();
        StepVerifier.create(favoriteService.isFavorited(userId, postId))
                .expectNext(false).verifyComplete();
    }
}
