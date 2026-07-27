package com.wenxinblog.experiment.service;

import com.wenxinblog.experiment.dto.AssignmentResponse;
import com.wenxinblog.experiment.dto.ExperimentRequest;
import com.wenxinblog.experiment.dto.ExperimentResponse;
import com.wenxinblog.experiment.entity.Experiment;
import com.wenxinblog.experiment.repository.ExperimentRepository;
import com.wenxinblog.experiment.repository.LayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 实验 CRUD + 生命周期（start/stop）。同一 layer 同一时刻仅允许一个 RUNNING 实验（互斥分流）。
 * stop 时清除该 layer 的全部分桶缓存，使流量重新计算。
 */
@Service
@RequiredArgsConstructor
public class ExperimentManageService {

    private final ExperimentRepository experimentRepo;
    private final LayerRepository layerRepo;
    private final ReactiveStringRedisTemplate redis;
    private final AssignmentService assignmentService;

    /** 创建实验：解析 layerName→layerId，初始状态 DRAFT。 */
    public Mono<ExperimentResponse> create(ExperimentRequest req) {
        return layerRepo.findByName(req.layerName())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Layer not found: " + req.layerName())))
                .flatMap(layer -> {
                    Experiment exp = Experiment.builder()
                            .name(req.name())
                            .description(req.description())
                            .layerId(layer.getId())
                            .status("DRAFT")
                            .trafficPct(req.trafficPct() != null ? req.trafficPct() : 100)
                            .config(req.config())
                            .build();
                    return experimentRepo.save(exp).map(saved -> ExperimentResponse.from(saved, layer));
                });
    }

    /** 列表查询：按 layer 和/或 status 过滤；二者皆空则返回全部。 */
    public Flux<ExperimentResponse> list(String layerName, String status) {
        boolean hasLayer = layerName != null && !layerName.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        Flux<Experiment> base;
        if (hasLayer) {
            base = layerRepo.findByName(layerName)
                    .flatMapMany(layer -> experimentRepo.findByLayerId(layer.getId()))
                    .switchIfEmpty(Flux.empty());
        } else if (hasStatus) {
            base = experimentRepo.findByStatus(status);
        } else {
            base = experimentRepo.findAll();
        }
        return base
                .filter(exp -> !hasStatus || status.equals(exp.getStatus()))
                .flatMap(exp -> layerRepo.findById(exp.getLayerId())
                        .map(layer -> ExperimentResponse.from(exp, layer))
                        .defaultIfEmpty(ExperimentResponse.from(exp, null)));
    }

    /** 启动实验：校验同 layer 无其它 RUNNING，置 RUNNING + startedAt。 */
    public Mono<ExperimentResponse> start(UUID id) {
        return experimentRepo.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Experiment not found: " + id)))
                .flatMap(exp -> experimentRepo.findRunningByLayerId(exp.getLayerId())
                        .filter(running -> !running.getId().equals(exp.getId()))
                        .flatMap(running -> Mono.<Experiment>error(
                                new IllegalStateException("Another experiment already RUNNING in this layer: " + running.getName())))
                        .switchIfEmpty(Mono.defer(() -> {
                            exp.setStatus("RUNNING");
                            exp.setStartedAt(LocalDateTime.now());
                            return experimentRepo.save(exp);
                        })))
                .flatMap(this::toResponse);
    }

    /** 停止实验：置 COMPLETED + endedAt，清空该 layer 分桶缓存。 */
    public Mono<ExperimentResponse> stop(UUID id) {
        return experimentRepo.findById(id)
                .switchIfEmpty(Mono.error(new NoSuchElementException("Experiment not found: " + id)))
                .flatMap(exp -> {
                    exp.setStatus("COMPLETED");
                    exp.setEndedAt(LocalDateTime.now());
                    return experimentRepo.save(exp)
                            .flatMap(saved -> layerRepo.findById(saved.getLayerId())
                                    .flatMap(layer -> clearLayerAssignments(layer.getName()))
                                    .then(Mono.just(saved)));
                })
                .flatMap(this::toResponse);
    }

    /** 调试入口：手动获取分桶。 */
    public Mono<AssignmentResponse> assign(String userId, String layerName) {
        return assignmentService.getAssignment(userId, layerName);
    }

    /** SCAN + DEL 清除指定 layer 的全部分桶缓存（实验停止/变更后强制重算）。 */
    private Mono<Void> clearLayerAssignments(String layerName) {
        return redis.scan(ScanOptions.scanOptions().count(500).match("ab:*:" + layerName).build())
                .collectList()
                .flatMap(keys -> keys.isEmpty() ? Mono.empty() : redis.delete(keys.toArray(new String[0])).then());
    }

    private Mono<ExperimentResponse> toResponse(Experiment exp) {
        return layerRepo.findById(exp.getLayerId())
                .map(layer -> ExperimentResponse.from(exp, layer))
                .defaultIfEmpty(ExperimentResponse.from(exp, null));
    }
}
