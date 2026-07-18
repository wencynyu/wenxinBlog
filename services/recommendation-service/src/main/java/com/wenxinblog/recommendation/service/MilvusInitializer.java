package com.wenxinblog.recommendation.service;

import com.wenxinblog.recommendation.config.MilvusConfig;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时确保 Milvus collection（blog_embeddings / user_embeddings）存在并加载。
 * Milvus 不可用时不阻断启动（推荐降级为热门）。
 *
 * <p>backfill（把已有博文批量嵌入）依赖 embedding 服务，未在此处做；待 embedding 服务就绪后
 * 由 Kafka consumer 增量入库，或单独触发批量任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusInitializer implements ApplicationRunner {

    private final MilvusServiceClient client;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureBlogCollection();
            ensureUserCollection();
            log.info("Milvus collections ready: {}, {}", MilvusConfig.BLOG_COLLECTION, MilvusConfig.USER_COLLECTION);
        } catch (Exception e) {
            log.error("Milvus init failed, recommendation will degrade to trending: {}", e.getMessage());
        }
    }

    private void ensureBlogCollection() {
        ensure(MilvusConfig.BLOG_COLLECTION, List.of(
                FieldType.newBuilder().withName("post_id").withDataType(DataType.VarChar)
                        .withMaxLength(64).withPrimaryKey(true).withAutoID(false).build(),
                FieldType.newBuilder().withName("author_id").withDataType(DataType.VarChar)
                        .withMaxLength(64).build(),
                FieldType.newBuilder().withName("title").withDataType(DataType.VarChar)
                        .withMaxLength(512).build(),
                FieldType.newBuilder().withName(MilvusConfig.VECTOR_FIELD)
                        .withDataType(DataType.FloatVector).withDimension(MilvusConfig.DIM).build()
        ));
    }

    private void ensureUserCollection() {
        ensure(MilvusConfig.USER_COLLECTION, List.of(
                FieldType.newBuilder().withName("user_id").withDataType(DataType.VarChar)
                        .withMaxLength(64).withPrimaryKey(true).withAutoID(false).build(),
                FieldType.newBuilder().withName(MilvusConfig.VECTOR_FIELD)
                        .withDataType(DataType.FloatVector).withDimension(MilvusConfig.DIM).build()
        ));
    }

    private void ensure(String name, List<FieldType> fields) {
        R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(name).build());
        if (has.getStatus() == R.Status.Success.getCode() && Boolean.TRUE.equals(has.getData())) {
            client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).build());
            log.info("Milvus collection {} already exists, loaded", name);
            return;
        }
        CreateCollectionParam.Builder builder = CreateCollectionParam.newBuilder().withCollectionName(name);
        for (FieldType f : fields) {
            builder.addFieldType(f);
        }
        R<?> created = client.createCollection(builder.build());
        if (created.getStatus() != R.Status.Success.getCode()) {
            log.error("Create Milvus collection {} failed: {}", name, created.getMessage());
            return;
        }
        R<?> indexed = client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(name)
                .withFieldName(MilvusConfig.VECTOR_FIELD)
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"nlist\":128}")
                .build());
        if (indexed.getStatus() != R.Status.Success.getCode()) {
            log.error("Create index for {} failed: {}", name, indexed.getMessage());
            return;
        }
        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).build());
        log.info("Milvus collection {} created + indexed + loaded", name);
    }
}
