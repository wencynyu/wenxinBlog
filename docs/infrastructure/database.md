# 数据库设计

## 数据库架构

```
┌─────────────────────────────────────────────────────────────┐
│                       应用层                                 │
├─────────────────────────────────────────────────────────────┤
│  auth_db      │  user_db      │  blog_db                    │
│  (认证)        │  (用户)        │  (业务)                      │
├───────────────┼───────────────┼─────────────────────────────┤
│  users        │  user_profiles│  posts                      │
│  oauth_accounts│  follows      │  tags                       │
│  sessions     │  user_stats   │  post_tags                  │
│  2fa          │               │  comments                   │
│               │               │  likes                      │
│               │               │  media_assets               │
│               │               │  ad_*                       │
└───────────────┴───────────────┴─────────────────────────────┘
         │                 │                  │
         └─────────────────┴──────────────────┘
                          │
                   PostgreSQL 15
              (3个独立数据库/集群)
```

## 数据库隔离策略

### 为什么分离数据库？

1. **服务隔离** - 每个微服务独立数据库
2. **性能隔离** - 高频查询(认证)与低频查询(广告)分离
3. **扩展灵活** - 可独立扩容、备份
4. **故障隔离** - 一个库问题不影响其他服务

### 跨库查询

```sql
-- 方案1: 应用层join (推荐)
-- 在Java/Go代码中分别查询后组装
user = user_db.get_user(userId)
posts = blog_db.get_posts_by_user(userId)
result = { user, posts }

-- 方案2: PostgreSQL FDW (只读场景)
CREATE SERVER user_db FOREIGN DATA WRAPPER postgres_fdw
  OPTIONS (host 'localhost', dbname 'user_db');

CREATE USER MAPPING FOR postgres SERVER user_db
  OPTIONS (user 'postgres', password 'postgres');

IMPORT FOREIGN SCHEMA public FROM SERVER user_db INTO public;

-- 现在可以跨库查询
SELECT p.*, u.display_name
FROM posts p
JOIN user_profiles u ON p.author_id = u.user_id;
```

## auth_db (认证数据库)

### users (用户表)
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    email_verified BOOLEAN DEFAULT FALSE,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    two_factor_secret VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
```

### oauth_accounts (OAuth绑定)
```sql
CREATE TABLE oauth_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP,
    UNIQUE(provider, provider_user_id)
);
```

### sessions (会话)
```sql
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_expires ON sessions(expires_at);
```

## user_db (用户数据库)

### user_profiles (用户资料)
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    bio TEXT,
    website VARCHAR(255),
    location VARCHAR(100),
    company VARCHAR(100),
    view_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_profile FOREIGN KEY (user_id)
      REFERENCES auth_db.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_display_name ON user_profiles USING GIN(to_tsvector('simple', display_name));
```

### follows (关注关系)
```sql
CREATE TABLE follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_follower FOREIGN KEY (follower_id)
      REFERENCES auth_db.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_following FOREIGN KEY (following_id)
      REFERENCES auth_db.users(id) ON DELETE CASCADE,
    UNIQUE(follower_id, following_id),
    CHECK (follower_id != following_id)
);

CREATE INDEX idx_follows_follower ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);
```

### user_stats (用户统计)
```sql
CREATE TABLE user_stats (
    user_id UUID PRIMARY KEY,
    post_count INT DEFAULT 0,
    follower_count INT DEFAULT 0,
    following_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_stats FOREIGN KEY (user_id)
      REFERENCES auth_db.users(id) ON DELETE CASCADE
);
```

## blog_db (业务数据库)

### posts (博文)
```sql
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    summary VARCHAR(500),
    cover_image VARCHAR(500),
    status VARCHAR(20) DEFAULT 'DRAFT',
    view_count BIGINT DEFAULT 0,
    like_count BIGINT DEFAULT 0,
    comment_count BIGINT DEFAULT 0,
    is_top BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,

    CONSTRAINT fk_post_author FOREIGN KEY (author_id)
      REFERENCES auth_db.users(id)
);

CREATE INDEX idx_posts_author ON posts(author_id);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_published ON posts(published_at DESC);
CREATE INDEX idx_posts_title ON posts USING GIN(to_tsvector('simple', title));
```

