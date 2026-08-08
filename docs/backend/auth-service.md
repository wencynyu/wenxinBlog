# Auth Service

认证服务 - 负责用户注册/登录、JWT 签发与校验、注册后向 user-service 同步建号。

> 最近更新：2026-08-02（对照实际代码核对）

> ⚠️ **本文档部分内容已过时**（2026-08-06 复核）：此后 auth-service 新增了 **RBAC**（`roles`/`permissions`/`role_permissions`/`user_roles` 表 + `/api/v1/admin/**`、`/api/v1/account/**` 管理端，均落在 auth_db）、**Google OAuth + 手机号短信登录**（详见 `services/auth-service/docs/social-login-setup.md`）、**Redis 接入**（OAuth state/中间码 + 短信验证码/限流），`/api/v1/auth/validate` 现回传 `{userId, roles, permissions}`（email 仍占位为空）。因此下表中 **OAuth「❌ 未实现」、Redis「❌ 未接入」、「实际只有一张表」「不使用 Redis」等描述均已失效**——请以代码（`cmd/server/main.go`、`db/migrations/000001~000004`、`internal/handler/*`）为准。**仍准确**：2FA 未实现、logout 无状态、GitHub 仅有 config 占位不实例化。

## 实现现状（体检）

| 模块                                | 状态        | 说明                                                                                  |
| ----------------------------------- | ----------- | ------------------------------------------------------------------------------------- |
| 注册 / 登录 / refresh / logout      | ✅ 路由齐全 | `POST /api/v1/auth/register\|login\|refresh\|logout` + `GET /validate`                |
| JWT（access / refresh 双 token）    | ✅ 完整     | access 15min、refresh 7d，HS256，`tokenType` claim 区分                               |
| 注册后同步建 user                   | ✅ 接入     | `POST /internal/users` 调 user-service，失败仅记日志、不阻断注册                      |
| OAuth2.0（Google/GitHub/微信）      | ❌ 未实现   | 仅有 config + 一个未使用的 `OAuthAccount` 模型结构体，无 handler/路由/provider 客户端 |
| 2FA                                 | ❌ 未实现   | `users` 表有 `two_fa_*` 列，但无 enable/verify handler                                |
| 会话管理（Redis）                   | ❌ 未接入   | config 里有 `redis.url`，但代码从未创建 Redis 客户端；`logout` 是无状态空操作         |
| `GetCurrentUser` / `AuthMiddleware` | ⚠️ 孤儿     | 代码已写但从未在 `main.go` 注册路由 / 挂载中间件，调用会 panic                        |
| `ValidateToken` 的 email 字段       | ⚠️ 占位     | 固定返回 `"email": ""`，只透传 `userId` + `roles`                                     |

## 技术栈

- Go 1.25.0（`go.mod`；注意 README 里写的 1.23 已过时）
- Fiber `v2` v2.52.12
- `golang-jwt/jwt/v5` v5.2.0
- `lib/pq` v1.10.9（`database/sql`，无 ORM）
- OpenTelemetry Go SDK v1.40.0 + `otelfiber/v2`（OTLP gRPC 上报）
- PostgreSQL（`auth_db`）；**不使用 Redis、不使用 Kafka**

## 数据库 (auth_db)

Schema 由 `db/schema.sql` 手动管理（无迁移工具）。**实际只有一张表：**

### users 表

```sql
CREATE TABLE public.users (
    id character varying(36) NOT NULL,                 -- 应用层 uuid.New() 生成，无 DB 默认值
    username character varying(50) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    avatar_url character varying(500) DEFAULT '',
    status character varying(20) DEFAULT 'ACTIVE' NOT NULL,
    two_fa_enabled boolean DEFAULT false NOT NULL,     -- 列存在，但无业务逻辑读写
    two_fa_secret character varying(255) DEFAULT '',
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_username_key UNIQUE (username)
);
```

> 设计文档里提到的 `oauth_accounts`、`sessions` 两张表**在 SQL 里不存在**，只作为未使用的 Go 结构体留在 `internal/model/user.go`（无对应 repository，是死代码）。

## API

```
GET    /health                       - 健康检查
POST   /api/v1/auth/register         - 用户注册（同步建 user-service 账号）
POST   /api/v1/auth/login            - 密码登录，返回 access+refresh
POST   /api/v1/auth/refresh          - 用 refresh token 换新 access（校验 tokenType==refresh）
POST   /api/v1/auth/logout           - 登出（当前为无状态空操作，未吊销 token）
GET    /api/v1/auth/validate         - 网关鉴权回调，校验 Bearer token 并回传 userId/roles
```

> 旧文档列出的 `/auth/oauth/:provider`、`/auth/callback/:provider`、`/auth/2fa/enable`、`/auth/2fa/verify` **均不存在**（OAuth/2FA 未实现）。

## JWT

`internal/service/jwt_service.go` 的 `Claims` 带 `tokenType` 字段：

- **access token**：`tokenType=access`，有效期 **15 分钟**，`ExpiresIn=900`
- **refresh token**：`tokenType=refresh`，有效期 **7 天**
- 算法 HS256，`roles` 登录时硬编码为 `["USER"]`
- refresh 流程会拒绝 `tokenType != "refresh"` 的 token

> `config.yaml` 里的 `jwt.expiry: 86400` **实际上未被读取**，有效期在代码里硬编码。

## 注册后同步建号

`auth_service.Register` 先写本地 `users`，再调 `HTTPUserSyncClient.CreateUser`：

- `POST {userService.url}/internal/users`，body `{"id","username","email"}`
- 5s 超时，期望 HTTP 200
- **失败只记日志，不阻断注册**（user-service 没建号时该用户在 user-service 侧缺失）

## 可观测性 (OTel)

原生 Go SDK（不走 Java Agent），`internal/observability/otel.go` + `main.go`：

- `service.name=auth-service`，OTLP **gRPC** 上报到 `OTEL_EXPORTER_OTLP_ENDPOINT`（默认 `localhost:4317`）
- traces：`TracerProvider` batch 上报
- metrics：`MeterProvider` + `PeriodicReader`（15s）
- `otelfiber.Middleware()` 自动为每条请求建 span + HTTP 指标
- propagator：`TraceContext + Baggage`（W3C traceparent）
- **日志暂留控制台**（`log.Printf`），未导出

## 环境变量 / 配置

`config.yaml` 提交默认值，env 可覆盖（`internal/config/config.go`）：

```bash
PORT=8001
DATABASE_URL=postgres://postgres:postgres@localhost:5432/auth_db?sslmode=disable
REDIS_URL=redis://localhost:6379          # 已解析但代码未使用
JWT_SECRET=                               # 必填，空则启动 fail-fast
JWT_EXPIRY=86400                          # 已解析但 JWT 服务未读取（见上）
USER_SERVICE_URL=http://localhost:8002

# OAuth（config + env 已就绪，但无实现代码）
GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET
WECHAT_APP_ID / WECHAT_APP_SECRET
```

## 运行

```bash
cd services/auth-service
go run cmd/server/main.go
```
