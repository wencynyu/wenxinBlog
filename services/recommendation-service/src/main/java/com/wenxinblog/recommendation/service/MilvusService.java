package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /** ANN 搜索 nprobe（越大召回越高、越慢），可经 milvus.nprobe 配置。 */
    @Value("${milvus.nprobe:16}")
    private int nprobe;

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
                    .withParams("{\"nprobe\":" + nprobe + "}")
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

    // ============ 用户兴趣向量（user_embeddings） ============

    /** upsert 用户兴趣向量（聚合兴趣标签得到）。 */
    public Mono<Void> upsertUserVector(String userId, float[] vector) {
        return Mono.fromCallable(() -> {
            List<InsertParam.Field> fields = List.of(
                    new InsertParam.Field("user_id", List.of(userId)),
                    new InsertParam.Field(MilvusConfig.VECTOR_FIELD, List.of(toFloatList(vector)))
            );
            R<?> r = client.upsert(UpsertParam.newBuilder()
                    .withCollectionName(MilvusConfig.USER_COLLECTION)
                    .withFields(fields)
                    .build());
            check(r, "upsertUserVector " + userId);
            return true;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** 取用户兴趣向量；不存在返回长度 0 的数组。 */
    public Mono<float[]> getUserVector(String userId) {
        return Mono.fromCallable(() -> queryVector(MilvusConfig.USER_COLLECTION, "user_id", userId, "getUserVector"))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 取博文向量（item-CF：用户向量 EMA 用）；不存在返回长度 0 的数组。 */
    public Mono<float[]> getPostVector(String postId) {
        return Mono.fromCallable(() -> queryVector(MilvusConfig.BLOG_COLLECTION, "post_id", postId, "getPostVector"))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 通用：按 pk 查某个 collection 的 embedding 向量。 */
    private float[] queryVector(String collection, String pkField, String pkValue, String op) {
        R<io.milvus.grpc.QueryResults> r = client.query(QueryParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(pkField + " == \"" + pkValue + "\"")
                .addOutField(MilvusConfig.VECTOR_FIELD)
                .withLimit(1L)
                .build());
        check(r, op + " " + pkValue);
        QueryResultsWrapper wrapper = new QueryResultsWrapper(r.getData());
        if (wrapper.getRowCount() == 0) {
            return new float[0];
        }
        List<?> fieldData = wrapper.getFieldWrapper(MilvusConfig.VECTOR_FIELD).getFieldData();
        return fieldData.isEmpty() ? new float[0] : toPrimArray(fieldData.get(0));
    }

    private List<Float> toFloatList(float[] v) {
        List<Float> list = new ArrayList<>(v.length);
        for (float f : v) {
            list.add(f);
        }
        return list;
    }

    private float[] toPrimArray(Object v) {
        if (v instanceof float[] arr) {
            return arr;
        }
        if (v instanceof List<?> list) {
            float[] arr = new float[list.size()];
            int i = 0;
            for (Object o : list) {
                arr[i++] = ((Number) o).floatValue();
            }
            return arr;
        }
        return new float[0];
    }

    private void check(R<?> r, String op) {
        if (r == null || r.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus " + op + " failed: "
                    + (r == null ? "null response" : r.getMessage()));
        }
    }
}
