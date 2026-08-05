package com.wenxinblog.search.controller;

import com.wenxinblog.search.dto.*;
import com.wenxinblog.search.service.SearchHistoryService;
import com.wenxinblog.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SearchHistoryService historyService;

    @GetMapping("/blog")
    public Mono<Result<PageResult<BlogSearchResponse>>> searchBlogs(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int size,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String authorId) {

        SearchRequest request = new SearchRequest(q, page, size, sortBy, tags, category, authorId);

        return searchService.searchBlogs(request)
                .map(Result::success)
                .doOnSuccess(r -> {
                    if (page == 0 && q != null && !q.isBlank()) {
                        searchService.recordSearch(q).subscribe();
                    }
                });
    }

    @GetMapping("/users")
    public Mono<Result<PageResult<UserSearchResponse>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return searchService.searchUsers(q, page, size)
                .map(Result::success)
                .doOnSuccess(r -> {
                    if (page == 0 && q != null && !q.isBlank()) {
                        searchService.recordSearch(q).subscribe();
                    }
                });
    }

    @GetMapping("/suggest")
    public Mono<Result<List<SuggestResponse>>> suggest(
            @RequestParam String q,
            @RequestParam(defaultValue = "blog") String type) {

        return searchService.suggest(q, type)
                .map(Result::success);
    }

    @GetMapping("/trending")
    public Mono<Result<List<String>>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {

        return searchService.getTrendingSearches(limit)
                .map(Result::success);
    }

    @GetMapping("/trending/tags")
    public Mono<Result<List<String>>> getTrendingTags(
            @RequestParam(defaultValue = "20") int limit) {

        return searchService.getTrendingTags(limit)
                .map(Result::success);
    }

    @GetMapping("/history")
    public Mono<Result<List<String>>> getHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "20") int limit) {

        return historyService.getSearchHistory(userId, limit)
                .collectList()
                .map(Result::success);
    }

    @DeleteMapping("/history")
    public Mono<Result<Boolean>> clearHistory(
            @RequestHeader("X-User-Id") String userId) {

        return historyService.clearSearchHistory(userId)
                .map(Result::success);
    }
}
