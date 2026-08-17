package com.wenxinblog.analytics.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdEventConsumerTest {

    @Mock
    private JdbcTemplate clickHouse;

    @Mock
    private Acknowledgment acknowledgment;

    @Mock
    private PreparedStatement ps;

    private AdEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AdEventConsumer(clickHouse);
    }

    private BatchPreparedStatementSetter captureInsertedBatch(String value) {
        consumer.consume(new ConsumerRecord<>("ad-events", 0, 0L, "key", value), acknowledgment);
        consumer.scheduledFlush();

        var captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(clickHouse).batchUpdate(anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void consume_ValidJson_ShouldInsertAllFields() throws Exception {
        BatchPreparedStatementSetter batch = captureInsertedBatch("""
                {"eventType":"IMPRESSION","campaignId":100,"creativeId":1,
                 "userId":"user123","positionType":"FEED","timestamp":"2026-08-18T10:00:00.123"}""");

        assertEquals(1, batch.getBatchSize());
        batch.setValues(ps, 0);

        verify(ps).setString(1, "IMPRESSION");
        verify(ps).setLong(2, 100L);
        verify(ps).setLong(3, 1L);
        verify(ps).setString(4, "user123");
        verify(ps).setString(5, "FEED");
        verify(ps).setTimestamp(6, Timestamp.valueOf(LocalDateTime.of(2026, 8, 18, 10, 0, 0, 123000000)));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consume_OldNonJsonFormat_ShouldInsertParseErrorRow() throws Exception {
        // 2026-08-18 之前的旧消息是 Map.toString() 格式，earliest 重放时会进来
        BatchPreparedStatementSetter batch = captureInsertedBatch("{eventType=CLICK, campaignId=1}");

        assertEquals(1, batch.getBatchSize());
        batch.setValues(ps, 0);

        verify(ps).setString(1, "parse_error");
        verify(ps).setLong(2, 0L);
        // 解析失败也 ack，避免毒消息无限重投
        verify(acknowledgment).acknowledge();
    }

    @Test
    void flush_WhenClickHouseFails_ShouldKeepBatchUnacknowledged() {
        when(clickHouse.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new RuntimeException("ClickHouse down"));
        consumer.consume(new ConsumerRecord<>("ad-events", 0, 0L, "key",
                "{\"eventType\":\"CLICK\",\"campaignId\":1,\"creativeId\":1,\"timestamp\":\"2026-08-18T10:00:00\"}"), acknowledgment);

        consumer.scheduledFlush();
        verify(acknowledgment, never()).acknowledge();

        // 恢复后重试成功 → ack
        when(clickHouse.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class))).thenReturn(new int[1]);
        consumer.scheduledFlush();
        verify(acknowledgment).acknowledge();
    }
}
