package com.wenxinblog.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthorizationFilterTest {

    private AuthorizationFilter filter;
    @Mock private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthorizationFilter();
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private MockServerWebExchange exchange(String method, String path, String perms) {
        var req = MockServerHttpRequest.method(HttpMethod.valueOf(method), path);
        if (perms != null) {
            req.header("X-User-Permissions", perms);
        }
        return MockServerWebExchange.from(req.build());
    }

    @Test
    void managedEndpoint_MissingPermission_Returns403() {
        var ex = exchange("POST", "/api/v1/experiments", null);
        filter.filter(ex, chain).block();
        assertEquals(403, ex.getResponse().getStatusCode().value());
        verify(chain, never()).filter(any());
    }

    @Test
    void managedEndpoint_HasPermission_Forwards() {
        var ex = exchange("POST", "/api/v1/experiments", "post:create,experiment:manage");
        filter.filter(ex, chain).block();
        verify(chain, times(1)).filter(any());
    }

    @Test
    void backfill_WithoutRecommendationManage_Returns403() {
        var ex = exchange("POST", "/api/v1/recommend/admin/backfill", "post:create");
        filter.filter(ex, chain).block();
        assertEquals(403, ex.getResponse().getStatusCode().value());
    }

    @Test
    void backfill_WithRecommendationManage_Forwards() {
        var ex = exchange("POST", "/api/v1/recommend/admin/backfill", "recommendation:manage");
        filter.filter(ex, chain).block();
        verify(chain, times(1)).filter(any());
    }

    @Test
    void unlistedPath_Forwards() {
        var ex = exchange("GET", "/api/v1/posts", null);
        filter.filter(ex, chain).block();
        verify(chain, times(1)).filter(any());
    }

    @Test
    void listedPath_WrongMethod_Forwards() {
        // GET /campaigns/** 不在规则中（只有 POST/PUT/DELETE）
        var ex = exchange("GET", "/api/v1/campaigns", null);
        filter.filter(ex, chain).block();
        verify(chain, times(1)).filter(any());
    }

    @Test
    void experimentsAssign_Get_NotMatchedByManageRule_Forwards() {
        // GET /experiments/assign 是公开分配端点，不应被 experiment:manage 规则误伤
        var ex = exchange("GET", "/api/v1/experiments/assign", null);
        filter.filter(ex, chain).block();
        verify(chain, times(1)).filter(any());
    }

    @Test
    void analyticsRead_Missing_Returns403() {
        var ex = exchange("GET", "/api/v1/analytics/count", null);
        filter.filter(ex, chain).block();
        assertEquals(403, ex.getResponse().getStatusCode().value());
    }

    @Test
    void analyticsRead_HasPermission_Forwards() {
        var ex = exchange("GET", "/api/v1/analytics/count", "analytics:read");
        filter.filter(ex, chain).block();
        verify(chain, times(1)).filter(any());
    }
}
