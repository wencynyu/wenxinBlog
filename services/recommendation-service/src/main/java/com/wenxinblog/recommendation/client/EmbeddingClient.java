package com.wenxinblog.recommendation.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
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
 * <p>业务指标（Micrometer）：
 * <ul>
 *   <li>embedding_request_seconds{status} — 调用延迟（Timer，含 count/sum → 成功率 + 平均延迟）</li>
 *   <li>embedding_circuit_open — 熔断状态 Gauge（0=closed, 1=open）</li>
 * </ul>
 */
@Slf4j
@Component
public class EmbeddingClient {

    private static final int DIM = 1024;
    private static final String MODEL = "Qwen3-VL-Embedding-2B";
    private static final int FAILURE_THRESHOLD = 5;
    private static final long COOLDOWN_MS = 30_000;

    private final WebClient client;
    private final MeterRegistry meterRegistry;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openUntilMs = 0;

    public EmbeddingClient(@Value("${embedding.url:http://localhost:8008}") String url,
                           MeterRegistry meterRegistry) {
        this.client = WebClient.builder().baseUrl(url).build();
        this.meterRegistry = meterRegistry;
        // 熔断状态 Gauge：值为 isCircuitOpen() 的实时结果
        meterRegistry.gauge("embedding_circuit_open", Tags.empty(), this, c -> c.isCircuitOpen() ? 1.0 : 0.0);
    }

    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new float[0]);
        }
        return embedBatch(List.of(text)).map(list -> list.isEmpty() ? new float[0] : list.get(0));
    }

    public Mono<float[]> embedImage(String imageSrc) {
        if (imageSrc == null || imageSrc.isBlank()) {
            return Mono.just(new float[0]);
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        return client.post()
                .uri("/embed-image")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("image", imageSrc, "dimensions", DIM))
                .retrieve()
                .bodyToMono(EmbedImageResponse.class)
                .timeout(Duration.ofSeconds(30))
                .map(resp -> {
                    sample.stop(recordTimer("embedding_request_seconds", "success", "image"));
                    return resp == null || resp.embedding() == null ? new float[0] : resp.embedding();
                })
                .onErrorResume(e -> {
                    sample.stop(recordTimer("embedding_request_seconds", "error", "image"));
                    log.warn("embed-image failed, degrading: {}", e.getMessage());
                    return Mono.just(new float[0]);
                });
    }

    public Mono<List<float[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }
        if (System.currentTimeMillis() < openUntilMs) {
            return Mono.just(List.of());
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        return client.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", MODEL, "input", texts, "dimensions", DIM))
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .timeout(Duration.ofSeconds(20))
                .map(resp -> {
                    consecutiveFailures.set(0);
                    sample.stop(recordTimer("embedding_request_seconds", "success", "text"));
                    return resp == null || resp.data() == null
                            ? List.<float[]>of()
                            : resp.data().stream().map(EmbedItem::embedding).toList();
                })
                .onErrorResume(e -> {
                    int n = consecutiveFailures.incrementAndGet();
                    sample.stop(recordTimer("embedding_request_seconds", "error", "text"));
                    if (n >= FAILURE_THRESHOLD) {
                        openUntilMs = System.currentTimeMillis() + COOLDOWN_MS;
                        log.warn("embedding circuit OPEN after {} consecutive failures, cool down {}ms", n, COOLDOWN_MS);
                    } else {
                        log.warn("embedding call failed ({}/{}): {}", n, FAILURE_THRESHOLD, e.getMessage());
                    }
                    return Mono.just(List.of());
                });
    }

    private Timer recordTimer(String name, String status, String type) {
        return Timer.builder(name).tag("status", status).tag("type", type).register(meterRegistry);
    }

    public boolean isCircuitOpen() {
        return System.currentTimeMillis() < openUntilMs;
    }

    public record EmbedItem(float[] embedding, int index) {}

    public record EmbedResponse(List<EmbedItem> data, String model, int dimensions) {}

    public record EmbedImageResponse(float[] embedding, int dimensions) {}
}
