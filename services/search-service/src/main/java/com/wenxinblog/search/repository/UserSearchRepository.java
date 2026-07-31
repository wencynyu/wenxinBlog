package com.wenxinblog.search.repository;

import com.wenxinblog.search.model.UserDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.elasticsearch._types.FieldValue;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserSearchRepository {

    private final ReactiveElasticsearchOperations operations;

    public void indexUser(UserDocument doc) {
        operations.save(doc)
                .doOnSuccess(saved -> log.debug("Indexed user: {}", doc.getId()))
                .onErrorResume(e -> {
                    log.error("Failed to index user {}: {}", doc.getId(), e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    public void updateUser(UserDocument doc) {
        indexUser(doc);
    }

    public Mono<SearchPage<UserDocument>> searchUsers(String query, int page, int size) {
        NativeQuery q = NativeQuery.builder()
                .withQuery(m -> m.multiMatch(mm -> mm
                        .fields("display_name^3", "username^2", "bio")
                        .query(query)
                        .fuzziness("AUTO")))
                .withPageable(PageRequest.of(page, size))
                .build();
        return operations.searchForPage(q, UserDocument.class);
    }

    public Flux<String> suggestUsers(String query, int size) {
        NativeQuery q = NativeQuery.builder()
                .withQuery(m -> m.match(mm -> mm
                        .field("display_name")
                        .query(FieldValue.of(query))
                        .fuzziness("AUTO")))
                .withMaxResults(size)
                .build();
        return operations.search(q, UserDocument.class)
                .map(SearchHit::getContent)
                .map(UserDocument::getDisplayName)
                .onErrorResume(e -> {
                    log.error("User suggest failed: {}", e.getMessage());
                    return Flux.empty();
                });
    }
}
