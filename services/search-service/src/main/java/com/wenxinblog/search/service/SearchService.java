package com.wenxinblog.search.service;

import com.wenxinblog.search.dto.*;
import com.wenxinblog.search.model.BlogDocument;
import com.wenxinblog.search.model.UserDocument;
import com.wenxinblog.search.repository.BlogSearchRepository;
import com.wenxinblog.search.repository.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final BlogSearchRepository blogRepo;
    private final UserSearchRepository userRepo;
    private final ReactiveStringRedisTemplate redis;

    public Mono<PageResult<BlogSearchResponse>> searchBlogs(SearchRequest request) {
        return Mono.fromCallable(() -> {
            SearchResponse<BlogDocument> response = blogRepo.searchBlogs(request);
            List<BlogSearchResponse> items = new ArrayList<>();
            long total = response.hits().total().value();

            for (Hit<BlogDocument> hit : response.hits().hits()) {
                BlogDocument doc = hit.source();
                if (doc == null) continue;

                List<String> hlTitle = hit.highlight() != null && hit.highlight().get("title") != null
                        ? hit.highlight().get("title") : List.of();
                List<String> hlContent = hit.highlight() != null && hit.highlight().get("content") != null
                        ? hit.highlight().get("content") : List.of();

                items.add(new BlogSearchResponse(
                        doc.getId(), doc.getTitle(), doc.getContent(), doc.getSummary(),
                        doc.getAuthorId(), doc.getAuthorName(), doc.getTags(),
                        doc.getCategory(), doc.getViewCount(), doc.getLikeCount(),
                        doc.getPublishedAt(), hit.score() != null ? hit.score() : 0.0,
                        hlTitle, hlContent
                ));
            }
            int totalPages = request.size() > 0 ? (int) Math.ceil((double) total / request.size()) : 0;
            return new PageResult<>(items, total, request.page(), request.size(), totalPages);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PageResult<UserSearchResponse>> searchUsers(String query, int page, int size) {
        return Mono.fromCallable(() -> {
            SearchResponse<UserDocument> response = userRepo.searchUsers(query, page, size);
            List<UserSearchResponse> items = new ArrayList<>();
            long total = response.hits().total().value();

            for (Hit<UserDocument> hit : response.hits().hits()) {
                UserDocument doc = hit.source();
                if (doc == null) continue;
                items.add(new UserSearchResponse(
                        doc.getId(), doc.getDisplayName(), doc.getUsername(),
                        doc.getBio(), doc.getAvatarUrl(), doc.getFollowerCount(),
                        doc.getPostCount(), hit.score() != null ? hit.score() : 0.0
                ));
            }
            int totalPages2 = size > 0 ? (int) Math.ceil((double) total / size) : 0;
            return new PageResult<>(items, total, page, size, totalPages2);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<SuggestResponse>> suggest(String query, String type) {
        return Mono.fromCallable(() -> {
            List<String> results = switch (type) {
                case "user" -> userRepo.suggestUsers(query, 10);
                default -> blogRepo.suggestBlog(query, 10);
            };
            return results.stream()
                    .map(text -> new SuggestResponse(text, type))
                    .collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<String>> getTrendingSearches(int limit) {
        String key = "search:trending";
        return redis.opsForZSet().reverseRange(key, Range.closed(0L, (long) (limit - 1)))
                .collectList()
                .defaultIfEmpty(Collections.emptyList());
    }

    public Mono<List<String>> getTrendingTags(int limit) {
        String key = "search:trending:tags";
        return redis.opsForZSet().reverseRange(key, Range.closed(0L, (long) (limit - 1)))
                .collectList()
                .defaultIfEmpty(Collections.emptyList());
    }

    public Mono<Void> recordSearch(String query) {
        String key = "search:trending";
        return redis.opsForZSet().incrementScore(key, query, 1).then();
    }
}
