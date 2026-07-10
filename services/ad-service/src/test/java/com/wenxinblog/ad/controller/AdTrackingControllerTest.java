package com.wenxinblog.ad.controller;

import com.wenxinblog.ad.entity.AdEvent;
import com.wenxinblog.ad.service.AdTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdTrackingControllerTest {

    @Mock
    private AdTrackingService trackingService;

    @InjectMocks
    private AdTrackingController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void click_WithValidRequest_ShouldRecordClick() {
        AdEvent event = AdEvent.builder()
                .id(1L)
                .creativeId(1L)
                .userId("user123")
                .eventType("CLICK")
                .createdAt(LocalDateTime.now())
                .build();

        when(trackingService.recordClick(eq(1L), eq("user123"), isNull(), isNull()))
                .thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/ads/click")
                .bodyValue(Map.of(
                        "creativeId", 1,
                        "userId", "user123"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$..data.eventType").isEqualTo("CLICK");
    }

    @Test
    void click_WithDuplicateClick_ShouldReturnNull() {
        when(trackingService.recordClick(eq(1L), eq("user123"), isNull(), isNull()))
                .thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/api/v1/ads/click")
                .bodyValue(Map.of(
                        "creativeId", 1,
                        "userId", "user123"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isEmpty();
    }

    @Test
    void conversion_WithValidRequest_ShouldRecordConversion() {
        AdEvent event = AdEvent.builder()
                .id(1L)
                .creativeId(1L)
                .userId("user123")
                .eventType("CONVERSION")
                .createdAt(LocalDateTime.now())
                .build();

        when(trackingService.recordConversion(eq(1L), eq("user123"), eq("127.0.0.1"), eq("Mozilla")))
                .thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/ads/conversion")
                .bodyValue(Map.of(
                        "creativeId", 1,
                        "userId", "user123",
                        "ipAddress", "127.0.0.1",
                        "userAgent", "Mozilla"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.eventType").isEqualTo("CONVERSION");
    }

    @Test
    void conversion_WithAllFields_ShouldRecordWithAllFields() {
        AdEvent event = AdEvent.builder()
                .id(1L)
                .creativeId(2L)
                .userId("user456")
                .eventType("CONVERSION")
                .ipAddress("192.168.1.1")
                .userAgent("Chrome")
                .createdAt(LocalDateTime.now())
                .build();

        when(trackingService.recordConversion(eq(2L), eq("user456"), eq("192.168.1.1"), eq("Chrome")))
                .thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/ads/conversion")
                .bodyValue(Map.of(
                        "creativeId", 2,
                        "userId", "user456",
                        "ipAddress", "192.168.1.1",
                        "userAgent", "Chrome"
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.creativeId").isEqualTo(2)
                .jsonPath("$.data.userId").isEqualTo("user456")
                .jsonPath("$.data.ipAddress").isEqualTo("192.168.1.1")
                .jsonPath("$.data.userAgent").isEqualTo("Chrome");
    }

    @Test
    void click_WithOnlyCreativeId_ShouldPassNullForOtherFields() {
        AdEvent event = AdEvent.builder()
                .id(1L)
                .creativeId(1L)
                .userId(null)
                .eventType("CLICK")
                .createdAt(LocalDateTime.now())
                .build();

        when(trackingService.recordClick(eq(1L), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/ads/click")
                .bodyValue(Map.of("creativeId", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.eventType").isEqualTo("CLICK");

        verify(trackingService).recordClick(eq(1L), isNull(), isNull(), isNull());
    }

    @Test
    void conversion_WithOnlyCreativeId_ShouldPassNullForOtherFields() {
        AdEvent event = AdEvent.builder()
                .id(1L)
                .creativeId(1L)
                .userId(null)
                .eventType("CONVERSION")
                .createdAt(LocalDateTime.now())
                .build();

        when(trackingService.recordConversion(eq(1L), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/ads/conversion")
                .bodyValue(Map.of("creativeId", 1))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data.id").isEqualTo(1)
                .jsonPath("$.data.eventType").isEqualTo("CONVERSION");

        verify(trackingService).recordConversion(eq(1L), isNull(), isNull(), isNull());
    }
}
