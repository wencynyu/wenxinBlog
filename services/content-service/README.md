# Content Service

内容服务 - 负责图片/视频上传、审核、CDN管理

## 功能

- 文件上传 (图片/视频)
- 图片处理 (压缩、裁剪、水印)
- 视频处理 (转码、截图)
- 内容审核 (阿里云AI)
- CDN分发 (MinIO本地 / 阿里云OSS生产)
- 文件管理 (删除、批量操作)

## 技术栈

- Java 25
- Spring Boot 4.0.4 (WebFlux + R2DBC)
- MinIO / 阿里云OSS
- RabbitMQ (异步处理任务)
- PostgreSQL (blog_db - content表)

## 数据库

### media_assets 表
```sql
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth_db.users(id),
    post_id UUID REFERENCES posts(id) ON DELETE SET NULL,
    type VARCHAR(20) NOT NULL, -- IMAGE, VIDEO
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT,
    height INT,
    duration INT, -- 视频时长(秒)

    -- 存储信息
    storage_provider VARCHAR(20), -- LOCAL, OSS
    bucket VARCHAR(100),
    object_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500),

    -- 处理状态
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, PROCESSING, READY, FAILED
    processing_errors JSONB,

    -- 审核状态
    moderation_status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    moderation_result JSONB,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_media_assets_user ON media_assets(user_id);
CREATE INDEX idx_media_assets_post ON media_assets(post_id);
CREATE INDEX idx_media_assets_status ON media_assets(status);
```

### media_variants 表 (图片变体)
```sql
CREATE TABLE media_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    variant_type VARCHAR(20) NOT NULL, -- THUMB, SMALL, MEDIUM, LARGE
    width INT NOT NULL,
    height INT NOT NULL,
    size_bytes INT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### video_thumbnails 表 (视频缩略图)
```sql
CREATE TABLE video_thumbnails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    timestamp INT NOT NULL, -- 截取时间点(秒)
    object_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## API

### 上传
```
POST   /api/v1/content/upload             - 上传文件
POST   /api/v1/content/upload/url         - 从URL上传
POST   /api/v1/content/upload/batch       - 批量上传
```

### 文件管理
```
GET    /api/v1/content/:id               - 获取文件信息
DELETE /api/v1/content/:id               - 删除文件
GET    /api/v1/content/post/:postId      - 获取博文的所有文件
```

### 图片处理
```
POST   /api/v1/content/:id/resize        - 调整尺寸
POST   /api/v1/content/:id/crop          - 裁剪
POST   /api/v1/content/:id/watermark     - 添加水印
GET    /api/v1/content/:id/variants      - 获取所有变体
```

### 视频处理
```
POST   /api/v1/content/:id/transcode     - 转码
POST   /api/v1/content/:id/screenshot    - 截图
GET    /api/v1/content/:id/thumbnails    - 获取缩略图
GET    /api/v1/content/:id/playback      - 获取播放信息
```

### 处理状态
```
GET    /api/v1/content/:id/status        - 获取处理状态
WebSocket /ws/content/:id/status         - 实时状态更新
```

## RabbitMQ队列

### 上传处理队列
```
Queue: content.upload.pending
Exchange: content.upload
Routing Key: upload.pending

Payload:
{
  "assetId": "uuid",
  "type": "IMAGE" | "VIDEO",
  "originalKey": "uploads/original/xxx.jpg"
}
```

### 图片处理队列
```
Queue: content.image.process
Exchange: content.image
Routing Key: image.process

Payload:
{
  "assetId": "uuid",
  "operations": ["resize", "watermark"],
  "params": { }
}
```

### 视频处理队列
```
Queue: content.video.transcode
Exchange: content.video
Routing Key: video.transcode

Payload:
{
  "assetId": "uuid",
  "targetFormat": "mp4",
  "resolutions": [720, 1080]
}
```

## 存储策略

### MinIO (本地开发)
```
Bucket: wenxinblog-content
结构:
├── original/        # 原始文件
├── images/          # 处理后的图片
│   ├── thumb/
│   ├── small/
│   └── large/
├── videos/          # 处理后的视频
└── thumbnails/      # 视频缩略图
```

### 阿里云OSS (生产)
```
Bucket: wenxinblog
CDN域名: https://cdn.wenxinblog.com

结构:
├── content/original/
├── content/images/
├── content/videos/
└── content/thumbnails/
```

## 环境变量

```yaml
server:
  port: 8003

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5434/blog_db
    username: postgres
    password: postgres

  rabbitmq:
    host: localhost
    port: 5672

# MinIO配置
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: wenxinblog-content

# 阿里云OSS (生产)
aliyun:
  oss:
    enabled: false
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    bucket: wenxinblog
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}

# 文件限制
file:
  upload:
    max-size: 50MB
    allowed-types:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
      - video/mp4
      - video/webm

# 图片处理配置
image:
  variants:
    thumb: { width: 150, height: 150, quality: 80 }
    small: { width: 400, quality: 85 }
    medium: { width: 800, quality: 90 }
    large: { width: 1200, quality: 95 }
  watermark:
    enabled: true
    image: "classpath:watermark.png"
    position: "bottom-right"
    opacity: 0.5

# 视频处理配置
video:
  transcode:
    formats: ["mp4", "webm"]
    resolutions: [480, 720, 1080]
    bitrate: "2000k"
  thumbnails:
    count: 5
    interval: 10
```

## 内容审核

### 阿里云AI审核
```yaml
aliyun:
  green:
    enabled: true
    region: cn-shanghai
    access-key-id: ${GREEN_ACCESS_KEY_ID}
    access-key-secret: ${GREEN_ACCESS_KEY_SECRET}

# 审核场景
scenes:
  - porn          # 色情
  - terrorism     # 暴恐
  - politics      # 政治
  - ad            # 广告
  - spam          # 垃圾信息
```

## 运行

```bash
cd services/content-service
mvn spring-boot:run
```
