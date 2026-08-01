# Blog Service

博文服务 - 负责博文 CRUD、标签、评论、点赞。

> 最近更新：2026-08-02（对照实际代码核对）

## 实现现状（体检）

| 模块                    | 状态      | 说明                                                             |
| ----------------------- | --------- | ---------------------------------------------------------------- |
| 博文 CRUD / 发布 / 列表 | ✅        | 列表支持 `page/pageSize/authorId/sortBy/sortOrder/tag`           |
| 标签（多对多 + 计数）   | ✅        | `post_tags` 关联表，列表可按 tag 过滤                            |
| 评论（树形 + 计数）     | ✅        | 递归 CTE 删子评论并减计数                                        |
| 点赞（toggle + 计数）   | ✅        | `post_likes` 表                                                  |
| 草稿 / 发布状态机       | ✅        | `status: DRAFT/PUBLISHED`，列表只看 published                    |
| IDOR 属主校验           | ✅        | 改/删博文、删评论校验 `X-User-Id==author`，否则 **403**          |
| Kafka 事件下发          | ✅        | `wenxinblog.blog.events` 发 CREATE/UPDATE/DELETE（仅 published） |
| Markdown 渲染           | ❌ 未实现 | 旧文档列为功能，无 markdown 库，`content` 按纯文本存             |
| 收藏 / 书签             | ❌ 占位   | `post_favorites` 表 + DTO 字段存在，但无 entity/repo/controller  |
| Redis 缓存              | ❌ 未接入 | 依赖和配置都在，但代码从不使用 RedisTemplate                     |

## 技术栈

- Java 25 + Spring Boot 4.0.4（WebFlux + R2DBC）
- PostgreSQL（`blog_db`，实例端口 5434）
- `spring-kafka`（事件生产者）
- Flyway（**独立 history table `flyway_schema_history_blog`**，`baselineOnMigrate=true`）
- `spring-boot-starter-data-redis-reactive`（依赖在、配置在，但代码未用）
- Actuator + `micrometer-registry-prometheus`
- OTel Java Agent 2.30.0（`-javaagent`）

**端口：8003**（服务监听；5434 是数据库端口）。

## 数据库 (blog_db) — Flyway 管理

迁移文件 `src/main/resources/db/migration/V1__init_blog_schema.sql`。共 6 张表：

### posts 表

```sql
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,                    -- 无外键（跨服务，不引用 auth_db.users）
    title VARCHAR(200) NOT NULL,
    content TEXT,                               -- 可空（非 NOT NULL）
    summary VARCHAR(500),
    cover_image VARCHAR(500),
    status VARCHAR(20) DEFAULT 'DRAFT',         -- DRAFT / PUBLISHED
    view_count INT DEFAULT 0,                   -- INT，非 BIGINT
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- 注意：无 is_top 列
);
-- btree(author_id)、btree(status)、btree(published_at DESC)
```

### tags 表

```sql
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    slug VARCHAR(60) UNIQUE NOT NULL,           -- 60，非 50
    description VARCHAR(200),                   -- VARCHAR(200)，非 TEXT
    post_count INT DEFAULT 0
);
```

### post_tags 表（博文-标签多对多）

```sql
CREATE TABLE post_tags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag_id INT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);
-- btree(tag_id)
```

### comments 表

```sql
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,                    -- 无外键
    parent_id UUID REFERENCES comments(id) ON DELETE CASCADE,  -- CASCADE 删子评论
    content TEXT NOT NULL,
    like_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- btree(post_id)、btree(author_id)
```

### post_likes / post_favorites 表

```sql
CREATE TABLE post_likes (
    user_id UUID NOT NULL,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id)
);
-- post_favorites 结构同 post_likes；表存在但当前无 entity/repo/controller（占位）
```

## API

三个 `@RestController`（Spring 用 `{id}` 风格，非 `:id`）：

### 博文

```
POST   /api/v1/posts                - 创建（X-User-Id 作 authorId，信任网关头）
GET    /api/v1/posts/{id}           - 详情
PUT    /api/v1/posts/{id}           - 更新（属主校验，403）
DELETE /api/v1/posts/{id}           - 删除（属主校验，403）
GET    /api/v1/posts                - 列表（page,pageSize,authorId,sortBy,sortOrder,tag）
POST   /api/v1/posts/{id}/publish   - 发布（属主校验，403）
```

### 评论

```
POST   /api/v1/posts/{postId}/comments   - 发表评论
GET    /api/v1/posts/{postId}/comments   - 评论列表
DELETE /api/v1/comments/{id}             - 删除评论（属主校验，403）
```

### 点赞

```
POST   /api/v1/posts/{postId}/like       - toggle 点赞
GET    /api/v1/posts/{postId}/liked      - 当前用户是否已赞
```

> 属主校验失败统一返回 `ResponseStatusException(403, "Not the author")`。创建类接口不校验调用者，直接信任网关注入的 `X-User-Id`。
> 一个已知行为：`getPost`/`updatePost` 找不到资源时返回 **HTTP 200 + body `{code:404}`**（不是真实 404 状态码）。

## Kafka 事件

`service/BlogEventPublisher` 向 **`wenxinblog.blog.events`** 单 topic 发事件（消费者是 search-service / recommendation-service）：

- `eventType: CREATE | UPDATE | DELETE`，key = postId
- `data`: `{id,title,content,summary,authorId,status,tags[],publishedAt,viewCount,likeCount,commentCount}`
- 仅当 status 为 `published`（或变为 published）时发 CREATE/UPDATE；删除发 `{"eventType":"DELETE","data":{"id":...}}`
- 发送是 fire-and-forget，失败只记日志，不影响博文保存流程

## 配置 (application.yml 节选)

```yaml
server:
  port: 8003
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5434/blog_db
  flyway:
    enabled: true
    url: jdbc:postgresql://localhost:5434/blog_db
    locations: classpath:db/migration
  data:
    redis: { host: localhost, port: 6379 } # 配置在，代码未用
management:
  endpoints:
    web: { base-path: /, exposure: { include: health, info, metrics, prometheus } }
```

Kafka bootstrap 由 `@Value("${spring.kafka.bootstrap-servers:localhost:9092}")` 兜底（yml 里未写）。

## 可观测性 / 运行

- OTel Java Agent 2.30.0（Dockerfile 下载并 `-javaagent` 挂载）；OTLP gRPC 上报到 collector，`OTEL_SERVICE_NAME=blog-service`，`OTEL_INSTRUMENTATION_MICROMETER_ENABLED=true`
- 堆内存：开发环境 `scripts/start-dev.sh` 统一 `-Xmx512m`；生产 Dockerfile 未显式设 -Xmx（用 JVM 默认）

```bash
cd services/blog-service
mvn spring-boot:run
```
