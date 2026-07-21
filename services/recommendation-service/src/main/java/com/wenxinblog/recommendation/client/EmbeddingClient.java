package com.wenxinblog.recommendation.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调 embedding 服务（本地 FastAPI+MPS / prod vLLM）的 OpenAI 兼容 /v1/embeddings。
 * 失败/超时返回空（上游降级为热门），不抛错。
 *
 * <p>内置轻量熔断：连续失败 {@link #FAILURE_THRESHOLD} 次后开启熔断，冷却 {@link #COOLDOWN_MS}
 * 内直接返回空（不再打下游），避免宕机的 embedding 服务被持续 hammer；冷却后放一次试探。
 *
 * <p>契约：POST {EMBEDDING_URL}/v1/embeddings {model,input,dimensions:1024}
 * → {data:[{embedding:[1024],index}],...}
 */
@Slf4j
@Component
public class EmbeddingClient {

    private static final int DIM = 1024;
    private static final String MODEL = "Qwen3-VL-Embedding-2B";
    private static final int FAILURE_THRESHOLD = 5;
    private static final long COOLDOWN_MS = 30_000;

    private final WebClient client;

    /** 连续失败计数 + 熔断打开的时间戳（多线程读，volatile 保护可见性）。 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openUntilMs = 0;

    public EmbeddingClient(@Value("${embedding.url:http://localhost:8008}") String url) {
        this.client = WebClient.builder().baseUrl(url).build();
    }

    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new float[0]);
        }
        return embedBatch(List.of(text)).map(list -> list.isEmpty() ? new float[0] : list.get(0));
    }

    /** 多模态：嵌入图像（URL/base64/路径）。VL 模型下与文本同空间，可用于图文混合检索。 */
    public Mono<float[]> embedImage(String imageSrc) {
        if (imageSrc == null || imageSrc.isBlank()) {
            return Mono.just(new float[0]);
        }
        return client.post()
                .uri("/embed-image")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("image", imageSrc, "dimensions", DIM))
                .retrieve()
                .bodyToMono(EmbedImageResponse.class)
                .timeout(Duration.ofSeconds(30)) // 图像处理更慢
                .map(resp -> resp == null || resp.embedding() == null ? new float[0] : resp.embedding())
                .onErrorResume(e -> {
                    log.warn("embed-image failed, degrading: {}", e.getMessage());
                    return Mono.just(new float[0]);
                });
    }

    public record EmbedImageResponse(float[] embedding, int dimensions) {}

    public Mono<List<float[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }
        // 熔断开启：冷却期内直接降级，不打下游
        if (System.currentTimeMillis() < openUntilMs) {
            return Mono.just(List.of());
        }
        return client.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", MODEL, "input", texts, "dimensions", DIM))
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .timeout(Duration.ofSeconds(20))
                .map(resp -> {
                    consecutiveFailures.set(0); // 成功，重置
                    return resp == null || resp.data() == null
                            ? List.<float[]>of()
                            : resp.data().stream().map(EmbedItem::embedding).toList();
                })
                .onErrorResume(e -> {
                    int n = consecutiveFailures.incrementAndGet();
                    if (n >= FAILURE_THRESHOLD) {
                        openUntilMs = System.currentTimeMillis() + COOLDOWN_MS;
                        log.warn("embedding circuit OPEN after {} consecutive failures, cool down {}ms",
                                n, COOLDOWN_MS);
                    } else {
                        log.warn("embedding call failed ({}/{}): {}", n, FAILURE_THRESHOLD, e.getMessage());
                    }
                    return Mono.just(List.of());
                });
    }

    /** 熔断是否开启（便于健康检查/指标）。 */
    public boolean isCircuitOpen() {
        return System.currentTimeMillis() < openUntilMs;
    }

    public record EmbedItem(float[] embedding, int index) {}

    public record EmbedResponse(List<EmbedItem> data, String model, int dimensions) {}
}
