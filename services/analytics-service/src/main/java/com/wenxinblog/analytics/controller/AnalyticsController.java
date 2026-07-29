package com.wenxinblog.analytics.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final JdbcTemplate clickHouse;

    public AnalyticsController(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouse) {
        this.clickHouse = clickHouse;
    }

    @GetMapping("/count")
    public Mono<Long> count() {
        return Mono.fromCallable(() ->
                clickHouse.queryForObject("SELECT count() FROM behavior_events", Long.class))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/recent")
    public Mono<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "10") int limit) {
        return Mono.fromCallable(() ->
                clickHouse.queryForList(
                    "SELECT timestamp, user_id, event_type, experiment_id, variant FROM behavior_events ORDER BY timestamp DESC LIMIT ?",
                    limit))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
