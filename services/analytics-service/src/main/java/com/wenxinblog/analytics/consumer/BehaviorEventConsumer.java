package com.wenxinblog.analytics.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class BehaviorEventConsumer {

    private final JdbcTemplate clickHouse;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final int BATCH_SIZE = 500;
    private final List<String> buffer = Collections.synchronizedList(new ArrayList<>());

    public BehaviorEventConsumer(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse) {
        this.clickHouse = clickHouse;
    }

    @KafkaListener(topics = "user-behavior-events", groupId = "analytics-service")
    public void consume(ConsumerRecord<String, String> record) {
        buffer.add(record.value());
        if (buffer.size() >= BATCH_SIZE) flush();
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() { flush(); }

    private synchronized void flush() {
        if (buffer.isEmpty()) return;
        List<String> batch;
        synchronized (buffer) { batch = new ArrayList<>(buffer); buffer.clear(); }
        try {
            clickHouse.batchUpdate(
                "INSERT INTO behavior_events (user_id, event_type, post_id, experiment_id, variant, layer) VALUES (?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        try {
                            JsonNode n = mapper.readTree(batch.get(i));
                            ps.setString(1, n.path("userId").asText(""));
                            ps.setString(2, n.path("eventType").asText(""));
                            ps.setString(3, n.path("postId").asText(""));
                            ps.setString(4, n.path("experimentId").asText(""));
                            ps.setString(5, n.path("variant").asText(""));
                            ps.setString(6, n.path("layer").asText(""));
                        } catch (Exception e) {
                            ps.setString(1, "");
                            ps.setString(2, "parse_error");
                            ps.setString(3, "");
                            ps.setString(4, "");
                            ps.setString(5, "");
                            ps.setString(6, "");
                        }
                    }
                    @Override
                    public int getBatchSize() { return batch.size(); }
                });
            log.info("Flushed {} events to ClickHouse", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush {} events: {}", batch.size(), e.getMessage());
            synchronized (buffer) { buffer.addAll(0, batch); }
        }
    }
}
