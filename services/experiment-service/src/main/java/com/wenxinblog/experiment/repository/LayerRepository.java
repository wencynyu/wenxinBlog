package com.wenxinblog.experiment.repository;

import com.wenxinblog.experiment.entity.Layer;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LayerRepository extends ReactiveCrudRepository<Layer, UUID> {
    @Query("SELECT * FROM layers WHERE name = :name")
    Mono<Layer> findByName(String name);
}
