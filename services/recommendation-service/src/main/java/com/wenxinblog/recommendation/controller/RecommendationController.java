package com.wenxinblog.recommendation.controller;

import com.wenxinblog.recommendation.dto.FeedRecommendation;
import com.wenxinblog.recommendation.dto.Result;
import com.wenxinblog.recommendation.dto.TrendingPost;
import com.wenxinblog.recommendation.entity.UserInterestTag;
import com.wenxinblog.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recommend")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/feed")
    public Mono<Result<List<FeedRecommendation>>> getFeed(
            @RequestParam String userId,
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

    @GetMapping("/trending")
    public Mono<Result<List<TrendingPost>>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {
        return recommendationService.getTrendingPosts(limit)
                .map(Result::success);
    }

    @GetMapping("/users")
    public Mono<Result<List<String>>> getUserRecommendations(
            @RequestParam String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return recommendationService.getUserRecommendations(userId, limit)
                .map(Result::success);
    }

    @GetMapping("/interests")
    public Mono<Result<List<UserInterestTag>>> getInterests(@RequestParam String userId) {
        return recommendationService.getInterestTags(userId)
                .collectList()
                .map(Result::success);
    }

    @PutMapping("/interests")
    public Mono<Result<List<UserInterestTag>>> updateInterests(
            @RequestParam String userId,
            @RequestBody List<String> tags) {
        return recommendationService.updateInterestTags(userId, tags)
                .map(Result::success);
    }

    @PostMapping("/feedback")
    public Mono<Result<String>> feedback(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String postId = body.get("postId");
        String action = body.get("action");
        return recommendationService.recordFeedback(userId, postId, action)
                .thenReturn(Result.success("ok"));
    }
}
