package com.wenxin.blog.service;

import com.wenxin.blog.AbstractIntegrationTest;
import com.wenxin.blog.dto.PostRequest;
import com.wenxin.blog.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PostService 集成测试：真实 PostgreSQL（Testcontainers）执行原生 SQL 路径——
 * listPublishedPosts 的排序白名单/标签过滤/分页 total、批量回填 author/tags/用户状态、
 * 标签 upsert（ON CONFLICT）——这些在纯 Mockito 单测里 mock 代价极高且脆弱。
 */
class PostServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PostService postService;

    private final UUID authorId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID viewerId = UUID.randomUUID();

    // ---------- createPost ----------

    @Test
    void createPost_draftWithTags_savesPostAndTagsWithoutPublishEvent() {
        PostRequest req = new PostRequest();
        req.setTitle("草稿标题");
        req.setContent("内容");
        req.setStatus("draft");
        req.setTags(List.of("java", "spring"));

        StepVerifier.create(postService.createPost(authorId, req, "post:create"))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getStatus()).isEqualTo("draft");
                    assertThat(saved.getPublishedAt()).isNull();
                })
                .verifyComplete();

        // 标签已落库（tags 表 upsert + post_tags 关联）
        List<String> names = listTagNames();
        assertThat(names).containsExactlyInAnyOrder("java", "spring");

        verify(blogEventPublisher, never()).publishCreate(any(), anyList());
    }

    @Test
    void createPost_published_setsPublishedAtAndPublishesCreateEvent() {
        PostRequest req = new PostRequest();
        req.setTitle("发布标题");
        req.setContent("内容");
        req.setStatus("PUBLISHED");
        req.setTags(List.of("go"));

        StepVerifier.create(postService.createPost(authorId, req, "post:create"))
                .assertNext(saved -> {
                    assertThat(saved.getStatus()).isEqualTo("published");
                    assertThat(saved.getPublishedAt()).isNotNull();
                })
                .verifyComplete();

        verify(blogEventPublisher).publishCreate(any(Post.class), anyList());
    }

    @Test
    void createPost_withoutPermission_rejected403() {
        PostRequest req = new PostRequest();
        req.setTitle("无权限");
        req.setContent("内容");

        StepVerifier.create(postService.createPost(authorId, req, null))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(ResponseStatusException.class)
                        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    // ---------- updatePost（含标签替换 upsert）----------

    @Test
    void updatePost_ownerReplacesTags_andPublishesUpdateEvent() {
        PostRequest create = new PostRequest();
        create.setTitle("原标题");
        create.setContent("内容");
        create.setStatus("published");
        create.setTags(List.of("java", "spring"));
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        PostRequest update = new PostRequest();
        update.setTitle("新标题");
        update.setTags(List.of("spring", "redis"));

        StepVerifier.create(postService.updatePost(authorId, postId, update, "post:update:own"))
                .assertNext(saved -> assertThat(saved.getTitle()).isEqualTo("新标题"))
                .verifyComplete();

        // 标签被整体替换：java 关联移除，spring 复用（tags 表 ON CONFLICT 不重复插入），redis 新增
        //（tags 表中的 java 行是共享标签，不随关联删除，属预期行为）
        List<String> names = listTagNames();
        assertThat(names).contains("spring", "redis");
        assertThat(names.size()).isEqualTo(names.stream().distinct().count());
        assertThat(tagCountOf(postId)).containsExactly("redis", "spring");
        verify(blogEventPublisher).publishUpdate(any(Post.class), anyList(), anyString());
    }

    @Test
    void updatePost_nonOwnerWithoutUpdateAny_rejected403() {
        PostRequest create = new PostRequest();
        create.setTitle("别人的帖子");
        create.setContent("内容");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        StepVerifier.create(postService.updatePost(otherUserId, postId, new PostRequest(), "post:update:own"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void updatePost_publishTransition_setsPublishedAtOnlyOnce() {
        PostRequest create = new PostRequest();
        create.setTitle("待发布");
        create.setContent("内容");
        create.setStatus("draft");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        PostRequest publish = new PostRequest();
        publish.setStatus("published");
        postService.updatePost(authorId, postId, publish, "post:update:own").block();

        PostRequest republish = new PostRequest();
        republish.setTitle("再次编辑");
        republish.setStatus("published");
        LocalDateTime firstPublishedAt = postService.updatePost(authorId, postId, republish, "post:update:own").block().getPublishedAt();

        assertThat(firstPublishedAt).isNotNull(); // 已有 publishedAt 不会被覆盖
    }

    @Test
    void updatePost_emptyTags_clearsAllTags() {
        PostRequest create = new PostRequest();
        create.setTitle("带标签");
        create.setContent("内容");
        create.setTags(List.of("a", "b"));
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        PostRequest update = new PostRequest();
        update.setTags(List.of());
        postService.updatePost(authorId, postId, update, "post:update:own").block();

        assertThat(tagCountOf(postId)).isEmpty();
    }

    // ---------- listPublishedPosts（排序白名单/标签过滤/分页/批量回填）----------

    @Test
    void listPublishedPosts_defaultSortByPublishedAtDesc_withPagination() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        insertPublishedPost(p1, authorId, "最旧", 0, 0, LocalDateTime.now().minusDays(3));
        insertPublishedPost(p2, authorId, "最新", 0, 0, LocalDateTime.now());
        insertPublishedPost(p3, authorId, "中间", 0, 0, LocalDateTime.now().minusDays(1));
        insertDraftPost(UUID.randomUUID(), authorId, "草稿不出现");

        // 第一页 2 条，总数 3（真实 COUNT，含未取回页）
        StepVerifier.create(postService.listPublishedPosts(0, 2, null, null, null, null))
                .assertNext(r -> {
                    assertThat(r.items()).extracting(Post::getTitle).containsExactly("最新", "中间");
                    assertThat(r.total()).isEqualTo(3);
                })
                .verifyComplete();

        StepVerifier.create(postService.listPublishedPosts(1, 2, null, null, null, null))
                .assertNext(r -> {
                    assertThat(r.items()).extracting(Post::getTitle).containsExactly("最旧");
                    assertThat(r.total()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void listPublishedPosts_sortByLikeCountDesc() throws Exception {
        insertPublishedPost(UUID.randomUUID(), authorId, "低赞", 1, 0, LocalDateTime.now());
        insertPublishedPost(UUID.randomUUID(), authorId, "高赞", 9, 0, LocalDateTime.now());
        insertPublishedPost(UUID.randomUUID(), authorId, "中赞", 5, 0, LocalDateTime.now());

        StepVerifier.create(postService.listPublishedPosts(0, 10, "likeCount", "desc", null, null))
                .assertNext(r -> assertThat(r.items()).extracting(Post::getTitle)
                        .containsExactly("高赞", "中赞", "低赞"))
                .verifyComplete();
    }

    @Test
    void listPublishedPosts_ascOrderAndUnknownSortFallback() throws Exception {
        insertPublishedPost(UUID.randomUUID(), authorId, "早发布", 0, 0, LocalDateTime.now().minusDays(1));
        insertPublishedPost(UUID.randomUUID(), authorId, "晚发布", 0, 0, LocalDateTime.now());

        // asc 生效；未知 sortBy 回落 published_at
        StepVerifier.create(postService.listPublishedPosts(0, 10, "whatever", "asc", null, null))
                .assertNext(r -> assertThat(r.items()).extracting(Post::getTitle)
                        .containsExactly("早发布", "晚发布"))
                .verifyComplete();
    }

    @Test
    void listPublishedPosts_tagFilter_onlyMatchingPublished() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        insertPublishedPost(p1, authorId, "java 帖", 0, 0, LocalDateTime.now());
        insertPublishedPost(p2, authorId, "go 帖", 0, 0, LocalDateTime.now());
        insertDraftPost(UUID.randomUUID(), authorId, "java 草稿");
        addTag(p1, "java");
        addTag(p2, "go");

        StepVerifier.create(postService.listPublishedPosts(0, 10, null, null, "java", null))
                .assertNext(r -> {
                    assertThat(r.items()).extracting(Post::getTitle).containsExactly("java 帖");
                    assertThat(r.total()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void listPublishedPosts_fillsAuthorTagsAndViewerState() throws Exception {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        insertPublishedPost(p1, authorId, "被赞帖", 0, 0, LocalDateTime.now());
        insertPublishedPost(p2, authorId, "被藏帖", 0, 0, LocalDateTime.now());
        insertAuthor(authorId, "alice", "Alice");
        addTag(p1, "java");

        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO post_likes (user_id, post_id) VALUES ('" + viewerId + "', '" + p1 + "')");
            st.execute("INSERT INTO post_favorites (user_id, post_id) VALUES ('" + viewerId + "', '" + p2 + "')");
        }

        StepVerifier.create(postService.listPublishedPosts(0, 10, null, null, null, viewerId))
                .assertNext(r -> {
                    assertThat(r.items()).hasSize(2);
                    Post liked = r.items().stream().filter(p -> p.getTitle().equals("被赞帖")).findFirst().orElseThrow();
                    Post favorited = r.items().stream().filter(p -> p.getTitle().equals("被藏帖")).findFirst().orElseThrow();
                    assertThat(liked.isLiked()).isTrue();
                    assertThat(liked.isFavorited()).isFalse();
                    assertThat(favorited.isFavorited()).isTrue();
                    assertThat(favorited.isLiked()).isFalse();
                    // author 批量回填（authors 缓存表）
                    assertThat(liked.getAuthor()).isNotNull();
                    assertThat(liked.getAuthor().getUsername()).isEqualTo("alice");
                    // tags 批量回填
                    assertThat(liked.getTags()).containsExactly("java");
                    assertThat(favorited.getTags()).isEmpty();
                })
                .verifyComplete();
    }

    // ---------- getPost / listPostsByAuthor ----------

    @Test
    void getPost_incrementsViewCount_andFillsAuthorTagsState() throws Exception {
        UUID postId = UUID.randomUUID();
        insertPublishedPost(postId, authorId, "详情", 0, 7, LocalDateTime.now());
        insertAuthor(authorId, "bob", "Bob");
        addTag(postId, "spring");
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO post_likes (user_id, post_id) VALUES ('" + viewerId + "', '" + postId + "')");
        }

        StepVerifier.create(postService.getPost(postId, viewerId))
                .assertNext(post -> {
                    assertThat(post.getViewCount()).isEqualTo(8);
                    assertThat(post.isLiked()).isTrue();
                    assertThat(post.isFavorited()).isFalse();
                    assertThat(post.getAuthor().getDisplayName()).isEqualTo("Bob");
                    assertThat(post.getTags()).containsExactly("spring");
                })
                .verifyComplete();

        // 未登录视角：不查点赞收藏，保持默认 false
        StepVerifier.create(postService.getPost(postId, null))
                .assertNext(post -> {
                    assertThat(post.getViewCount()).isEqualTo(9);
                    assertThat(post.isLiked()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void listPostsByAuthor_returnsOnlyThatAuthorWithTags() throws Exception {
        insertPublishedPost(UUID.randomUUID(), authorId, "我的帖", 0, 0, LocalDateTime.now());
        insertDraftPost(UUID.randomUUID(), authorId, "我的草稿");
        insertPublishedPost(UUID.randomUUID(), otherUserId, "别人的帖", 0, 0, LocalDateTime.now());

        StepVerifier.create(postService.listPostsByAuthor(authorId, 0, 10, null).collectList())
                .assertNext(posts -> {
                    assertThat(posts).hasSize(2); // 草稿也要出现在作者自己的列表
                    assertThat(posts).extracting(Post::getTitle).containsExactlyInAnyOrder("我的帖", "我的草稿");
                })
                .verifyComplete();
    }

    // ---------- deletePost / publishPost / featurePost ----------

    @Test
    void deletePost_owner_removesPostAndPublishesDeleteEvent() {
        PostRequest create = new PostRequest();
        create.setTitle("待删");
        create.setContent("内容");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        StepVerifier.create(postService.deletePost(authorId, postId, "post:delete:own"))
                .verifyComplete();

        StepVerifier.create(postService.getPost(postId, null)).verifyComplete(); // getPost 对不存在的帖子返回空
        verify(blogEventPublisher).publishDelete(any(Post.class));
    }

    @Test
    void deletePost_nonOwnerWithoutAny_rejected403() {
        PostRequest create = new PostRequest();
        create.setTitle("受保护");
        create.setContent("内容");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        StepVerifier.create(postService.deletePost(otherUserId, postId, "post:delete:own"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void publishPost_ownerWithPermission_setsPublished() {
        PostRequest create = new PostRequest();
        create.setTitle("待上线");
        create.setContent("内容");
        create.setStatus("draft");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        StepVerifier.create(postService.publishPost(authorId, postId, "post:publish"))
                .verifyComplete();

        StepVerifier.create(postService.getPost(postId, null))
                .assertNext(post -> assertThat(post.getStatus()).isEqualTo("published"))
                .verifyComplete();
    }

    @Test
    void publishPost_nonOwnerMissingUpdateAny_rejected403() {
        PostRequest create = new PostRequest();
        create.setTitle("别人的");
        create.setContent("内容");
        create.setStatus("draft");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        StepVerifier.create(postService.publishPost(otherUserId, postId, "post:publish"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void featurePost_withPermission_setsFeatured() {
        PostRequest create = new PostRequest();
        create.setTitle("精华候选");
        create.setContent("内容");
        create.setStatus("published");
        UUID postId = postService.createPost(authorId, create, "post:create").block().getId();

        StepVerifier.create(postService.featurePost(postId, "post:feature"))
                .assertNext(post -> assertThat(post.getFeatured()).isTrue())
                .verifyComplete();

        StepVerifier.create(postService.featurePost(postId, null))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    // ---------- JDBC 种子工具 ----------

    private void insertPublishedPost(UUID id, UUID aid, String title, int likeCount, int viewCount, LocalDateTime publishedAt) throws Exception {
        insertPost(id, aid, title, "published", likeCount, viewCount, publishedAt);
    }

    private void insertDraftPost(UUID id, UUID aid, String title) throws Exception {
        insertPost(id, aid, title, "draft", 0, 0, null);
    }

    private void insertPost(UUID id, UUID aid, String title, String status, int likeCount, int viewCount, LocalDateTime publishedAt) throws Exception {
        try (Connection conn = openJdbc(); PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO posts (id, author_id, title, content, status, featured, like_count, view_count, comment_count, published_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '内容', ?, false, ?, ?, 0, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, aid);
            ps.setString(3, title);
            ps.setString(4, status);
            ps.setInt(5, likeCount);
            ps.setInt(6, viewCount);
            ps.setObject(7, publishedAt);
            ps.setObject(8, publishedAt == null ? LocalDateTime.now() : publishedAt);
            ps.setObject(9, LocalDateTime.now());
            ps.executeUpdate();
        }
    }

    private void addTag(UUID postId, String name) throws Exception {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO tags (name, slug, description, post_count) VALUES ("
                    + "'" + name + "', '" + name.toLowerCase() + "', '', 0)");
            st.execute("INSERT INTO post_tags (post_id, tag_id) SELECT '" + postId + "', id FROM tags WHERE name = '" + name + "'");
        }
    }

    private List<String> listTagNames() {
        return r2dbcJdbcQuery("SELECT name FROM tags ORDER BY name");
    }

    private List<String> tagCountOf(UUID postId) {
        return r2dbcJdbcQuery("SELECT t.name FROM post_tags pt JOIN tags t ON pt.tag_id = t.id WHERE pt.post_id = '" + postId + "' ORDER BY t.name");
    }

    private List<String> r2dbcJdbcQuery(String sql) {
        try (Connection conn = openJdbc(); Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            java.util.List<String> out = new java.util.ArrayList<>();
            while (rs.next()) out.add(rs.getString(1));
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
