# Recommendation Service

推荐服务 - 基于向量召回 + trending 兜底的个性化推荐。

> 最近更新：2026-08-02（对照实际代码核对）

> ⚠️ **本文档已与代码核对。** 旧版本里"全是占位/DEMO 数据"的描述**已过时**：Milvus 向量召回、真实 embedding、trending 兜底均已实现。不再返回任何 DEMO 数据。

## 实现现状（体检）

| 模块                                                 | 状态            | 说明                                                                          |
| ---------------------------------------------------- | --------------- | ----------------------------------------------------------------------------- |
| Milvus 向量召回（feed / related / related-by-image） | ✅ 真实         | SDK 2.4.2，dim **1024**，`MetricType.IP`（向量预归一化≈cosine），IVF_FLAT     |
| Embedding 生成                                       | ✅ 真实         | 外部服务 `embedding.url`，Qwen3-VL-Embedding-2B，1024 维，带熔断器            |
| Trending 兜底                                        | ✅ 真实         | 召回为空/匿名 → trending；trending 用真实 SQL 信号 + 时间衰减                 |
| Kafka 消费（blog 事件 + 行为事件）                   | ✅ 至少一次     | 消费者返回 `Mono<Void>`，`enable.auto.commit=false`                           |
| 兴趣标签 / 反馈 / viewed 去重                        | ✅              | Redis SET 30 天 + 行为加权更新用户向量（EMA item-CF）                         |
| A/B 权重                                             | ✅ 部分         | 读取 experiment-service 写的 `ab:{userId}:recommendation`，发 impression 事件 |
| 用户推荐 `/users`（人-人协同过滤）                   | ❌ 未实现       | `// TODO Phase 3`，恒返回空 `List.of()`                                       |
| `/admin/backfill` 鉴权                               | ❌ 缺失（待修） | 无任何 admin 校验，匿名可调（见下）                                           |
| 离线批任务 / 定时重排                                | ❌ 未实现       | 无 `@Scheduled`，旧文档的"每5分钟/每小时"调度不存在                           |
| DEMO 数据                                            | ❌ 已无         | 代码里无 demo/stub/mock 数据                                                  |

## 技术栈

- Java 25 + Spring Boot 4.0.4（WebFlux + R2DBC）
- **Milvus SDK `2.4.2`**（`io.milvus:milvus-sdk-java`；旧文档写的 2.6 不对）
- 外部 Embedding 服务（`embedding.url: http://localhost:8008`，FastAPI/vLLM）
- `spring-kafka`、`spring-boot-starter-data-redis-reactive`
- PostgreSQL（`blog_db`，实例端口 5434，读 `posts`/`authors`/`tags`）
- Flyway（独立 history table `flyway_schema_history_recommendation`）
- Actuator + `micrometer-registry-prometheus`
- OTel Java Agent 2.30.0

**端口：8006**（旧文档环境变量块写 8005 是错的，8005 是 search-service）。

## 召回链路（真实）

```
feed(userId, page, size)
  ├─ 匿名 / 无 X-User-Id        → trendingAsFeed(size)
  └─ 有 userId
       读 user_embeddings 向量（无则由兴趣标签聚合重算）
       → Milvus search(blog_embeddings, IP, nprobe=16, topK)
       → hybrid 重排：0.6*相似度 + 0.3*热度 + 0.1*新鲜度（权重可被 A/B 覆盖）
       → 过滤已看（user:viewed:{userId}）
       → 仍为空 → trendingAsFeed(size)   ← switchIfEmpty 兜底

related/{postId}        → 取该 post 向量 → Milvus search → 兜底 trending
related-by-image/{postId} → 走 VL embedding（/embed-image）→ 同上
trending                → 真实 SQL（见下）
```

### Trending 真实打分（`PostReadRepository.findTrending`）

```sql
ORDER BY (like_count*3 + view_count*1 + comment_count*5)
       * POWER(GREATEST(EXTRACT(EPOCH FROM (NOW() - published_at)), 0), -0.4) DESC
-- 仅 status='published' AND published_at IS NOT NULL；常量 LIKE_W=3, VIEW_W=1, COMMENT_W=5, DECAY=0.4
```

## Milvus 集合（启动时 `MilvusInitializer` 自动建）

| 集合              | 字段                                                                                                       | 索引                                       |
| ----------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| `blog_embeddings` | `post_id` VarChar(64) pk, `author_id` VarChar(64), `title` VarChar(512), `embedding` FloatVector(**1024**) | IVF_FLAT, `MetricType.IP`, `{"nlist":128}` |
| `user_embeddings` | `user_id` VarChar(64) pk, `embedding` FloatVector(1024)                                                    | IVF_FLAT, IP, nlist 128                    |

