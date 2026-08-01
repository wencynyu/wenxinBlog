# Kafka 事件定义

> 最近更新：2026-08-02（对照实际配置核对）

## 概况

- 单节点 Kafka（`confluentinc/cp-kafka:7.4.0`，bootstrap `localhost:9092` / 容器内 `kafka:29092`）。
- Broker 开启 `auto.create.topics.enable=true`，**代码里没有 `NewTopic`/`KafkaAdmin` bean**，topic 首次生产时自动创建。
- 所有 Java 生产者统一用 `KafkaTemplate<String,String>` + `StringSerializer`，自行用 Jackson 把 payload 序列化成 JSON 字符串发送（**不是标准 CloudEvents 规范**，是项目自定义信封）。唯一的例外见 `ad-events`。
- Go 服务（auth/user）**完全不碰 Kafka**。
- kafka-ui 跑在 `http://localhost:8085`，可直接查看 topic / 消息。

## Topic 总览

| Topic                    | 生产者                                   | 消费者（groupId）                                                                                  | 状态                                                           |
| ------------------------ | ---------------------------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `wenxinblog.blog.events` | blog-service                             | search-service（`search-service`）、recommendation-service（`recommendation-service`）             | 正常                                                           |
| `user-behavior-events`   | recommendation-service                   | analytics-service（`analytics-service`）、recommendation-service（`recommendation-service-group`） | 正常；experiment-service 配了工厂但**无 listener**，未真正消费 |
| `ad-events`              | ad-service                               | **无**                                                                                             | 孤立生产者                                                     |
| `wenxinblog.user.events` | **无**（user-service 是 Go，不发 Kafka） | search-service（`search-service`）                                                                 | 孤立消费者                                                     |
| `wenxinblog.access-log`  | gateway                                  | **无**                                                                                             | 孤立生产者                                                     |

---

## 1. `wenxinblog.blog.events` — 博文生命周期

**生产者**：blog-service `BlogEventPublisher`（常量 `TOPIC = "wenxinblog.blog.events"`），
由 `PostService` 在 create/update/delete 时调用。message key = `postId`。

```json
{
  "eventType": "CREATE | UPDATE | DELETE",
  "data": {
    "id": "uuid",
    "title": "string",
    "content": "string",
    "summary": "string",
    "authorId": "uuid",
    "status": "published | draft | ...",
    "tags": ["tag1"],
    "publishedAt": "timestamp-string（仅非空时出现）",
    "viewCount": 0,
    "likeCount": 0,
    "commentCount": 0
  }
}
```

> DELETE 事件 `data` 只有 `{ "id": "<postId>" }`。

**消费者**：

- **search-service**（`@KafkaListener groupId="search-service"`）：CREATE/UPDATE → 建 `BlogDocument` 写入 Elasticsearch；DELETE → 删 ES 文档。
  > ⚠️ **契约缺口**：消费者还读 `authorName`、`category` 两个字段，但生产者**不发**这两个字段，ES 索引里始终为 null。
- **recommendation-service**（`groupId="recommendation-service"`）：CREATE/UPDATE 且 `status=="published"` → 调 embedding 服务把 title+summary 向量化后写入 Milvus；非 published 或 DELETE → 从 Milvus 移除。

---

## 2. `user-behavior-events` — 用户行为事件

**生产者**：recommendation-service `RecommendationService`（两处调用，**两种 payload**）。
message key = `userId`。

**变体 A — 推荐曝光事件**（`sendImpressionEvent`，出推荐流时发，带 A/B 实验分桶）：

```json
{
  "eventType": "impression",
  "userId": "uuid",
  "experimentId": "string",
  "variant": "string",
  "layer": "recommendation",
  "postIds": ["uuid", "...（前 10 条）"]
}
```

**变体 B — 行为反馈事件**（`recordFeedback`，view/like/comment/share 时发）：

```json
{
  "eventType": "view_post | like_post | comment_post | share_post",
  "userId": "uuid",
  "postId": "uuid",
  "tags": ["tag1"]
}
```

> `eventType` 在生产端会被强制加 `_post` 后缀。

**消费者**：

- **analytics-service**（`groupId="analytics-service"`）：缓冲（≤500 条或 5s）后批量写入 ClickHouse
  `behavior_events(user_id, event_type, post_id, experiment_id, variant, layer)`，写入成功后手动 ack（至少一次）。
