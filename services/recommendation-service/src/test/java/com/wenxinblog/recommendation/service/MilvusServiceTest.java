package com.wenxinblog.recommendation.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * MilvusService 单测：mock MilvusServiceClient，验证各方法对 R 成功/失败的封装（boundedElastic 包裹、
 * 失败抛 RuntimeException）。真实向量路径由集成测试（对接 live Milvus）覆盖。
 */
@ExtendWith(MockitoExtension.class)
class MilvusServiceTest {

    @Mock
    private MilvusServiceClient client;

    @InjectMocks
    private MilvusService milvusService;

    private static float[] dim(int n) {
        return new float[n];
    }

    @Test
    void upsertPost_success_completes() {
        when(client.upsert(any(UpsertParam.class))).thenReturn(R.success());
        StepVerifier.create(milvusService.upsertPost("p1", "a1", "title", dim(1024)))
                .verifyComplete();
    }

    @Test
    void upsertPost_failure_errors() {
        when(client.upsert(any(UpsertParam.class))).thenReturn(R.failed(new RuntimeException("boom")));
        StepVerifier.create(milvusService.upsertPost("p1", "a1", "title", dim(1024)))
                .verifyError(RuntimeException.class);
    }

    @Test
    void removePost_success_completes() {
        when(client.delete(any(DeleteParam.class))).thenReturn(R.success());
        StepVerifier.create(milvusService.removePost("p1"))
                .verifyComplete();
    }

    @Test
    void removePost_failure_errors() {
        when(client.delete(any(DeleteParam.class))).thenReturn(R.failed(new RuntimeException("boom")));
        StepVerifier.create(milvusService.removePost("p1"))
                .verifyError(RuntimeException.class);
    }

    @Test
    void searchByVector_failure_errors() {
        when(client.search(any(SearchParam.class))).thenReturn(R.failed(new RuntimeException("boom")));
        StepVerifier.create(milvusService.searchByVector(dim(1024), 5))
                .verifyError(RuntimeException.class);
    }
}
