# User Service

用户服务 - 负责用户资料、关注关系、个人主页

## 功能

- 用户资料管理 (头像、昵称、简介、网站)
- 关注/取关
- 粉丝列表/关注列表
- 用户主页
- 用户搜索
- 用户统计 (博文数、粉丝数等)

## 技术栈

- Go 1.23
- Fiber v2.52.0
- PostgreSQL (user_db)
- Redis (缓存关注关系)

## 数据库 (user_db)

### user_profiles 表
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES auth_db.users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    bio TEXT,
    website VARCHAR(255),
    location VARCHAR(100),
    company VARCHAR(100),
    birthday DATE,
    view_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_profiles_display_name ON user_profiles USING GIN(to_tsvector('simple', display_name));
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
```

### follows 表
```sql
CREATE TABLE follows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id UUID NOT NULL REFERENCES auth_db.users(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES auth_db.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(follower_id, following_id),
    CHECK (follower_id != following_id)
);

CREATE INDEX idx_follows_follower ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);
```

### user_stats 表 (统计数据缓存)
```sql
CREATE TABLE user_stats (
    user_id UUID PRIMARY KEY REFERENCES auth_db.users(id) ON DELETE CASCADE,
    post_count INT DEFAULT 0,
    follower_count INT DEFAULT 0,
    following_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## API

### 用户资料
```
GET    /api/v1/users/:id              - 获取用户资料
PUT    /api/v1/users/:id              - 更新用户资料 (需认证)
GET    /api/v1/users/:id/posts        - 用户博文列表
GET    /api/v1/users/:id/likes        - 用户喜欢的博文
GET    /api/v1/users/:id/stats        - 用户统计数据
```

### 关注关系
```
GET    /api/v1/users/:id/followers    - 粉丝列表 (分页)
GET    /api/v1/users/:id/following    - 关注列表 (分页)
POST   /api/v1/users/:id/follow       - 关注用户
DELETE /api/v1/users/:id/follow       - 取消关注
GET    /api/v1/me/following           - 当前用户关注的所有ID
```

### 搜索
```
GET    /api/v1/users/search?q=xxx     - 搜索用户 (全文搜索)
GET    /api/v1/users/suggest?q=xx     - 用户名自动补全
```

## Redis缓存设计

### 关注关系缓存
```
# 关注列表
Key: user:{userId}:following
Type: SET
TTL: 3600

# 粉丝列表
Key: user:{userId}:followers
Type: SET
TTL: 3600

# 关注状态
Key: user:{followerId}:follows:{followingId}
Type: STRING
Value: "1" | "0"
TTL: 3600
```

### 用户统计缓存
```
Key: user:{userId}:stats
Type: HASH
Fields: postCount, followerCount, followingCount, likeCount
TTL: 300
```

## 环境变量

```bash
PORT=8002
DATABASE_URL=postgres://postgres:postgres@localhost:5433/user_db
REDIS_URL=redis://localhost:6379
REDIS_PASSWORD=redis
AUTH_SERVICE_URL=http://localhost:8001
```

## 依赖服务

- **auth-service**: 验证用户身份、获取基础用户信息

## 运行

```bash
cd services/user-service
go mod download
go run cmd/server/main.go
```

## 测试

```bash
go test ./...
```
