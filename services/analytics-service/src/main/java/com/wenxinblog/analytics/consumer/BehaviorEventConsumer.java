package com.wenxinblog.analytics.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消费 Kafka user-behavior-events，微批写入 ClickHouse（HTTP API，JSONEachRow 格式）。
 * 不用 JDBC——避免 clickhouse-jdbc 在 Java 25 上的兼容性问题。
 */
@Slf4j
@Component
public class BehaviorEventConsumer {

    private final WebClient clickHouse;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final int BATCH_SIZE = 500;
    private final List<String> buffer = Collections.synchronizedList(new ArrayList<>());

    public BehaviorEventConsumer(@Qualifier("clickHouseClient") WebClient clickHouse) {
        this.clickHouse = clickHouse;
    }

    @KafkaListener(topics = "user-behavior-events", groupId = "analytics-service")
    public void consume(ConsumerRecord<String, String> record) {
        buffer.add(record.value());
        if (buffer.size() >= BATCH_SIZE) {
            flush();
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() {
        flush();
    }

    private synchronized void flush() {
        if (buffer.isEmpty()) return;
        List<String> batch;
        synchronized (buffer) {
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }
        try {
            // 构建 JSONEachRow body（每行一个 JSON 对象）
            StringBuilder body = new StringBuilder();
            for (String json : batch) {
                try {
                    JsonNode node = mapper.readTree(json);
                    String userId = node.path("userId").asText("");
                    String eventType = node.path("eventType").asText("");
                    String postId = node.path("postId").asText("");
                    String experimentId = node.path("experimentId").asText("");
                    String variant = node.path("variant").asText("");
                    String layer = node.path("layer").asText("");
                    // 构建紧凑 JSON 行
                    body.append(String.format(
                        "{\"user_id\":\"%s\",\"event_type\":\"%s\",\"post_id\":\"%s\",\"experiment_id\":\"%s\",\"variant\":\"%s\",\"layer\":\"%s\"}",
                        escape(userId), escape(eventType), escape(postId),
                        escape(experimentId), escape(variant), escape(layer)));
                    body.append('\n');
                } catch (Exception e) {
                    body.append("{\"event_type\":\"parse_error\"}\n");
                }
            }
            // ClickHouse HTTP INSERT FORMAT JSONEachRow
            // 用 uriBuilder 避免 WebClient 双重编码 query param
            String query = "INSERT INTO behavior_events (user_id, event_type, post_id, experiment_id, variant, layer) FORMAT JSONEachRow";
            String bodyStr = body.toString();
            String response = clickHouse.post()
                    .uri(uri -> uri.path("/").queryParam("query", query).build())
                    .bodyValue(bodyStr)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Flushed {} events to ClickHouse", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush {} events to ClickHouse: {}", batch.size(), e.getMessage());
            synchronized (buffer) {
                buffer.addAll(0, batch);
            }
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
