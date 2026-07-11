package com.wenxin.blog.repository;

import com.wenxin.blog.entity.Tag;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

public interface TagRepository extends ReactiveCrudRepository<Tag, Integer> {
    Mono<Tag> findByName(String name);
    Mono<Tag> findBySlug(String slug);
    Flux<Tag> findAllByOrderByPostCountDesc();
}
