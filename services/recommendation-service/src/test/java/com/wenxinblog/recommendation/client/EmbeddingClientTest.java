package com.wenxinblog.recommendation.client;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmbeddingClient 单测（MockWebServer 假 embedding 服务）：
 * 成功解析、失败降级空数组、连续 5 次失败熔断 fast-fail（不再打服务）、冷却后恢复。
 */
class EmbeddingClientTest {

    private static MockWebServer server;
    private EmbeddingClient client;

    @BeforeAll
    static void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.shutdown();
    }

    @BeforeEach
    void setUp() {
        client = new EmbeddingClient(server.url("/").toString(), new SimpleMeterRegistry());
    }

    private static void enqueueEmbedding(float... vector) {
        StringBuilder arr = new StringBuilder();
        for (float v : vector) arr.append(v).append(',');
        String vec = arr.length() > 0 ? arr.substring(0, arr.length() - 1) : "";
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"embedding\":[" + vec + "],\"index\":0}],\"model\":\"m\",\"dimensions\":"
                        + vector.length + "}"));
    }

    @Test
    void embed_success_parsesVector() {
        enqueueEmbedding(0.1f, 0.2f);

        int before = server.getRequestCount(); // MockWebServer 计数跨用例累计，按增量断言
        StepVerifier.create(client.embed("hello"))
                .assertNext(vec -> assertThat(vec).containsExactly(0.1f, 0.2f))
                .verifyComplete();
        assertThat(server.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void embed_blankText_returnsEmptyWithoutHttpCall() {
        int before = server.getRequestCount();
        StepVerifier.create(client.embed(null)).expectNextCount(1).verifyComplete(); // 空 vec 而非报错
        StepVerifier.create(client.embed("  ")).expectNextCount(1).verifyComplete();
        assertThat(server.getRequestCount() - before).isZero(); // 空白输入不打服务
    }

    @Test
    void embedBatch_emptyInput_returnsEmptyWithoutHttpCall() {
        int before = server.getRequestCount();
        StepVerifier.create(client.embedBatch(List.of())).expectNext(List.of()).verifyComplete();
        StepVerifier.create(client.embedBatch(null)).expectNext(List.of()).verifyComplete();
        assertThat(server.getRequestCount() - before).isZero();
    }

    @Test
    void embedBatch_serverError_degradesToEmpty() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        StepVerifier.create(client.embedBatch(List.of("a")))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();
        assertThat(client.isCircuitOpen()).isFalse(); // 单次失败不开闸
    }

    @Test
    void circuit_opensAfterFiveConsecutiveFailures_andFastFails() {
        int before = server.getRequestCount();
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
        }

        for (int i = 0; i < 5; i++) {
            StepVerifier.create(client.embedBatch(List.of("x")))
                    .assertNext(list -> assertThat(list).isEmpty())
                    .verifyComplete();
        }
        assertThat(client.isCircuitOpen()).isTrue();

        // 熔断打开：第 6 次直接空返回，不再请求服务端
        StepVerifier.create(client.embedBatch(List.of("x")))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();
        assertThat(server.getRequestCount() - before).isEqualTo(5);
    }

    @Test
    void circuit_recoversAfterCooldown() throws Exception {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
        }
        for (int i = 0; i < 5; i++) {
            client.embedBatch(List.of("x")).block();
        }
        assertThat(client.isCircuitOpen()).isTrue();

        // 把 openUntilMs 拨回过去，模拟冷却结束（不真等 30s）
        Field f = EmbeddingClient.class.getDeclaredField("openUntilMs");
        f.setAccessible(true);
        f.setLong(client, System.currentTimeMillis() - 1000);

        enqueueEmbedding(0.9f);
        StepVerifier.create(client.embedBatch(List.of("x")))
                .assertNext(list -> assertThat(list).isNotEmpty())
                .verifyComplete();
        assertThat(client.isCircuitOpen()).isFalse(); // 成功后闭合
    }

    @Test
    void embedImage_success_andErrorDegrades() {
        int before = server.getRequestCount();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"embedding\":[0.5],\"dimensions\":1}"));

        StepVerifier.create(client.embedImage("https://img.example.com/a.jpg"))
                .assertNext(vec -> assertThat(vec).containsExactly(0.5f))
                .verifyComplete();

        server.enqueue(new MockResponse().setResponseCode(503));
        StepVerifier.create(client.embedImage("https://img.example.com/a.jpg"))
                .assertNext(vec -> assertThat(vec).isEmpty())
                .verifyComplete();

        StepVerifier.create(client.embedImage(" ")).assertNext(v -> assertThat(v).isEmpty()).verifyComplete();
        assertThat(server.getRequestCount() - before).isEqualTo(2); // 空白输入不打服务
    }
}