- **recommendation-service**（`groupId="recommendation-service-group"`）：把兴趣标签写 `user_interest_tags`；
  用交互 post 的 Milvus 向量按 EMA 更新用户向量；记录已看/已赞 post 做推荐去重。
  权重：`view_post=0.1 / like_post=0.5 / comment_post=0.7 / share_post=0.8`，默认 `0.2`。
- **experiment-service**：`KafkaConfig` 里建了 consumer factory + `kafkaListenerContainerFactory`，
  注释说要消费本 topic 累积实验指标，但**全工程没有任何 `@KafkaListener` 方法**——已配线未实现。

---

## 3. `ad-events` — 广告追踪事件

**生产者**：ad-service `AdTrackingService.publishEvent`，在 `recordClick`（`CLICK`）和
`recordConversion`（`CONVERSION`）时发。message key = `userId`（可能为 null）。

> ⚠️ **已知缺陷：value 不是合法 JSON。** 生产者用 `java.util.Map.of(...).toString()` 拼字符串，
> 经 `StringSerializer` 发出，实际线上格式是 Java `Map.toString()`：
>
> ```
> {eventType=CLICK, campaignId=3, creativeId=3, userId=42, timestamp=2026-08-01T...}
> ```
>
> 无标准解析器能解析，待修成 Jackson 序列化的 JSON。

预期（修复后）字段：`eventType`、`campaignId`、`creativeId`、`userId`、`timestamp`。

**消费者**：**无**。（`IMPRESSION` 只入库 `ad_events` 表，不发 Kafka；`ad_events` 表名与该 topic 同名但无关。）

---

## 4. `wenxinblog.user.events` — 用户生命周期（孤立消费者）

**生产者**：仓库内**无**。自然的生成本该是 user-service，但 user-service 是 Go、不发 Kafka。

**消费者**：search-service（`groupId="search-service"`），期望信封（据消费端推断）：

```json
{
  "eventType": "CREATE | UPDATE | PROFILE_UPDATE | DELETE",
  "data": {
    "id": "uuid",
    "displayName": "string",
    "username": "string",
    "bio": "string",
    "avatarUrl": "string",
    "followerCount": 0,
    "postCount": 0
  }
}
```

CREATE/UPDATE/PROFILE_UPDATE → 写 `UserDocument` 到 ES；DELETE → 仅日志（不删索引）。

> 当前因为没有生产者，这个 listener 实际上收不到任何消息。

---

## 5. `wenxinblog.access-log` — 网关访问日志（孤立生产者）

**生产者**：gateway `AccessLogFilter`（常量 `ACCESS_LOG_TOPIC = "wenxinblog.access-log"`），
作为 Gateway 后置过滤器在每次响应时发。message key = `traceId`（取 `X-Trace-Id`/`X-B3-TraceId`，否则生成 UUID）。
payload 为 Jackson 序列化的 JSON（`AccessLogEvent`）。

字段：`traceId`、`requestId`、`timestamp`、`method`、`path`、`query`、`userId`、`clientIp`、
`userAgent`、`service`（按路径派生：auth/user/blog/content/search/recommendation/ad 或 `"unknown"`）、
`statusCode`、`responseTime(ms)`、`responseSize`、`level`（`ERROR`=5xx / `WARN`=4xx / `INFO`=10% 采样 / `DEBUG`）。

**消费者**：**无**。（疑为预留给 analytics-service 消费，目前 analytics-service 只消费 `user-behavior-events`。）

---

## 缺口与待办

| 问题                              | 说明                                                                    |
| --------------------------------- | ----------------------------------------------------------------------- |
| `ad-events` value 非 JSON         | 用 `Map.toString()` 发送，无消费者能解析；待改 Jackson JSON。           |
| `ad-events` 无消费者              | 生产了但没人订阅。                                                      |
| `wenxinblog.user.events` 无生产者 | user-service（Go）不发 Kafka，search-service 的 listener 收空。         |
| `wenxinblog.access-log` 无消费者  | gateway 发了没人收。                                                    |
| `blog.events` 契约缺口            | 生产者缺 `authorName`/`category`，search-service 消费端读不到。         |
| experiment-service 消费未实现     | `KafkaConfig` 配了 `user-behavior-events` 的工厂但无 `@KafkaListener`。 |