### tags (标签)
```sql
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    post_count INT DEFAULT 0
);
```

### post_tags (博文标签关联)
```sql
CREATE TABLE post_tags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX idx_post_tags_tag ON post_tags(tag_id);
```

### comments (评论)
```sql
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    parent_id UUID REFERENCES comments(id),
    content TEXT NOT NULL,
    like_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_comment_author FOREIGN KEY (author_id)
      REFERENCES auth_db.users(id)
);

CREATE INDEX idx_comments_post ON comments(post_id);
CREATE INDEX idx_comments_parent ON comments(parent_id);
```

### likes (点赞)
```sql
CREATE TABLE likes (
    user_id UUID NOT NULL,
    target_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL, -- POST, COMMENT
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, target_id, target_type),

    CONSTRAINT fk_like_user FOREIGN KEY (user_id)
      REFERENCES auth_db.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_likes_target ON likes(target_id, target_type);
```

## 数据迁移策略

### Flyway目录结构
```
services/
├── auth-service/
│   └── src/main/resources/db/migration/
│       ├── V1__create_users.sql
│       ├── V2__create_oauth.sql
│       └── V3__create_sessions.sql
├── user-service/
│   └── src/main/resources/db/migration/
│       └── V1__create_user_profiles.sql
└── blog-service/
    └── src/main/resources/db/migration/
        ├── V1__create_posts.sql
        └── V2__create_tags.sql
```

### Flyway配置
```yaml
# application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    validate-on-migrate: true
```

## 备份策略

### 全量备份 (每天凌晨)
```bash
#!/bin/bash
# backup-all.sh

BACKUP_DIR=/backup/$(date +%Y%m%d)
mkdir -p $BACKUP_DIR

# auth_db
pg_dump -h localhost -U postgres -d auth_db | gzip > $BACKUP_DIR/auth_db.sql.gz

# user_db
pg_dump -h localhost -U postgres -d user_db | gzip > $BACKUP_DIR/user_db.sql.gz

# blog_db
pg_dump -h localhost -U postgres -d blog_db | gzip > $BACKUP_DIR/blog_db.sql.gz

# 上传到OSS
aliyun oss cp $BACKUP_DIR oss://wenxinblog-backup/$(date +%Y%m%d)/ -r
```

### 增量备份 (WAL)
```bash
# postgresql.conf
wal_level = replica
archive_mode = on
archive_command = 'cp %p /backup/wal/%f'
```

### 恢复流程
```bash
# 1. 停止应用
# 2. 恢复全量备份
gunzip -c auth_db.sql.gz | psql -U postgres -d auth_db

# 3. 应用WAL日志
# 4. 验证数据完整性
# 5. 重启应用
```

## 监控指标

### 连接池监控
```
active_connections: 当前活跃连接
idle_connections: 空闲连接
max_connections: 最大连接数
connection_usage: 使用率
```

### 查询性能
```
slow_queries: 慢查询数量 (>1s)
query_duration_p95: 查询耗时P95
query_duration_p99: 查询耗时P99
deadlocks: 死锁次数
```

### 存储监控
```
database_size: 数据库大小
table_size: 各表大小
index_usage: 索引使用率
bloat_ratio: 膨胀率
```

## 优化建议

### 索引优化
```sql
-- 分析查询
EXPLAIN ANALYZE SELECT * FROM posts WHERE author_id = 'xxx';

-- 创建部分索引
CREATE INDEX idx_posts_published ON posts(published_at DESC)
WHERE status = 'PUBLISHED';

-- 创建表达式索引
CREATE INDEX idx_posts_lower_title ON posts(LOWER(title));
```

### 分区策略 (大数据量)
```sql
-- 按月分区logs表
CREATE TABLE logs (
    id SERIAL,
    created_at TIMESTAMP
) PARTITION BY RANGE (created_at);

CREATE TABLE logs_2024_01 PARTITION OF logs
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

### 连接池配置
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
      max-life-time: 1h
```
