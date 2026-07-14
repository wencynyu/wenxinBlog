package com.wenxin.blog.service;

import com.wenxin.blog.entity.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 直接写 OpenSearch 索引（不走 Kafka）。
 * blog-service 在 createPost/updatePost/deletePost 后同步索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index.blog:wenxinblog-blog}")
    private String indexName;

    private static final Map<String, Object> EMPTY = Map.of();

    public void indexPost(Post post) {
        if (post == null || post.getId() == null) return;
        try {
            Map<String, Object> doc = new java.util.HashMap<>();
            doc.put("id", post.getId().toString());
            doc.put("title", post.getTitle());
            doc.put("content", post.getContent());
            doc.put("summary", post.getSummary() != null ? post.getSummary() : "");
            doc.put("author_id", post.getAuthorId().toString());
            doc.put("status", post.getStatus());
            doc.put("view_count", post.getViewCount() != null ? post.getViewCount() : 0);
            doc.put("like_count", post.getLikeCount() != null ? post.getLikeCount() : 0);
            doc.put("comment_count", post.getCommentCount() != null ? post.getCommentCount() : 0);
            doc.put("published_at", post.getPublishedAt() != null ? post.getPublishedAt().toString() : null);
            doc.put("created_at", post.getCreatedAt() != null ? post.getCreatedAt().toString() : null);

            IndexRequest<Map<String, Object>> req = IndexRequest.of(i ->
                i.index(indexName).id(post.getId().toString()).document(doc));
            openSearchClient.index(req);
            log.info("Indexed post {} to OpenSearch", post.getId());
        } catch (Exception e) {
            log.warn("Failed to index post {}: {}", post.getId(), e.getMessage());
        }
    }

    public void deletePost(java.util.UUID postId) {
        if (postId == null) return;
        try {
            DeleteRequest req = DeleteRequest.of(d -> d.index(indexName).id(postId.toString()));
            openSearchClient.delete(req);
            log.info("Deleted post {} from OpenSearch", postId);
        } catch (Exception e) {
            log.warn("Failed to delete post {} from OpenSearch: {}", postId, e.getMessage());
        }
    }
}
