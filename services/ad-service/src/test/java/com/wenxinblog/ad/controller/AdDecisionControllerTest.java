package com.wenxinblog.ad.controller;

import com.wenxinblog.ad.dto.AdDecisionRequest;
import com.wenxinblog.ad.dto.AdDecisionResponse;
import com.wenxinblog.ad.service.AdDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdDecisionControllerTest {

    @Mock
    private AdDecisionService adDecisionService;

    @InjectMocks
    private AdDecisionController controller;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void decide_ShouldReturnAdDecisions() {
        AdDecisionRequest request = new AdDecisionRequest(
                "banner", "user123", "127.0.0.1", "Mozilla", null, 2);

        AdDecisionResponse response1 = new AdDecisionResponse(
                1L, 1L, "Ad Title 1", "http://example.com/image1.jpg",
                "http://example.com/landing1", "banner", new BigDecimal("1.00"));

        AdDecisionResponse response2 = new AdDecisionResponse(
                2L, 2L, "Ad Title 2", "http://example.com/image2.jpg",
                "http://example.com/landing2", "banner", new BigDecimal("0.50"));

        when(adDecisionService.decide(any(AdDecisionRequest.class)))
                .thenReturn(Flux.just(response1, response2));

        webTestClient.post()
                .uri("/internal/ads/decision")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(2)
                .jsonPath("$.data[0].creativeId").isEqualTo(1)
                .jsonPath("$.data[1].creativeId").isEqualTo(2);
    }

    @Test
    void decide_WithEmptyResult_ShouldReturnEmptyList() {
        AdDecisionRequest request = new AdDecisionRequest(
                "banner", "user123", "127.0.0.1", "Mozilla", null, 2);

        when(adDecisionService.decide(any(AdDecisionRequest.class)))
                .thenReturn(Flux.empty());

        webTestClient.post()
                .uri("/internal/ads/decision")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo(0)
                .jsonPath("$.data").isArray()
                .jsonPath("$.data.length()").isEqualTo(0);
    }
}
