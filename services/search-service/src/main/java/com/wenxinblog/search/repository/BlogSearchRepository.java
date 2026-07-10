package com.wenxinblog.search.repository;

import com.wenxinblog.search.dto.SearchRequest;
import com.wenxinblog.search.model.BlogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BlogSearchRepository {

    private final OpenSearchClient client;

    @Value("${opensearch.index.blog:wenxinblog-blog}")
    private String blogIndex;

    public void indexBlog(BlogDocument doc) {
        try {
            client.index(i -> i.index(blogIndex).id(doc.getId()).document(doc));
            log.debug("Indexed blog: {}", doc.getId());
        } catch (IOException e) {
            log.error("Failed to index blog {}: {}", doc.getId(), e.getMessage());
        }
    }

    public void updateBlog(BlogDocument doc) {
        indexBlog(doc);
    }

    public void deleteBlog(String blogId) {
        try {
            client.delete(d -> d.index(blogIndex).id(blogId));
            log.debug("Deleted blog: {}", blogId);
        } catch (IOException e) {
            log.error("Failed to delete blog {}: {}", blogId, e.getMessage());
        }
    }

    public SearchResponse<BlogDocument> searchBlogs(SearchRequest request) {
        try {
            // Build query using typed API
            MultiMatchQuery multiMatch = MultiMatchQuery.of(m -> m
                    .query(request.query())
                    .fields("title^3", "content^2", "summary^2")
                    .type(TextQueryType.BestFields));

            List<Query> mustQueries = List.of(Query.of(q -> q.multiMatch(multiMatch)));

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                    .must(mustQueries);

            // Sort
            SortOptions sortOption;
            if ("date".equals(request.sortBy())) {
                sortOption = SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("published_at").order(SortOrder.Desc))));
            } else if ("views".equals(request.sortBy())) {
                sortOption = SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("view_count").order(SortOrder.Desc))));
            } else if ("likes".equals(request.sortBy())) {
                sortOption = SortOptions.of(s -> s.field(FieldSort.of(f -> f.field("like_count").order(SortOrder.Desc))));
            } else {
                sortOption = SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            }

            return client.search(s -> s.index(blogIndex)
                    .from(request.page() * request.size())
                    .size(request.size())
                    .query(Query.of(q -> q.bool(boolBuilder.build())))
                    .sort(sortOption)
                    .highlight(h -> h
                            .fields("title", hf -> hf.preTags("<em>").postTags("</em>"))
                            .fields("content", hf -> hf.preTags("<em>").postTags("</em>").fragmentSize(200))
                            .preTags("<em>")
                            .postTags("</em>")),
                    BlogDocument.class);
        } catch (IOException e) {
            log.error("Blog search failed: {}", e.getMessage());
            throw new RuntimeException("Search failed", e);
        }
    }

    public List<String> suggestBlog(String query, int size) {
        try {
            SearchResponse<BlogDocument> response = client.search(s -> s.index(blogIndex)
                    .size(size)
                    .query(q -> q.match(m -> m
                            .field("title")
                            .query(org.opensearch.client.opensearch._types.FieldValue.of(query))
                            .fuzziness("AUTO"))),
                    BlogDocument.class);

            List<String> suggestions = new ArrayList<>();
            for (Hit<BlogDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    suggestions.add(hit.source().getTitle());
                }
            }
            return suggestions;
        } catch (IOException e) {
            log.error("Blog suggest failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
