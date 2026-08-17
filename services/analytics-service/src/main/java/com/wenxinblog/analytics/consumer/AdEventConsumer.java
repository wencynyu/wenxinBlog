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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 消费 ad-events（曝光/点击/转化）写入 ClickHouse ad_events，供广告报表聚合。
 *  与 BehaviorEventConsumer 同一套微批 + 手动 ack 模式（至少一次语义）。
 *  注意：auto-offset-reset=earliest 会重放历史消息——2026-08-18 之前的旧格式
 *  （Map.toString()，非 JSON）无法解析，按惯例写 event_type='parse_error' 行，不污染聚合口径。 */
@Slf4j
@Component
public class AdEventConsumer {

    private final JdbcTemplate clickHouse;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final int BATCH_SIZE = 500;
    private final List<PendingEvent> buffer = Collections.synchronizedList(new ArrayList<>());

    public AdEventConsumer(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse) {
        this.clickHouse = clickHouse;
    }

    @KafkaListener(topics = "ad-events", groupId = "analytics-service")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        buffer.add(new PendingEvent(record, acknowledgment));
        if (buffer.size() >= BATCH_SIZE) flush();
    }

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() { flush(); }

    private synchronized void flush() {
        if (buffer.isEmpty()) return;
        List<PendingEvent> batch;
        synchronized (buffer) { batch = new ArrayList<>(buffer); buffer.clear(); }
        try {
            clickHouse.batchUpdate(
                "INSERT INTO ad_events (event_type, campaign_id, creative_id, user_id, position_type, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        try {
                            JsonNode n = mapper.readTree(batch.get(i).record.value());
                            ps.setString(1, n.path("eventType").asText(""));
                            ps.setLong(2, n.path("campaignId").asLong(0));
                            ps.setLong(3, n.path("creativeId").asLong(0));
                            ps.setString(4, n.path("userId").asText(""));
                            ps.setString(5, n.path("positionType").asText(""));
                            // 生产端 timestamp 是 LocalDateTime.toString()（如 2026-08-18T10:00:00.123，无时区）
                            ps.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.parse(
                                    n.path("timestamp").asText(java.time.LocalDateTime.now().toString()))));
                        } catch (Exception e) {
                            ps.setString(1, "parse_error");
                            ps.setLong(2, 0);
                            ps.setLong(3, 0);
                            ps.setString(4, "");
                            ps.setString(5, "");
                            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                        }
                    }
                    @Override
                    public int getBatchSize() { return batch.size(); }
                });
            batch.forEach(pending -> pending.acknowledgment.acknowledge());
            log.info("Flushed {} ad events to ClickHouse and acknowledged offsets", batch.size());
        } catch (Exception e) {
            log.error("Failed to flush {} ad events, keeping them unacknowledged for retry: {}", batch.size(), e.getMessage());
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
