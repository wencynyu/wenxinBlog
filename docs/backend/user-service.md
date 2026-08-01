# User Service

用户服务 - 负责用户资料、关注关系、用户主页、用户搜索、统计缓存。

> 最近更新：2026-08-02（对照实际代码核对）

## 实现现状（体检）

| 模块                                   | 状态      | 说明                                                                  |
| -------------------------------------- | --------- | --------------------------------------------------------------------- |
| 资料 GET / PUT                         | ✅        | PUT 有 IDOR 属主校验（`X-User-Id` == `:id`，否则 403）                |
| 关注 / 取关 / 列表                     | ✅        | 关注/取关异步维护 `user_stats`                                        |
| 用户搜索（全文）                       | ✅        | 真实 Postgres FTS（`to_tsvector('simple', display_name)` + GIN 索引） |
| 统计缓存（Redis）                      | ✅        | `user:{id}:stats` HASH，TTL 300s                                      |
| 接收 auth 注册同步                     | ✅        | `POST /internal/users` 幂等建号                                       |
| 用户名自动补全 `/suggest`              | ❌ 未实现 | 旧文档列了，代码无路由/handler                                        |
| `/users/:id/posts`、`/users/:id/likes` | ❌ 未实现 | 旧文档列了，代码无                                                    |
| 关注关系 Redis 缓存                    | ❌ 未实现 | 旧文档大段描述，代码里 `FollowRepository` 完全不碰 Redis              |

## 技术栈

- Go 1.24.0（`go.mod`；Dockerfile 用 `golang:1.25-alpine` 构建）
- Fiber `v2` v2.52.12
- `lib/pq` v1.10.9（`database/sql`）
- `redis/go-redis/v9` v9.7.0
- OpenTelemetry Go SDK v1.40.0 + `otelfiber/v2`
- PostgreSQL（`user_db`，实例端口 5433）；Redis（缓存统计）

## 数据库 (user_db)

Schema 由 `db/schema.sql` 手动管理（`pg_dump` 产物，无迁移工具）。**关键点：外键全部指向本库内的 `public.users`，不是 `auth_db.users`**（Postgres 也不支持跨库 FK）。`users` 表由 auth-service 注册后通过 `/internal/users` 复制进来。

### users 表（由 auth 同步写入）

```sql
CREATE TABLE public.users (
    id uuid NOT NULL,
    username character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    avatar_url character varying(500),
    status character varying(50) DEFAULT 'ACTIVE' NOT NULL,
    two_fa_enabled boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    -- PK(id), UNIQUE(email), UNIQUE(username)
);
```

### user_profiles 表

```sql
CREATE TABLE public.user_profiles (
    id uuid NOT NULL,                            -- 应用层 uuid.New() 生成
    user_id uuid NOT NULL,                       -- UNIQUE, FK public.users(id) ON DELETE CASCADE
    display_name character varying(255) NOT NULL,
    avatar_url character varying(500),
    bio text,
    website character varying(500),
    location character varying(255),
    company character varying(255),
    birthday timestamp without time zone,        -- 是 TIMESTAMP，不是 DATE
    view_count integer DEFAULT 0 NOT NULL,       -- 是 INTEGER，不是 BIGINT
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);
-- GIN(to_tsvector('simple', display_name))；btree(user_id)；btree(view_count DESC)
```

### follows 表（复合主键，无独立 id）

```sql
CREATE TABLE public.follows (
    follower_id uuid NOT NULL,
    following_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    -- PRIMARY KEY (follower_id, following_id)
    -- FK follower_id / following_id -> public.users(id) ON DELETE CASCADE
);
-- 自我关注检查在应用层（service 里 followerID==followingID 直接返回），不是 DB CHECK
-- btree(follower_id)、btree(following_id)、btree(created_at DESC)
```

### user_stats 表

```sql
CREATE TABLE public.user_stats (
    user_id uuid PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    post_count integer DEFAULT 0 NOT NULL,       -- 本服务不维护，由 blog 侧产生
    follower_count integer DEFAULT 0 NOT NULL,
    following_count integer DEFAULT 0 NOT NULL,
    like_count integer DEFAULT 0 NOT NULL,       -- 本服务不维护
    updated_at timestamp without time zone NOT NULL
);
```

## API

实际路由在 `cmd/server/main.go` 注册（前缀 `/api/v1`）。注意：`handler/user_handler.go` 里还有一个 `RegisterRoutes` 函数，但**从未被调用，是死代码**。

```
GET    /health                                - 健康检查
GET    /api/v1/users/:id                      - 获取用户资料
PUT    /api/v1/users/:id                      - 更新资料（IDOR 校验，403）
GET    /api/v1/users/:id/stats                - 统计
GET    /api/v1/users/:id/followers            - 粉丝列表
GET    /api/v1/users/:id/following            - 关注列表
GET    /api/v1/users/search?q=                - 全文搜索用户
POST   /api/v1/users/:id/follow               - 关注（需认证）
DELETE /api/v1/users/:id/follow               - 取关（需认证）
GET    /api/v1/me/following                   - 当前用户关注的所有 ID（需认证）
POST   /internal/users                        - auth-service 注册回调，幂等建号
```

> 旧文档的 `/users/suggest`、`/users/:id/posts`、`/users/:id/likes` **均不存在**。

需认证的路由走 `middleware.AuthMiddleware()`，从 `X-User-Id` 头取身份（由网关注入）。`FollowUser`/`UnfollowUser`/`GetMyFollowing` 的 actor 取自 `c.Locals("userID")`，不从 URL 取，因此不会发生越权。

## Redis 缓存设计

**只有统计缓存是真的**，注入在 `StatsRepository`：

```
Key:   user:{userId}:stats
Type:  HASH
Fields: post_count, follower_count, following_count, like_count   # snake_case
TTL:   300s
```

每次 follow/unfollow 后异步 `IncrementFollowerCount`/`IncrementFollowingCount`（`INSERT ... ON CONFLICT DO UPDATE`，自减用 `GREATEST(x-1,0)` 兜底），并 `DEL` 失效缓存。

> 旧文档里写的「关注列表 SET / 粉丝列表 SET / 关注状态 STRING」**代码里完全没有**——`FollowRepository` 不使用 Redis，关注状态总是现查 Postgres。

## 环境变量 / 配置

```bash
PORT=8002
DATABASE_URL=postgres://postgres:postgres@localhost:5433/user_db?sslmode=disable
REDIS_URL=localhost:6379                       # 注意：go-redis 的 Addr 用 host:port，不是 redis:// URL
REDIS_PASSWORD=redis
AUTH_SERVICE_URL=http://localhost:8001          # 已解析但代码从未使用（无 auth 客户端）
```

> `REDIS_URL` 在容器里被设成 `redis://redis-cache:6379` 直接喂给 `Addr`，与 go-redis 的解析期望不符，是个潜在隐患（本地默认路径 `localhost:6379` 是对的）。

## 可观测性 (OTel)

原生 Go SDK，`internal/observability/otel.go`：

- `service.name=user-service`，OTLP gRPC 上报（`OTEL_EXPORTER_OTLP_ENDPOINT`，默认 `localhost:4317`）
- traces + metrics（15s 周期），`otelfiber.Middleware()` 自动埋点
- propagator：`TraceContext + Baggage`
- **日志暂留控制台**，未导出

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
