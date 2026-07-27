package com.wenxinblog.experiment.repository;

import com.wenxinblog.experiment.entity.Experiment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ExperimentRepository extends ReactiveCrudRepository<Experiment, UUID> {
    Flux<Experiment> findByLayerId(UUID layerId);
    Flux<Experiment> findByStatus(String status);

    @Query("SELECT * FROM experiments WHERE layer_id = :layerId AND status = 'RUNNING'")
    Mono<Experiment> findRunningByLayerId(UUID layerId);
}
