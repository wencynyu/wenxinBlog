package com.wenxin.blog.service;

import com.wenxin.blog.AbstractIntegrationTest;
import com.wenxin.blog.dto.CommentRequest;
import com.wenxin.blog.entity.Comment;
import com.wenxin.blog.entity.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommentService 集成测试：重点覆盖纯 mock 无法验证的 SQL 行为——
 * 递归 CTE 删除评论子树并回减 posts.comment_count、batchFillAuthors 的 ANY(:ids) 回填、审核状态流转。
 */
class CommentServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private PostService postService;

    private final UUID postAuthor = UUID.randomUUID();
    private final UUID commenter = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();

    private UUID seedPublishedPost() throws Exception {
        UUID postId = UUID.randomUUID();
        try (Connection conn = openJdbc(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO posts (id, author_id, title, content, status, like_count, view_count, comment_count, published_at, created_at, updated_at) VALUES ("
                    + "'" + postId + "', '" + postAuthor + "', '标题', '内容', 'published', 0, 0, 0, now(), now(), now())");
        }
        return postId;
    }

    private CommentRequest reply(String content, UUID parentId) {
        CommentRequest req = new CommentRequest();
        req.setContent(content);
        req.setParentId(parentId);
        return req;
    }

    @Test
    void createComment_savesAndIncrementsPostCommentCount() throws Exception {
        UUID postId = seedPublishedPost();

        StepVerifier.create(commentService.createComment(postId, commenter,
                reply("一级评论", null), "comment:create"))
                .assertNext(c -> {
                    assertThat(c.getId()).isNotNull();
                    assertThat(c.getStatus()).isEqualTo("APPROVED");
                    assertThat(c.getPostId()).isEqualTo(postId);
                })
                .verifyComplete();

        StepVerifier.create(postService.getPost(postId, null))
                .assertNext(p -> assertThat(p.getCommentCount()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void createComment_withoutPermission_rejected403() throws Exception {
        UUID postId = seedPublishedPost();

        StepVerifier.create(commentService.createComment(postId, commenter, reply("x", null), null))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void listComments_returnsTopLevelOnly_andFillsAuthor() throws Exception {
        UUID postId = seedPublishedPost();
        insertAuthor(commenter, "carol", "Carol");
        UUID root = commentService.createComment(postId, commenter, reply("根评论", null), "comment:create").block().getId();
        commentService.createComment(postId, commenter, reply("子回复不出现", root), "comment:create").block();

        StepVerifier.create(commentService.listComments(postId).collectList())
                .assertNext(comments -> {
                    assertThat(comments).hasSize(1); // 只返回顶级（parent_id IS NULL）
                    assertThat(comments.get(0).getContent()).isEqualTo("根评论");
                    assertThat(comments.get(0).getAuthor()).isNotNull();
                    assertThat(comments.get(0).getAuthor().getUsername()).isEqualTo("carol");
                })
                .verifyComplete();
    }

    @Test
    void listReplies_returnsChildrenOfParent() throws Exception {
        UUID postId = seedPublishedPost();
        UUID root = commentService.createComment(postId, commenter, reply("根", null), "comment:create").block().getId();
        commentService.createComment(postId, commenter, reply("回复1", root), "comment:create").block();
        commentService.createComment(postId, commenter, reply("回复2", root), "comment:create").block();

        StepVerifier.create(commentService.listReplies(root).collectList())
                .assertNext(replies -> {
                    assertThat(replies).hasSize(2);
                    assertThat(replies).extracting(Comment::getContent)
                            .containsExactlyInAnyOrder("回复1", "回复2");
                })
                .verifyComplete();
    }

    @Test
    void deleteComment_owner_removesEntireSubtreeAndDecrementsCount() throws Exception {
        UUID postId = seedPublishedPost();
        UUID root = commentService.createComment(postId, commenter, reply("根", null), "comment:create").block().getId();
        UUID child = commentService.createComment(postId, commenter, reply("子", root), "comment:create").block().getId();
        commentService.createComment(postId, commenter, reply("孙", child), "comment:create").block();
        commentService.createComment(postId, commenter, reply("独立评论", null), "comment:create").block();

        StepVerifier.create(postService.getPost(postId, null))
                .assertNext(p -> assertThat(p.getCommentCount()).isEqualTo(4))
                .verifyComplete();

        // 删除根评论 → 递归 CTE 一次删掉 根+子+孙（3 行）并回减计数
        StepVerifier.create(commentService.deleteComment(commenter, root, "comment:delete:own"))
                .verifyComplete();

        StepVerifier.create(postService.getPost(postId, null))
                .assertNext(p -> assertThat(p.getCommentCount()).isEqualTo(1))
                .verifyComplete();

        // 子树已全部消失
        StepVerifier.create(commentService.listReplies(root).collectList())
                .assertNext(List::isEmpty)
                .verifyComplete();
    }

    @Test
    void deleteComment_moderator_canDeleteOthersComment() throws Exception {
        UUID postId = seedPublishedPost();
        UUID cid = commentService.createComment(postId, commenter, reply("待审核删除", null), "comment:create").block().getId();

        StepVerifier.create(commentService.deleteComment(moderator, cid, "comment:moderate"))
                .verifyComplete();

        StepVerifier.create(postService.getPost(postId, null))
                .assertNext(p -> assertThat(p.getCommentCount()).isZero())
                .verifyComplete();
    }

    @Test
    void deleteComment_withoutPermission_rejected403() throws Exception {
        UUID postId = seedPublishedPost();
        UUID cid = commentService.createComment(postId, commenter, reply("别人的", null), "comment:create").block().getId();

        StepVerifier.create(commentService.deleteComment(moderator, cid, "comment:delete:own"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }

    @Test
    void moderateComment_transitionsStatus_andValidatesInput() throws Exception {
        UUID postId = seedPublishedPost();
        UUID cid = commentService.createComment(postId, commenter, reply("待审核", null), "comment:create").block().getId();

        StepVerifier.create(commentService.moderateComment(cid, "hidden", "comment:moderate"))
                .assertNext(c -> assertThat(c.getStatus()).isEqualTo("HIDDEN"))
                .verifyComplete();

        StepVerifier.create(commentService.moderateComment(cid, "approved", "comment:moderate"))
                .assertNext(c -> assertThat(c.getStatus()).isEqualTo("APPROVED"))
                .verifyComplete();

        // 非法状态 → 400；无权限 → 403
        StepVerifier.create(commentService.moderateComment(cid, "deleted", "comment:moderate"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST))
                .verify();

        StepVerifier.create(commentService.moderateComment(cid, "hidden", null))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN))
                .verify();
    }
}
