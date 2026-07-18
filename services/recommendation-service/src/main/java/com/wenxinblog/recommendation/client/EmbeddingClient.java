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

/**
 * 调 embedding 服务（本地 FastAPI+MPS / prod vLLM）的 OpenAI 兼容 /v1/embeddings。
 * 失败/超时返回空（上游降级为热门），不抛错。
 *
 * <p>契约：POST {EMBEDDING_URL}/v1/embeddings {model,input,dimensions:1024}
 * → {data:[{embedding:[1024],index}],...}
 */
@Slf4j
@Component
public class EmbeddingClient {

    private static final int DIM = 1024;
    private static final String MODEL = "Qwen3-VL-Embedding-2B";

    private final WebClient client;

    public EmbeddingClient(@Value("${embedding.url:http://localhost:8008}") String url) {
        this.client = WebClient.builder().baseUrl(url).build();
    }

    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new float[0]);
        }
        return embedBatch(List.of(text)).map(list -> list.isEmpty() ? new float[0] : list.get(0));
    }

    public Mono<List<float[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }
        return client.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", MODEL, "input", texts, "dimensions", DIM))
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .timeout(Duration.ofSeconds(20))
                .map(resp -> resp == null || resp.data() == null
                        ? List.<float[]>of()
                        : resp.data().stream().map(EmbedItem::embedding).toList())
                .onErrorResume(e -> {
                    log.warn("embedding call failed (model service down?), degrading: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    public record EmbedItem(float[] embedding, int index) {}

    public record EmbedResponse(List<EmbedItem> data, String model, int dimensions) {}
}
