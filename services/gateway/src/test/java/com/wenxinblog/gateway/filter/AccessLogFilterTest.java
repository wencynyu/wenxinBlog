package com.wenxinblog.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLogFilterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private GatewayFilterChain chain;

    private AccessLogFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AccessLogFilter(kafkaTemplate, new tools.jackson.databind.ObjectMapper());
    }

    @Test
    void testSendsLogToKafka() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-User-Id", "user123")
            .header("X-Trace-Id", "trace123")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), eq("trace123"), any());
    }

    @Test
    void testTraceIdFromHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .header("X-Trace-Id", "custom-trace-id")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), traceIdCaptor.capture(), any());
        assertEquals("custom-trace-id", traceIdCaptor.getValue());
    }

    @Test
    void testGeneratesTraceId_WhenNotProvided() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts")
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        ArgumentCaptor<String> traceIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), traceIdCaptor.capture(), any());
        assertNotNull(traceIdCaptor.getValue());
        assertTrue(traceIdCaptor.getValue().length() > 0);
    }

    @Test
    void testServiceNameMapping_AuthService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), any(), any());
    }

    @Test
    void testServiceNameMapping_ContentService() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/content/upload").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), any(), any());
    }

    @Test
    void testLogLevel_Error() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), any(), any());
    }

    @Test
    void testLogLevel_Warn() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/posts").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);

        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(any());

        filter.apply(new AccessLogFilter.Config()).filter(exchange, chain).block();

        verify(kafkaTemplate).send(eq("wenxinblog.access-log"), any(), any());
    }
}