- 查询参数 `nprobe=16`；`DIM=1024`（旧文档写 768 不对）
- metric 用 **IP**（向量预归一化，等价 cosine），旧文档写的 `COSINE` 不对
- Milvus 连不上时 `MilvusInitializer` 捕获异常并记日志（"recommendation will degrade to trending"），不阻断启动

## API（均在 `/api/v1/recommend` 下）

```
GET  /feed                   ?page=0&size=10            X-User-Id 可选；无 type 参数（旧文档的 type 不存在）
GET  /related/{postId}       ?topK=10
GET  /related-by-image/{postId} ?topK=10                 图像相似博文（VL embedding）
GET  /trending               ?limit=10
GET  /users                  ?limit=                    X-User-Id 可选；⚠️ 当前恒返回 []（人-人 CF 未实现）
GET  /interests                                         X-User-Id 可选；无则返回 []
PUT  /interests              body: ["tag1","tag2"]      X-User-Id 必填
POST /feedback               body: {postId, action}     X-User-Id 必填；发行为事件
POST /admin/backfill         ?limit=1000                ⚠️ 无 admin 鉴权（见下）
```

响应统一是 `Result{code,message,data}`，`data` 直接是 `List<...>`。无旧文档的 `refreshId` / `reason` 字段。

### `/admin/backfill` 鉴权缺失（待修）

`backfill` handler 无 `@PreAuthorize`、无角色检查、无 `X-User-Id` 要求，`pom.xml` 也无 `spring-boot-starter-security`。任何匿名客户端都能 POST，触发最多 1000 次 embedding + Milvus upsert（成本/滥用风险）。**需要补 admin 鉴权。**

## Kafka 消费（至少一次）

`enable.auto.commit=false`，消费者返回 `Mono<Void>`，等 Milvus/embedding 写完才提交 offset；失败抛错 → 不 ack → 重投递。

| Topic                    | groupId                        | 处理                                                                                                                      |
| ------------------------ | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| `wenxinblog.blog.events` | `recommendation-service`       | CREATE/UPDATE 且 `status==published` → embedding + `upsertPost`；非 published → `removePost`；DELETE → `removePost`       |
| `user-behavior-events`   | `recommendation-service-group` | 按行为加权（like 0.5 / comment 0.7 / share 0.8 / view 0.1 / default 0.2），存 `user_interest_tags`，并用 EMA 更新用户向量 |

生产者：`/feedback` 和 impression 事件都发到 `user-behavior-events`。

> 旧文档列的 `wenxinblog.user.view/like/comment/follow/share` 五个 topic **不存在**。

## Redis 缓存

```
recommend:feed:{userId}:{page}:{size}   STRING(JSON)  TTL 10min
recommend:trending:{limit}              STRING(JSON)  TTL 10min
user:viewed:{userId}                    SET           TTL 30 天   已看去重
ab:{userId}:recommendation              STRING(JSON)  A/B 桶（experiment-service 写入）
```

> 旧文档的 `recommend:interests:*` HASH、`recommend:trending:daily/weekly` ZSET **不存在**；兴趣直接读 DB `user_interest_tags`。

## Flyway 表（迁移 V1）

- `recommendation_config`（`user_id, algorithm_type, weights JSONB`）— **当前未被任何代码使用**（占位）
- `user_interest_tags`（`user_id, tag, weight`，UNIQUE(user_id,tag)）— 实际在用

> `posts`/`authors`/`tags` 不在此迁移里——recommendation 直接读共享的 `blog_db`（blog-service 所有权）。

## A/B 测试（轻量）

`getExperimentWeights(userId)` 读 `ab:{userId}:recommendation` 的 `params.hybridWeights`（3 元素数组），作为 hybrid 重排权重；默认 `{0.6, 0.3, 0.1}`（相似度/热度/新鲜度）。`sendImpressionEvent` 读同一 key 取 `experimentId`+`variant`，发 impression 事件到 `user-behavior-events` 供 experiment-service 算 CTR。**这是 experiment-service 桶的消费者**，不是自包含实验框架；旧文档的 `feed_algorithm_v2` 设计是 aspirational。

## 可观测性 / 运行

- OTel Java Agent 2.30.0；大量自定义 Micrometer 指标：`recommendation_source_total{source=trending|...}`、`milvus_*_seconds`、`embedding_request_seconds`、`embedding_circuit_open`、`backfill_total{result}`、`recommendation_cache_total{cache,result}`、`user_vector_update_total`
- 堆内存：开发环境 `scripts/start-dev.sh` 统一 `-Xmx512m`；生产 Dockerfile 未显式设 -Xmx

```bash
cd services/recommendation-service
mvn spring-boot:run
```
