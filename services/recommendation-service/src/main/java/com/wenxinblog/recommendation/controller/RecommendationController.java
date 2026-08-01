package com.wenxinblog.recommendation.controller;

import com.wenxinblog.recommendation.dto.FeedRecommendation;
import com.wenxinblog.recommendation.dto.Result;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 推荐接口。用户身份统一从网关注入的 X-User-Id header 读取（来自 JWT，可信），
 * 不再用客户端可伪造的 userId 查询参数。
 * GET 端点 X-User-Id 可空（匿名 → trending 兜底）；POST/PUT 由网关保证必填。
 */
@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/feed")
    public Mono<Result<List<FeedRecommendation>>> getFeed(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return recommendationService.getFeedRecommendations(userId, page, size)
                .map(Result::success);
    }

    @GetMapping("/related/{postId}")
    public Mono<Result<List<FeedRecommendation>>> getRelated(
            @PathVariable String postId,
            @RequestParam(defaultValue = "10") int topK) {
        return recommendationService.getRelatedPosts(postId, topK)
                .map(Result::success);
    }

    /** 图文混合：以帖子封面图找相关博文（VL 图像向量检索文本向量）。 */
    @GetMapping("/related-by-image/{postId}")
    public Mono<Result<List<FeedRecommendation>>> getRelatedByImage(
            @PathVariable String postId,
            @RequestParam(defaultValue = "10") int topK) {
        return recommendationService.getRelatedByImage(postId, topK)
                .map(Result::success);
    }

    @GetMapping("/trending")
    public Mono<Result<List<TrendingPost>>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {
        return recommendationService.getTrendingPosts(limit)
                .map(Result::success);
    }

    @GetMapping("/users")
    public Mono<Result<List<String>>> getUserRecommendations(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return recommendationService.getUserRecommendations(userId, limit)
                .map(Result::success);
    }

    @GetMapping("/interests")
    public Mono<Result<List<UserInterestTag>>> getInterests(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.just(Result.success(List.of()));
        }
        return recommendationService.getInterestTags(userId)
                .collectList()
                .map(Result::success);
    }

    @PutMapping("/interests")
    public Mono<Result<List<UserInterestTag>>> updateInterests(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody List<String> tags) {
        return recommendationService.updateInterestTags(userId, tags)
                .map(Result::success);
    }

    @PostMapping("/feedback")
    public Mono<Result<String>> feedback(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> body) {
        String postId = body.get("postId");
        String action = body.get("action");
        return recommendationService.recordFeedback(userId, postId, action)
                .thenReturn(Result.success("ok"));
    }

    /** 把已有已发布帖子批量嵌入 Milvus（返回成功 upsert 条数）。仅 admin 可调。 */
    @PostMapping("/admin/backfill")
    public Mono<Result<Integer>> backfill(
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles,
            @RequestParam(defaultValue = "1000") int limit) {
        if (isNotAdmin(roles)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限"));
        }
        return recommendationService.backfill(limit).map(Result::success);
    }

    /** 网关在 JWT 验证后注入的 X-User-Roles 为逗号分隔；未含 admin 则拒绝。 */
    private boolean isNotAdmin(String roles) {
        if (roles == null || roles.isBlank()) return true;
        for (String role : roles.split(",")) {
            if ("admin".equals(role.trim())) return false;
        }
        return true;
    }
}
