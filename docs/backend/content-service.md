# Content Service

内容服务 - 负责图片/视频文件上传到 MinIO、元数据落库、按属主删除。

> 最近更新：2026-08-02（对照实际代码核对）

## 实现现状（体检）

这是一个**轻量的文件上传 CRUD**，不是旧文档描述的那种带转码/审核/CDN 的媒体处理平台。

| 模块                                                | 状态      | 说明                                                         |
| --------------------------------------------------- | --------- | ------------------------------------------------------------ |
| 单文件上传（MinIO + 落库）                          | ✅        | `POST /api/v1/content/upload`                                |
| 查文件 / 按博文查 / 删除                            | ✅        | 删除有 IDOR 属主校验（403）                                  |
| MinIO bucket 自动创建                               | ✅        | 启动时 `CommandLineRunner` 兜底建 bucket                     |
| Flyway 管理表结构                                   | ✅        | 独立 history table `flyway_schema_history_content`           |
| 图片处理（resize/crop/watermark/变体）              | ❌ 未实现 | 无端点、无图像库；`media_variants` 表存在但从不写入          |
| 视频处理（transcode/screenshot/thumbnail/playback） | ❌ 未实现 | 无端点、无 ffmpeg；无 `video_thumbnails` 表                  |
| 内容审核（阿里云 Green）                            | ❌ 未实现 | 无 SDK；`moderation_status` 列存在但从不读写，上传即 `READY` |
| RabbitMQ 异步队列                                   | ❌ 未实现 | 旧文档三套队列/exchange **代码里完全不存在**，无 amqp 依赖   |
| 阿里云 OSS / 生产 CDN                               | ❌ 未实现 | 无 OSS SDK，存储仅 MinIO                                     |
| 从 URL 上传 / 批量上传                              | ❌ 未实现 | 无端点                                                       |
| 处理状态查询 / WebSocket 推送                       | ❌ 未实现 | 无端点、无 websocket                                         |
| MIME 白名单                                         | ❌ 未实现 | 只校验 `max-size: 50MB`，不校验类型                          |
| Kafka                                               | ❌ 未接入 | `spring-kafka` 在 pom 里但代码零引用                         |

## 技术栈

- Java 25 + Spring Boot 4.0.4（WebFlux + R2DBC）
- MinIO `8.5.17`（唯一显式指定版本的依赖）
- PostgreSQL（`blog_db`，实例端口 5434，content 表与 blog 共库）
- Flyway（独立 history table）
- Actuator
- `spring-kafka`（依赖声明但未使用）

**端口：8004**（旧文档写的 8003 是错的，8003 是 blog-service）。

## 数据库 (blog_db) — Flyway 管理

迁移 `V1__init_content_schema.sql`，2 张表（无 `video_thumbnails`）：

### media_assets 表

```sql
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                      -- 无外键
    post_id UUID,                               -- 无外键
    type VARCHAR(20) NOT NULL,                  -- IMAGE / VIDEO（按 mime 前缀判定）
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT,
    height INT,
    duration INT,                               -- 视频时长，从不写入
    storage_provider VARCHAR(20),               -- 实际硬编码 "MINIO"
    bucket VARCHAR(100),
    object_key VARCHAR(500) NOT NULL,           -- uploads/{userId}/{uuid}/{filename}
    cdn_url VARCHAR(500),                       -- 从不写入
    status VARCHAR(20) DEFAULT 'PENDING',       -- 上传成功后直接置 READY
    processing_errors JSONB,
    moderation_status VARCHAR(20) DEFAULT 'PENDING',  -- 列存在，从不读写
    -- 注意：无 moderation_result 列
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- idx_media_user(user_id)、idx_media_post(post_id)、idx_media_status(status)
```

### media_variants 表（存在但当前为死表）

```sql
CREATE TABLE media_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    variant_type VARCHAR(20) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    size_bytes INT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

> `MediaVariant` 实体与 repository 都没有，该表无任何代码写入。

## API（仅 4 个端点）

```
POST   /api/v1/content/upload         - 上传单文件（X-User-Id, multipart "file"）
GET    /api/v1/content/{id}           - 文件元数据
DELETE /api/v1/content/{id}           - 删除（IDOR：X-User-Id==user_id，否则 403）
GET    /api/v1/content/post/{postId}  - 某博文的所有文件
```

> 旧文档列的 `/upload/url`、`/upload/batch`、`/{id}/resize|crop|watermark|variants|transcode|screenshot|thumbnails|playback|status`、WebSocket `/ws/content/{id}/status` **均不存在**。
> 删除时若文件不存在，`switchIfEmpty(empty)` 导致返回 200（不报 404）。`GET /{id}` 和 `/post/{postId}` 不做属主校验。

## 存储（MinIO）

`config/MinioConfig.java` 注入 `MinioClient`，启动自动建 bucket。

- bucket：`wenxinblog-content`
- object key：`uploads/{userId}/{UUID}/{filename}`（**扁平结构**，旧文档的 `original/`、`images/thumb/`、`videos/` 目录树是虚构的）
- 上传：`putObject`（内存缓冲整个文件 bytes）
- 删除：`removeObject`（best-effort，先删 MinIO 再删 DB 行）
- `storageProvider` 硬编码字符串 `"MINIO"`，无 LOCAL/OSS 分支

## 配置 (application.yml)

```yaml
server:
  port: 8004
spring:
  r2dbc: { url: r2dbc:postgresql://localhost:5434/blog_db, username: postgres, password: ${POSTGRES_PASSWORD:postgres} }
  flyway: { enabled: true, url: jdbc:postgresql://localhost:5434/blog_db, locations: classpath:db/migration }
  data: { redis: { host: localhost, port: 6379, password: ${REDIS_PASSWORD:redis} } }
minio:
  endpoint: http://localhost:9000
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: wenxinblog-content
file:
  upload:
    max-size: 50MB                            # 仅此一项，无 allowed-types
```

> 旧文档里大段的 `aliyun.oss`、`aliyun.green`、`image.variants/watermark`、`video.transcode/thumbnails`、`rabbitmq` 配置 **在 application.yml 里全部不存在**。

## 可观测性 / 运行

- OTel Java Agent 2.30.0（Dockerfile `-javaagent` 挂载）；Actuator 暴露 `/health` 等，base-path 为 `/`
- 堆内存：开发环境 `scripts/start-dev.sh` 统一 `-Xmx512m`；生产 Dockerfile 未显式设 -Xmx

```bash
cd services/content-service
mvn spring-boot:run
```
