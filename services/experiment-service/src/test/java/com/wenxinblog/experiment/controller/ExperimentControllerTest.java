package com.wenxinblog.experiment.controller;

import com.wenxinblog.experiment.dto.ExperimentRequest;
import com.wenxinblog.experiment.service.ExperimentAnalyzer;
import com.wenxinblog.experiment.service.ExperimentManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentControllerTest {

    @Mock
    private ExperimentManageService manageService;

    @Mock
    private ExperimentAnalyzer analyzer;

    @InjectMocks
    private ExperimentController controller;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToController(controller).build();
    }

    @Test
    void list_WithoutPermission_Forbidden() {
        client.get().uri("/api/v1/experiments")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void list_WithPermission_Ok() {
        when(manageService.list(any(), any())).thenReturn(Flux.empty());

        client.get().uri("/api/v1/experiments")
                .header("X-User-Permissions", "experiment:manage")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void create_WithoutPermission_Forbidden() {
        ExperimentRequest req = new ExperimentRequest("exp", "desc", "layer", 100, "{}");

        client.post().uri("/api/v1/experiments")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void start_WithoutPermission_Forbidden() {
        client.post().uri("/api/v1/experiments/{id}/start", UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void stop_WithoutPermission_Forbidden() {
        client.post().uri("/api/v1/experiments/{id}/stop", UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void results_WithoutPermission_Forbidden() {
        client.get().uri("/api/v1/experiments/{id}/results", UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void assign_IsPublic() {
        // assign 端点无需 experiment:manage，保持公开
        when(manageService.assign(eq("u1"), eq("layer1"))).thenReturn(Mono.empty());

        client.get().uri("/api/v1/experiments/assign?userId=u1&layer=layer1")
                .exchange()
                .expectStatus().isOk();
    }
}
