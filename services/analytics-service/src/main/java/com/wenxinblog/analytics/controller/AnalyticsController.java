package com.wenxinblog.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 只读查询接口：通过 ClickHouse HTTP API 查询 behavior_events。
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final WebClient clickHouse;

    public AnalyticsController(@Qualifier("clickHouseClient") WebClient clickHouse) {
        this.clickHouse = clickHouse;
    }

    @GetMapping("/count")
    public Mono<String> count() {
        return clickHouse.get()
                .uri("/?query=" + URLEncoder.encode("SELECT count() FROM behavior_events", StandardCharsets.UTF_8))
                .retrieve()
                .bodyToMono(String.class)
                .map(r -> "{\"total_events\":" + r.trim() + "}");
    }

    @GetMapping("/recent")
    public Mono<String> recent(@RequestParam(defaultValue = "10") int limit) {
        String sql = "SELECT timestamp, user_id, event_type, experiment_id, variant "
                + "FROM behavior_events ORDER BY timestamp DESC LIMIT " + limit + " FORMAT JSON";
        return clickHouse.get()
                .uri("/?query=" + URLEncoder.encode(sql, StandardCharsets.UTF_8))
                .retrieve()
                .bodyToMono(String.class);
    }
}
