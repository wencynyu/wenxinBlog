package com.wenxinblog.analytics.controller;

import com.wenxinblog.analytics.common.Permissions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
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
    public Mono<Long> count(@RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions) {
        if (!Permissions.has(permissions, "analytics:read")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need analytics:read"));
        }
        return Mono.fromCallable(() ->
                clickHouse.queryForObject("SELECT count() FROM behavior_events", Long.class))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/recent")
    public Mono<List<Map<String, Object>>> recent(@RequestParam(defaultValue = "10") int limit,
                                                  @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions) {
        if (!Permissions.has(permissions, "analytics:read")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need analytics:read"));
        }
        return Mono.fromCallable(() ->
                clickHouse.queryForList(
                    "SELECT timestamp, user_id, event_type, experiment_id, variant FROM behavior_events ORDER BY timestamp DESC LIMIT ?",
                    limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 广告报表（按 campaign × creative × 天）：曝光/点击/转化/CTR。数据来自 Kafka ad-events（含历史重放的 parse_error 行，已排除）。 */
    @GetMapping("/ads/report")
    public Mono<List<Map<String, Object>>> adReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions) {
        if (!Permissions.has(permissions, "analytics:read")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need analytics:read"));
        }
        LocalDate end = resolveRange(to).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(8);
        return Mono.fromCallable(() ->
                clickHouse.queryForList("""
                        SELECT campaign_id, creative_id,
                               toDate(timestamp) AS day,
                               countIf(event_type = 'IMPRESSION') AS impressions,
                               countIf(event_type = 'CLICK') AS clicks,
                               countIf(event_type = 'CONVERSION') AS conversions,
                               if(impressions > 0, round(clicks / impressions, 4), 0) AS ctr
                        FROM ad_events
                        WHERE timestamp >= ? AND timestamp < ?
                          AND event_type != 'parse_error'
                        GROUP BY campaign_id, creative_id, day
                        ORDER BY campaign_id, day
                        """, start, end))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 广告位报表（按 position_type × 天）：FEED/SIDEBAR 各自的曝光/点击/CTR。 */
    @GetMapping("/ads/report/positions")
    public Mono<List<Map<String, Object>>> adPositionReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions) {
        if (!Permissions.has(permissions, "analytics:read")) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "need analytics:read"));
        }
        LocalDate end = resolveRange(to).plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(8);
        return Mono.fromCallable(() ->
                clickHouse.queryForList("""
                        SELECT position_type,
                               toDate(timestamp) AS day,
                               countIf(event_type = 'IMPRESSION') AS impressions,
                               countIf(event_type = 'CLICK') AS clicks,
                               if(impressions > 0, round(clicks / impressions, 4), 0) AS ctr
                        FROM ad_events
                        WHERE timestamp >= ? AND timestamp < ?
                          AND event_type != 'parse_error'
                          AND position_type != ''
                        GROUP BY position_type, day
                        ORDER BY position_type, day
                        """, start, end))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private LocalDate resolveRange(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }
}
