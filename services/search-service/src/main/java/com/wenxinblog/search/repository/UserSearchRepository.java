package com.wenxinblog.search.repository;

import com.wenxinblog.search.model.UserDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserSearchRepository {

    private final OpenSearchClient client;

    @Value("${opensearch.index.user:wenxinblog-user}")
    private String userIndex;

    public void indexUser(UserDocument doc) {
        try {
            client.index(i -> i.index(userIndex).id(doc.getId()).document(doc));
            log.debug("Indexed user: {}", doc.getId());
        } catch (IOException e) {
            log.error("Failed to index user {}: {}", doc.getId(), e.getMessage());
        }
    }

    public void updateUser(UserDocument doc) {
        indexUser(doc);
    }

    public SearchResponse<UserDocument> searchUsers(String query, int page, int size) {
        try {
            return client.search(s -> s.index(userIndex)
                    .from(page * size)
                    .size(size)
                    .query(q -> q.multiMatch(m -> m
                            .fields("display_name^3", "username^2", "bio")
                            .query(query)
                            .fuzziness("AUTO"))),
                    UserDocument.class);
        } catch (IOException e) {
            log.error("User search failed: {}", e.getMessage());
            throw new RuntimeException("User search failed", e);
        }
    }

    public List<String> suggestUsers(String query, int size) {
        try {
            SearchResponse<UserDocument> response = client.search(s -> s.index(userIndex)
                    .size(size)
                    .query(q -> q.match(m -> m
                            .field("display_name")
                            .query(FieldValue.of(query))
                            .fuzziness("AUTO"))),
                    UserDocument.class);

            List<String> suggestions = new ArrayList<>();
            for (Hit<UserDocument> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    suggestions.add(hit.source().getDisplayName());
                }
            }
            return suggestions;
        } catch (IOException e) {
            log.error("User suggest failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
