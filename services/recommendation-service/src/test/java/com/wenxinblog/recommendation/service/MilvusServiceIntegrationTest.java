package com.wenxinblog.recommendation.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.milvus.MilvusContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MilvusService + MilvusInitializer 真实容器集成测试（不起 Spring 上下文，直接 new）。
 * 此前 MilvusService 只有失败分支的单测、成功检索链路完全未验证过；
 * MilvusInitializer 的建 collection/索引/load 轨迹也只能在真实 Milvus 上验证。
 * 镜像不用与 compose 一致的 v2.6.13：Testcontainers MilvusContainer 的内嵌 etcd 启动方式
 * 在 2.6.x 上崩溃（exit 134）；v2.4.x 与 milvus-sdk-java 2.4.2 同代，与该模块兼容。
 */
class MilvusServiceIntegrationTest {

    private static final MilvusContainer MILVUS =
            new MilvusContainer(DockerImageName.parse("milvusdb/milvus:v2.4.13"));

    static {
        // 与 AbstractIntegrationTest 相同的 Docker 29 兼容处理
        System.setProperty("api.version", System.getProperty("api.version", "1.44"));
        MILVUS.start();
    }

    private MilvusServiceClient client;
    private MilvusService service;
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        client = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(MILVUS.getHost())
                .withPort(MILVUS.getMappedPort(19530))
                .build());
        service = new MilvusService(client, registry);
        // 非 Spring 环境注入 @Value 字段默认值（与生产配置一致）
        org.springframework.test.util.ReflectionTestUtils.setField(service, "nprobe", 16);

        // 每个用例前重建 collection，保证隔离（先删后建，等价干净库）
        dropIfPresent("blog_embeddings");
        dropIfPresent("user_embeddings");
        new MilvusInitializer(client).run(null);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    private void dropIfPresent(String collection) {
        R<Boolean> has = client.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collection).build());
        if (has.getStatus() == R.Status.Success.getCode() && Boolean.TRUE.equals(has.getData())) {
            client.dropCollection(io.milvus.param.collection.DropCollectionParam.newBuilder()
                    .withCollectionName(collection).build());
            try {
                Thread.sleep(500); // 等 release 生效
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 1024 维单位向量（与 collection DIM 一致）。 */
    private static float[] unitVector(int dim) {
        float[] v = new float[dim];
        float c = (float) (1.0 / Math.sqrt(dim));
        java.util.Arrays.fill(v, c);
        return v;
    }

    @Test
    void initializer_createsCollections_andSecondRunTakesExistsPath() {
        // setUp 里第一次 run 已建好 collection；再跑一次走"已存在→load"分支且不报错
        new MilvusInitializer(client).run(null);

        for (String name : List.of("blog_embeddings", "user_embeddings")) {
            R<Boolean> has = client.hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(name).build());
            assertThat(has.getStatus()).isEqualTo(R.Status.Success.getCode());
            assertThat(has.getData()).isTrue();
        }
    }

    @Test
    void upsertPost_thenSearchByVector_findsIt() {
        String postId = UUID.randomUUID().toString();
        float[] vec = unitVector(1024);

        StepVerifier.create(service.upsertPost(postId, "author-1", "标题", vec)).verifyComplete();

        List<MilvusService.SearchHit> hits = awaitSearch(vec, postId);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).postId()).isEqualTo(postId);
        assertThat(hits.get(0).score()).isGreaterThan(0.9); // 同向量 IP≈1
    }

    @Test
    void getPostVector_returnsStoredVector_andEmptyAfterRemoval() {
        String postId = UUID.randomUUID().toString();
        StepVerifier.create(service.upsertPost(postId, "author-1", "标题", unitVector(1024)))
                .verifyComplete();

        // 写后立读需要等可见性（bounded consistency），轮询直到读到
        float[] loaded = awaitVector(() -> service.getPostVector(postId).block(Duration.ofSeconds(5)));
        assertThat(loaded).hasSize(1024);

        StepVerifier.create(service.removePost(postId)).verifyComplete();
        // 删除后轮询直到查不到
        awaitUntil(() -> service.getPostVector(postId).block(Duration.ofSeconds(5)).length == 0);
        assertThat(service.getPostVector(postId).block(Duration.ofSeconds(5))).isEmpty();
    }

    @Test
    void upsertUserVector_thenGetUserVector_roundTrips() {
        String userId = UUID.randomUUID().toString();
        StepVerifier.create(service.upsertUserVector(userId, unitVector(1024))).verifyComplete();

        float[] loaded = awaitVector(() -> service.getUserVector(userId).block(Duration.ofSeconds(5)));
        assertThat(loaded).hasSize(1024);
    }

    @Test
    void getUserVector_missing_returnsEmptyArray() {
        StepVerifier.create(service.getUserVector("no-such-user"))
                .assertNext(v -> assertThat(v).isEmpty())
                .verifyComplete();
    }

    @Test
    void searchUsersByVector_findsSimilarUsers_andExcludesSelf() {
        String self = UUID.randomUUID().toString();
        String other1 = UUID.randomUUID().toString();
        String other2 = UUID.randomUUID().toString();
        float[] vec = unitVector(1024);
        StepVerifier.create(service.upsertUserVector(self, vec)).verifyComplete();
        StepVerifier.create(service.upsertUserVector(other1, vec)).verifyComplete();
        StepVerifier.create(service.upsertUserVector(other2, vec)).verifyComplete();

        AtomicReference<List<MilvusService.UserHit>> found = new AtomicReference<>();
        awaitUntil(() -> {
            List<MilvusService.UserHit> hits =
                    service.searchUsersByVector(vec, 5, self).block(Duration.ofSeconds(5));
            if (hits != null && hits.size() >= 2
                    && hits.stream().anyMatch(h -> other1.equals(h.userId()))
                    && hits.stream().anyMatch(h -> other2.equals(h.userId()))) {
                found.set(hits);
                return true;
            }
            return false;
        });
        // 排除自己：expr 过滤生效
        assertThat(found.get()).noneMatch(h -> self.equals(h.userId()));
    }

    @Test
    void upsertPost_isIdempotent_onSamePrimaryKey() {
        String postId = UUID.randomUUID().toString();
        StepVerifier.create(service.upsertPost(postId, "a", "v1", unitVector(1024))).verifyComplete();
        StepVerifier.create(service.upsertPost(postId, "a", "v2", unitVector(1024))).verifyComplete();

        List<MilvusService.SearchHit> hits = awaitSearch(unitVector(1024), postId);
        long count = hits.stream().filter(h -> postId.equals(h.postId())).count();
        assertThat(count).isEqualTo(1); // 主键 upsert 不产生重复行
    }

    // ---- 可见性轮询（Milvus bounded consistency 下写入非立即可见）----

    private List<MilvusService.SearchHit> awaitSearch(float[] vec, String expectPostId) {
        AtomicReference<List<MilvusService.SearchHit>> found = new AtomicReference<>();
        awaitUntil(() -> {
            List<MilvusService.SearchHit> hits = service.searchByVector(vec, 5).block(Duration.ofSeconds(5));
            if (hits != null && hits.stream().anyMatch(h -> expectPostId.equals(h.postId()))) {
                found.set(hits);
                return true;
            }
            return false;
        });
        return found.get();
    }

    private float[] awaitVector(java.util.function.Supplier<float[]> probe) {
        AtomicReference<float[]> out = new AtomicReference<>();
        awaitUntil(() -> {
            float[] v = probe.get();
            if (v != null && v.length > 0) {
                out.set(v);
                return true;
            }
            return false;
        });
        return out.get();
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("轮询被中断", e);
            }
        }
        throw new AssertionError("30s 内条件未满足（Milvus 数据可见性超时）");
    }
}
