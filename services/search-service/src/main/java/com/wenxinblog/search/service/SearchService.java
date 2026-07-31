package com.wenxinblog.search.service;

import com.wenxinblog.search.dto.*;
import com.wenxinblog.search.model.BlogDocument;
import com.wenxinblog.search.model.UserDocument;
import com.wenxinblog.search.repository.BlogSearchRepository;
import com.wenxinblog.search.repository.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        return blogRepo.searchBlogs(request).map(page -> {
            List<BlogSearchResponse> items = new ArrayList<>();
            for (SearchHit<BlogDocument> hit : page.getContent()) {
                BlogDocument doc = hit.getContent();
                Map<String, List<String>> hl = hit.getHighlightFields();
                List<String> hlTitle = hl != null && hl.get("title") != null ? hl.get("title") : List.of();
                List<String> hlContent = hl != null && hl.get("content") != null ? hl.get("content") : List.of();
                items.add(new BlogSearchResponse(
                        doc.getId(), doc.getTitle(), doc.getContent(), doc.getSummary(),
                        doc.getAuthorId(), doc.getAuthorName(), doc.getTags(),
                        doc.getCategory(), doc.getViewCount(), doc.getLikeCount(),
                        doc.getPublishedAt(), hit.getScore(),
                        hlTitle, hlContent
                ));
            }
            long total = page.getTotalElements();
            int totalPages = request.size() > 0 ? (int) Math.ceil((double) total / request.size()) : 0;
            return new PageResult<BlogSearchResponse>(items, total, request.page(), request.size(), totalPages);
        });
    }

    public Mono<PageResult<UserSearchResponse>> searchUsers(String query, int page, int size) {
        return userRepo.searchUsers(query, page, size).map(pg -> {
            List<UserSearchResponse> items = new ArrayList<>();
            for (SearchHit<UserDocument> hit : pg.getContent()) {
                UserDocument doc = hit.getContent();
                items.add(new UserSearchResponse(
                        doc.getId(), doc.getDisplayName(), doc.getUsername(),
                        doc.getBio(), doc.getAvatarUrl(), doc.getFollowerCount(),
                        doc.getPostCount(), hit.getScore()
                ));
            }
            long total = pg.getTotalElements();
            int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
            return new PageResult<UserSearchResponse>(items, total, page, size, totalPages);
        });
    }

    public Mono<List<SuggestResponse>> suggest(String query, String type) {
        Flux<String> results = switch (type) {
            case "user" -> userRepo.suggestUsers(query, 10);
            default -> blogRepo.suggestBlog(query, 10);
        };
        return results.collectList()
                .map(list -> list.stream()
                        .map(text -> new SuggestResponse(text, type))
                        .collect(Collectors.toList()));
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
