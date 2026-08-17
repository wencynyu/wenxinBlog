package com.wenxinblog.ad.service;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafka;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AdEventPublisher(kafka, objectMapper);
        ReflectionTestUtils.setField(publisher, "adEventsTopic", "ad-events");
    }

    @Test
    void publish_ShouldSendValidJsonWithAllFields() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(null);
        LocalDateTime ts = LocalDateTime.of(2026, 8, 18, 10, 0);

        publisher.publish("IMPRESSION", 100L, 1L, "user123", "FEED", ts);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("ad-events"), eq("user123"), captor.capture());
        var node = objectMapper.readTree(captor.getValue());
        assertEquals("IMPRESSION", node.get("eventType").asString());
        assertEquals(100L, node.get("campaignId").asLong());
        assertEquals(1L, node.get("creativeId").asLong());
        assertEquals("user123", node.get("userId").asString());
        assertEquals("FEED", node.get("positionType").asString());
        assertEquals("2026-08-18T10:00", node.get("timestamp").asString());
    }

    @Test
    void publish_WithNullUserIdAndPosition_ShouldSendNullKeyAndEmptyStrings() {
        when(kafka.send(anyString(), any(), anyString())).thenReturn(null);

        publisher.publish("CLICK", 100L, 1L, null, null, LocalDateTime.now());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("ad-events"), isNull(), captor.capture());
        var node = objectMapper.readTree(captor.getValue());
        assertEquals("", node.get("userId").asString());
        assertEquals("", node.get("positionType").asString());
    }

    @Test
    void publish_WhenKafkaSendThrows_ShouldNotPropagate() {
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka connection failed"));

        assertDoesNotThrow(() ->
                publisher.publish("CLICK", 100L, 1L, "user123", null, LocalDateTime.now()));
    }

    @Test
    void publish_WhenKafkaSendCompletesExceptionally_ShouldNotPropagate() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Broker not available"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);

        assertDoesNotThrow(() ->
                publisher.publish("CLICK", 100L, 1L, "user123", null, LocalDateTime.now()));
    }
}
