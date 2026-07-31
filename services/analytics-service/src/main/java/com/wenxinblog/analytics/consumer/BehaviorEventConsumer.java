package com.wenxinblog.analytics.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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
    private final List<PendingEvent> buffer = Collections.synchronizedList(new ArrayList<>());

    public BehaviorEventConsumer(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse) {
        this.clickHouse = clickHouse;
    }

    @KafkaListener(topics = "user-behavior-events", groupId = "analytics-service")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        buffer.add(new PendingEvent(record, acknowledgment));
        if (buffer.size() >= BATCH_SIZE) flush();
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() { flush(); }

    /**
     * 将缓冲的事件写入 ClickHouse，成功后才 acknowledge 对应 Kafka offset（至少一次语义）。
     * 失败时批次留在 buffer 头部待重试且不提交 offset，崩溃/重启后由 Kafka 重新投递。
     */
    private synchronized void flush() {
        if (buffer.isEmpty()) return;
        List<PendingEvent> batch;
        synchronized (buffer) { batch = new ArrayList<>(buffer); buffer.clear(); }
        try {
            clickHouse.batchUpdate(
                "INSERT INTO behavior_events (user_id, event_type, post_id, experiment_id, variant, layer) VALUES (?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        try {
                            JsonNode n = mapper.readTree(batch.get(i).record.value());
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
            batch.forEach(pending -> pending.acknowledgment.acknowledge());
            log.info("Flushed {} events to ClickHouse and acknowledged offsets", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush {} events, keeping them unacknowledged for retry: {}", batch.size(), e.getMessage());
            synchronized (buffer) { buffer.addAll(0, batch); }
        }
    }

    private static final class PendingEvent {
        final ConsumerRecord<String, String> record;
        final Acknowledgment acknowledgment;
        PendingEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
            this.record = record;
            this.acknowledgment = acknowledgment;
        }
    }
}
