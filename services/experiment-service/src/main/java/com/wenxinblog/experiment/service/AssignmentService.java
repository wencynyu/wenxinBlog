package com.wenxinblog.experiment.service;

import com.wenxinblog.experiment.dto.AssignmentResponse;
import com.wenxinblog.experiment.entity.Experiment;
import com.wenxinblog.experiment.repository.ExperimentRepository;
import com.wenxinblog.experiment.repository.LayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/**
 * 分桶核心：按 (userId, layerName) 正交哈希决定是否进实验、命哪个变体，结果缓存 7 天。
 *
 * <p>正交性：layerName 作为流量分桶的盐，experimentId 作为变体分桶的盐，
 * 使不同 layer / 不同实验的分桶互不相关。
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final ReactiveStringRedisTemplate redis;
    private final ExperimentRepository experimentRepo;
    private final LayerRepository layerRepo;
    private final ObjectMapper mapper;

    /**
     * 返回用户在指定 layer 的分桶结果。命中缓存（含 "null" 哨兵）直接返回；
     * 否则查 layer→running 实验，按流量比例与变体权重分桶后回写缓存。
     */
    public Mono<AssignmentResponse> getAssignment(String userId, String layerName) {
        String key = "ab:" + userId + ":" + layerName;
        return redis.opsForValue().get(key)
                .flatMap(json -> "null".equals(json)
                        ? Mono.just(AssignmentResponse.empty())
                        : Mono.fromCallable(() -> mapper.readValue(json, AssignmentResponse.class)))
                .switchIfEmpty(Mono.defer(() ->
                        layerRepo.findByName(layerName)
                                .flatMap(layer -> experimentRepo.findRunningByLayerId(layer.getId()))
                                .flatMap(exp -> assignAndCache(userId, layerName, exp, key))
                                .switchIfEmpty(cacheNull(key).then(Mono.just(AssignmentResponse.empty())))));
    }

    /**
     * 计算分桶并回写缓存。同步计算（处理 JSON 抛检异常），再异步写 Redis。
     * 返回 null 哨兵表示未进实验（流量不足）。
     */
    private Mono<AssignmentResponse> assignAndCache(String userId, String layerName, Experiment exp, String redisKey) {
        return Mono.fromCallable(() -> computeAssignment(userId, layerName, exp))
                .flatMap(a -> a == null
                        ? cacheNull(redisKey).then(Mono.just(AssignmentResponse.empty()))
                        : redisSet(redisKey, a).thenReturn(a));
    }

    /** 纯同步分桶计算：null 表示未命中实验。 */
    private AssignmentResponse computeAssignment(String userId, String layerName, Experiment exp) throws Exception {
        // 流量分桶：layerName 作盐，决定是否进入该实验
        int bucket = Math.floorMod((userId + ":" + layerName).hashCode(), 100);
        if (bucket >= exp.getTrafficPct()) {
            return null;
        }
        // 变体分桶：experimentId 作盐，在变体间按权重分配
        int vBucket = Math.floorMod((userId + ":" + exp.getId()).hashCode(), 100);
        JsonNode variants = mapper.readTree(exp.getConfig()).path("variants");
        int cumulative = 0;
        for (JsonNode v : variants) {
            cumulative += (int) (v.path("weight").asDouble(0.5) * 100);
            if (vBucket < cumulative) {
                Map<String, Object> params = mapper.convertValue(v.path("params"), Map.class);
                return new AssignmentResponse(exp.getId().toString(), v.path("name").asText(), params);
            }
        }
        // 权重舍入误差兜底：落到最后一个变体
        JsonNode last = variants.get(variants.size() - 1);
        Map<String, Object> params = mapper.convertValue(last.path("params"), Map.class);
        return new AssignmentResponse(exp.getId().toString(), last.path("name").asText(), params);
    }

    private Mono<Void> cacheNull(String key) {
        return redis.opsForValue().set(key, "null", CACHE_TTL).then();
    }

    private Mono<Boolean> redisSet(String key, AssignmentResponse a) {
        try {
            return redis.opsForValue().set(key, mapper.writeValueAsString(a), CACHE_TTL);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }
}
