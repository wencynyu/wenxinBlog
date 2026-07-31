package com.wenxinblog.search.repository;

import com.wenxinblog.search.dto.SearchRequest;
import com.wenxinblog.search.model.BlogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BlogSearchRepository {

    private final ReactiveElasticsearchOperations operations;

    public void indexBlog(BlogDocument doc) {
        operations.save(doc)
                .doOnSuccess(saved -> log.debug("Indexed blog: {}", doc.getId()))
                .onErrorResume(e -> {
                    log.error("Failed to index blog {}: {}", doc.getId(), e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    public void updateBlog(BlogDocument doc) {
        indexBlog(doc);
    }

    public void deleteBlog(String blogId) {
        operations.delete(blogId, BlogDocument.class)
                .doOnSuccess(id -> log.debug("Deleted blog: {}", blogId))
                .onErrorResume(e -> {
                    log.error("Failed to delete blog {}: {}", blogId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    public Mono<SearchPage<BlogDocument>> searchBlogs(SearchRequest request) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(request.query())
                                .fields("title^3", "content^2", "summary^2", "tags^2", "author_name^1")
                                .type(TextQueryType.BestFields)))))
                .withSort(buildSort(request.sortBy()))
                .withPageable(PageRequest.of(request.page(), request.size()))
                .withHighlightQuery(new HighlightQuery(
                        new Highlight(List.of(
                                new HighlightField("title", HighlightFieldParameters.builder()
                                        .withPreTags("<em>").withPostTags("</em>").build()),
                                new HighlightField("content", HighlightFieldParameters.builder()
                                        .withPreTags("<em>").withPostTags("</em>")
                                        .withFragmentSize(200).build())
                        )),
                        BlogDocument.class))
                .build();
        return operations.searchForPage(query, BlogDocument.class);
    }

    private SortOptions buildSort(String sortBy) {
        if ("date".equals(sortBy)) {
            return SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("published_at").order(SortOrder.Desc))));
        } else if ("views".equals(sortBy)) {
            return SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("view_count").order(SortOrder.Desc))));
        } else if ("likes".equals(sortBy)) {
            return SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("like_count").order(SortOrder.Desc))));
        }
        return SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
    }

    public Flux<String> suggestBlog(String query, int size) {
        NativeQuery q = NativeQuery.builder()
                .withQuery(m -> m.match(mm -> mm
                        .field("title")
                        .query(FieldValue.of(query))
                        .fuzziness("AUTO")))
                .withMaxResults(size)
                .build();
        return operations.search(q, BlogDocument.class)
                .map(SearchHit::getContent)
                .map(BlogDocument::getTitle)
                .onErrorResume(e -> {
                    log.error("Blog suggest failed: {}", e.getMessage());
                    return Flux.empty();
                });
    }
}
