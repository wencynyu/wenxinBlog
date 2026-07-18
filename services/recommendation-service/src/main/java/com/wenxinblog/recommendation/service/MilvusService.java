package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 向量存储/检索。博文用 {@link #upsertPost}/{@link #removePost}/{@link #searchByVector}。
 *
 * <p>milvus-sdk-java 2.4.2 是<b>阻塞</b>客户端，所有调用包在 {@code Mono.fromCallable} +
 * {@code Schedulers.boundedElastic()} 里，避免卡 WebFlux 事件循环或 Kafka 消费线程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {

    private final MilvusServiceClient client;

    /** 一条检索命中：postId + 相似度分数（IP，向量已归一化即 cosine）。 */
    public record SearchHit(String postId, double score) {}

    /** upsert 博文向量（post_id 为主键，重复写幂等）。 */
    public Mono<Void> upsertPost(String postId, String authorId, String title, float[] vector) {
        return Mono.fromCallable(() -> {
            List<InsertParam.Field> fields = List.of(
                    new InsertParam.Field("post_id", List.of(postId)),
                    new InsertParam.Field("author_id", List.of(authorId != null ? authorId : "")),
                    new InsertParam.Field("title", List.of(title != null ? title : "")),
                    new InsertParam.Field(MilvusConfig.VECTOR_FIELD, List.of(toFloatList(vector)))
            );
            R<?> r = client.upsert(UpsertParam.newBuilder()
                    .withCollectionName(MilvusConfig.BLOG_COLLECTION)
                    .withFields(fields)
                    .build());
            check(r, "upsertPost " + postId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** 删除博文向量。 */
    public Mono<Void> removePost(String postId) {
        return Mono.fromCallable(() -> {
            R<?> r = client.delete(DeleteParam.newBuilder()
                    .withCollectionName(MilvusConfig.BLOG_COLLECTION)
                    .withExpr("post_id == \"" + postId + "\"")
                    .build());
            check(r, "removePost " + postId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** 按向量检索最相似的 topK 博文。 */
    public Mono<List<SearchHit>> searchByVector(float[] vector, int topK) {
        return Mono.fromCallable(() -> {
            List<List<Float>> vectors = List.of(toFloatList(vector));
            R<SearchResults> r = client.search(SearchParam.newBuilder()
                    .withCollectionName(MilvusConfig.BLOG_COLLECTION)
                    .withVectorFieldName(MilvusConfig.VECTOR_FIELD)
                    .withFloatVectors(vectors)
                    .withTopK(topK)
                    .withMetricType(MetricType.IP)
                    .withParams("{\"nprobe\":16}")
                    .build());
            check(r, "searchByVector");
            List<SearchResultsWrapper.IDScore> idScores =
                    new SearchResultsWrapper(r.getData().getResults()).getIDScore(0);
            List<SearchHit> hits = new ArrayList<>(idScores.size());
            for (SearchResultsWrapper.IDScore s : idScores) {
                hits.add(new SearchHit(s.getStrID(), s.getScore()));
            }
            return hits;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private List<Float> toFloatList(float[] v) {
        List<Float> list = new ArrayList<>(v.length);
        for (float f : v) {
            list.add(f);
        }
        return list;
    }

    private void check(R<?> r, String op) {
        if (r == null || r.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus " + op + " failed: "
                    + (r == null ? "null response" : r.getMessage()));
        }
    }
}
