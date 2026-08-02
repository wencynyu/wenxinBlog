package com.wenxinblog.analytics.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    private JdbcTemplate clickHouse;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        clickHouse = mock(JdbcTemplate.class);
        client = WebTestClient.bindToController(new AnalyticsController(clickHouse)).build();
    }

    @Test
    void count_WithoutPermission_Forbidden() {
        client.get().uri("/api/v1/analytics/count")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void count_WithPermission_Ok() {
        when(clickHouse.queryForObject("SELECT count() FROM behavior_events", Long.class)).thenReturn(100L);

        client.get().uri("/api/v1/analytics/count")
                .header("X-User-Permissions", "analytics:read")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class).isEqualTo(100L);
    }

    @Test
    void recent_WithoutPermission_Forbidden() {
        client.get().uri("/api/v1/analytics/recent")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void recent_WithPermission_Ok() {
        when(clickHouse.queryForList(anyString(), eq(10)))
                .thenReturn(List.of(Map.of("event_type", "view_post")));

        client.get().uri("/api/v1/analytics/recent")
                .header("X-User-Permissions", "analytics:read")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }
}
