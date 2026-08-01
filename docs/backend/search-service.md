# Search Service

搜索服务 - 负责博文/用户全文搜索、搜索建议、热门搜索词、搜索历史。

> 最近更新：2026-08-02（对照实际代码核对）

## 实现现状（体检）

| 模块                               | 状态      | 说明                                                                  |
| ---------------------------------- | --------- | --------------------------------------------------------------------- |
| 博文全文搜索（高亮 + 分页 + 排序） | ✅        | `multi_match` best_fields，title^3 等                                 |
| 用户搜索                           | ✅        | `multi_match` on display_name^3/username^2/bio                        |
| 搜索建议 suggest                   | ✅        | `match` + fuzziness AUTO（非 completion 字段）                        |
| 热门搜索词（trending）             | ✅        | Redis ZSET，每次搜索 `ZINCRBY`                                        |
| 消费 blog / user 事件建索引        | ✅        | Kafka 消费者返回 `Mono<Void>`，至少一次                               |
| 搜索历史 GET / DELETE              | ⚠️ 半成   | 端点在，但**写入路径未接**（见下）                                    |
| IK 分词 / 同义词 / completion 字段 | ❌ 未实现 | 旧文档的 `ik_max_word_synonym` 等是虚构的，用 ES 默认 standard 分析器 |
| BM25 k1/b 调参、字段权重 YAML      | ❌ 未实现 | 代码里无此配置，权重靠查询时 `^N` 写死                                |

## 技术栈

- Java 25 + Spring Boot 4.0.4（WebFlux）
- **Elasticsearch 9.3.8**（`pom.xml` 的 `<elasticsearch.version>9.3.8</elasticsearch.version>`）
- **`spring-boot-starter-data-elasticsearch`**（Spring Data Elasticsearch 6.x reactive）
  - 通过 `ReactiveElasticsearchOperations` + `NativeQuery`（`co.elastic.clients` elasticsearch-java 模型）查询
  - **已从旧的 OpenSearch + elasticsearch-java 客户端迁移过来**（旧文档写的 OpenSearch 2.11 已过时）
- `spring-kafka`（消费者）、`spring-boot-starter-data-redis-reactive`
- Actuator

**端口：8005**。ES 地址 `http://localhost:9200`。

## 索引（由 `@Document` 实体定义）

索引在应用启动时由 Spring Data ES 自动建（无手写 mapping JSON、无 IK 分析器、无 completion 字段）。

### wenxinblog-blog（`BlogDocument`）

```json
{
  "id": "keyword (@Id)",
  "title": "text",
  "content": "text",
  "summary": "text",
  "author_id": "keyword",
  "author_name": "keyword",
  "tags": ["keyword"],
  "category": "keyword",
  "status": "keyword",
  "view_count": "integer",
  "like_count": "integer",
  "comment_count": "integer",
  "published_at": "date (date_hour_minute_second)",
  "created_at": "date (date_hour_minute_second)"
}
```

### wenxinblog-user（`UserDocument`）

```json
{
  "id": "keyword (@Id)",
  "display_name": "keyword",
  "username": "keyword",
  "bio": "text",
  "avatar_url": "keyword",
  "follower_count": "integer",
  "post_count": "integer",
  "created_at": "date"
}
```

> 旧文档里 `blog_index`/`user_index` 的 `ik_max_word` 分词、`suggest` completion 字段、`author` 嵌套对象、`coverImage` 等**与实体不符**。实际用 ES 默认 standard 分析器，suggest 走 `match`+fuzziness 而非 completion。

## API

```
GET    /api/v1/search/blog?q=&page=0&size=10&sortBy=relevance&tags=&category=&authorId=
GET    /api/v1/search/users?q=&page=0&size=10              # 注意是 users（复数），旧文档写 user
GET    /api/v1/search/suggest?q=&type=blog|user
GET    /api/v1/search/trending?limit=10                    # 热门搜索词
GET    /api/v1/search/trending/tags?limit=20               # 热门标签（仅读，本服务不写入）
GET    /api/v1/search/history           (X-User-Id) limit=20
DELETE /api/v1/search/history           (X-User-Id)
```

### 搜索逻辑（真实）

- **博文**：`multi_match` best_fields，字段权重 `title^3, content^2, summary^2, tags^2, author_name^1`
- **排序**：`relevance`（`_score` desc，默认）/ `date`（published_at desc）/ `views` / `likes`
- **高亮**：`title` + `content`（fragment 200），`<em>...</em>`
- **用户**：`multi_match` on `display_name^3, username^2, bio`，fuzziness AUTO
- **suggest**：blog 走 `match` on `title` fuzziness AUTO；user 走 `match` on `display_name`

## Kafka 事件消费

两个消费者都返回 `Mono<Void>`，**Spring Kafka 等 Mono 完成后再提交 offset**；ES 写失败时返回 `Mono.error` → `DefaultErrorHandler` 重试（至少一次语义）。反序列化/字段解析错误则记日志后跳过（提交 offset，不重试）。

| Topic                    | groupId          | 处理                                                                    |
| ------------------------ | ---------------- | ----------------------------------------------------------------------- |
| `wenxinblog.blog.events` | `search-service` | CREATE/UPDATE → `indexBlog`/`updateBlog`（全量 `save`）；DELETE → 删除  |
| `wenxinblog.user.events` | `search-service` | CREATE/UPDATE/PROFILE_UPDATE → `indexUser`；DELETE → 仅记日志、不删索引 |

> 旧文档写的 `wenxinblog.blog.created/updated/deleted`、`wenxinblog.user.registered/updated` 及 `search-service-user` group **均不存在**，实际是单一 events topic。

## Redis 缓存设计

```
search:trending            ZSET   每次搜索 ZINCRBY 1（recordSearch）   无显式 TTL
search:trending:tags       ZSET   仅读（getTrendingTags）              本服务从不写入 → 实际为空
search:history:{userId}    LIST   LPUSH + trim 50 + TTL 30 天          见下方已知问题
```

### 已知问题：搜索历史写入未接

`SearchHistoryService.saveSearchHistory` 已实现（LPUSH + trim 50 + 30 天 TTL），但 **Controller 的搜索端点只调了 `recordSearch`（写 trending），从未调 `saveSearchHistory`**。因此 `GET /history` 能读、`DELETE /history` 能清，但列表始终是空的——写入路径断了，待修。

## 配置 (application.yml)

```yaml
server:
  port: 8005
spring:
  elasticsearch:
    uris: http://localhost:9200
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: search-service
      auto-offset-reset: earliest
  data:
    redis: { host: localhost, port: 6379, password: ${REDIS_PASSWORD:redis} }
search:
  max-results: 20
  default-page-size: 10
  highlight:
    pre-tags: ["<em>"]
    post-tags: ["</em>"]
```

> 旧文档的 `opensearch.*` 配置块、BM25 `similarity` YAML、字段权重 YAML **均不存在**。

## 可观测性 / 运行

- OTel Java Agent 2.30.0（Dockerfile `-javaagent`）。注：Dockerfile 顶部注释还写着 "OpenSearch"，是历史遗留，实际已是 ES 9.3.8
- 堆内存：开发环境 `scripts/start-dev.sh` 统一 `-Xmx512m`；生产 Dockerfile 未显式设 -Xmx

```bash
cd services/search-service
mvn spring-boot:run
```

索引初始化由 Spring Data ES 在首次写入时自动创建；如需重建索引，删除 `wenxinblog-blog` / `wenxinblog-user` 后重新消费 Kafka events 即可。
