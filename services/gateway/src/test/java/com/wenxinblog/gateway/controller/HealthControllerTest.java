package com.wenxinblog.gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private HealthController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthController(webClient);
    }

    @Test
    void testGatewayHealth() {
        WebTestClient client = WebTestClient.bindToController(controller)
            .configureClient()
            .build();

        client.get().uri("/health/gateway")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.service").isEqualTo("gateway");
    }

    @Test
    void testServiceHealth_UnknownService() {
        WebTestClient client = WebTestClient.bindToController(controller)
            .configureClient()
            .build();

        client.get().uri("/health/service/unknown-service")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("DOWN")
            .jsonPath("$.service").isEqualTo("unknown-service");
    }

    @Test
    void testServiceHealth_KnownService() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec).uri(anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(Mono.just(Map.of("status", "UP"))).when(responseSpec).bodyToMono(Map.class);

        WebTestClient client = WebTestClient.bindToController(controller)
            .configureClient()
            .build();

        client.get().uri("/health/service/auth-service")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.service").isEqualTo("auth-service");
    }

    @Test
    void testHealthCheck_AllServicesDown() {
        // Set up mock chain so all service checks fail with error
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec).uri(anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(Mono.error(new RuntimeException("Connection failed"))).when(responseSpec).bodyToMono(Map.class);

        WebTestClient client = WebTestClient.bindToController(controller)
            .configureClient()
            .build();

        client.get().uri("/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("DEGRADED");
    }

    @Test
    void testHealthCheck_KnownServiceDown() {
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn(requestHeadersSpec).when((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec).uri(anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        doReturn(Mono.error(new RuntimeException("Connection failed"))).when(responseSpec).bodyToMono(Map.class);

        WebTestClient client = WebTestClient.bindToController(controller)
            .configureClient()
            .build();

        client.get().uri("/health/service/auth-service")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("DOWN")
            .jsonPath("$.service").isEqualTo("auth-service");
    }
}
