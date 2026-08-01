# REST API 规范

> 最近更新：2026-08-02（对照实际配置核对）

## 网关路由

所有外部请求经 **gateway**（Spring Cloud Gateway，端口 `8080`）转发。路由全部声明在
`services/gateway/src/main/resources/application.yml` 的
`spring.cloud.gateway.server.webflux.routes` 下（Spring Cloud 2025 Gateway Server webflux 变体，
**纯 YAML**，无 Java `RouteLocator`）。所有路由 `StripPrefix=0`（路径原样透传）。

| Path 前缀                                                    | 转发到                       | 鉴权                  | route id               |
| ------------------------------------------------------------ | ---------------------------- | --------------------- | ---------------------- |
| `/api/v1/auth/**`                                            | auth-service :8001           | 公开                  | auth-service           |
| `/api/v1/users/**`、`/api/v1/me/**`                          | user-service :8002           | AuthenticationFilter  | user-service           |
| `/api/v1/posts/**`、`/api/v1/comments/**`、`/api/v1/tags/**` | blog-service :8003           | AuthenticationFilter  | blog-service           |
| `/api/v1/content/**`                                         | content-service :8004        | AuthenticationFilter  | content-service        |
| `/api/v1/search/**`                                          | search-service :8005         | AuthenticationFilter  | search-service         |
| `/api/v1/recommend/**`                                       | recommendation-service :8006 | AuthenticationFilter  | recommendation-service |
| `/api/v1/analytics/**`                                       | analytics-service :8010      | 公开                  | analytics-service      |
| `/api/v1/experiments/**`、`/api/v1/layers/**`                | experiment-service :8009     | AuthenticationFilter  | experiment-service     |
| `/api/v1/ads/t/**`                                           | ad-service :8007             | 公开（广告追踪/上报） | ad-tracking            |
| `/health/**`                                                 | auth-service :8001           | 公开                  | health                 |

> gateway 的 management/actuator 端点单独跑在 `:8081`（避开路由管道），暴露
> `health/info/metrics/gateway/prometheus`。

### 鉴权过滤器（`AuthenticationFilter`）

- GET 请求 + 白名单（`/api/v1/auth`、`/health`）：直接放行；带 token 则校验，不带则以匿名身份通过（不注入用户头）。
- POST/PUT/DELETE：必须带有效 `Authorization: Bearer <token>`；gateway 调 auth-service
  `/api/v1/auth/validate` 校验，通过后注入 `X-User-Id` / `X-User-Roles` / `X-User-Email` 给下游。
- 全局默认过滤器会**剥离客户端自带的** `X-User-Id` 等 header，确保下游只信任 gateway 注入的身份。
- CORS：允许来源 `http://localhost:3000` 与 `https://wenxinblog.com`，方法 GET/POST/PUT/DELETE/OPTIONS，凭据 `allow-credentials: true`。
- 其他横切：`RateLimitFilter`（默认 60 rps、burst 10）、`AccessLogFilter`；blog/recommendation 配了 resilience4j 熔断（recommendation 失败 `forward:/api/v1/recommend/fallback`）。

> **已知小坑**：gateway 同时有一条 `/health/**` 路由（转发到 auth-service:8001）和本地
> `HealthController`（聚合 7 服务健康）。路由会优先拦截 `/health/**`，导致本地聚合端点被遮蔽；
> 聚合健康检查请走 actuator `:8081/actuator/health`。

## 基础规范

### 请求格式

- Base URL: `http://localhost:8080/api/v1`（经 gateway）
- Content-Type: `application/json`
- 认证: `Authorization: Bearer <token>`（写接口必填，见上）

### 响应格式

成功响应:

```json
{
  "success": true,
  "data": {}
}
```

错误响应:

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "错误描述",
    "details": {}
  }
}
```

### HTTP状态码

| 状态码 | 说明         |
| ------ | ------------ |
| 200    | 成功         |
| 201    | 创建成功     |
| 400    | 请求参数错误 |
| 401    | 未认证       |
| 403    | 无权限       |
| 404    | 资源不存在   |
| 429    | 请求过于频繁 |
| 500    | 服务器错误   |

## 认证相关

### 注册

```
POST /auth/register
Content-Type: application/json

{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

### 登录

```
POST /auth/login
Content-Type: application/json

{
  "email": "string",
  "password": "string"
}

Response:
{
  "token": "jwt-token",
  "refreshToken": "refresh-token",
  "user": { }
}
```

### OAuth授权

```
GET /auth/oauth/{provider}?redirect_uri=xxx&state=xxx
```

## 博文相关

### 创建博文

```
POST /posts
Authorization: Bearer <token>

{
  "title": "string",
  "content": "markdown",
  "summary": "string",
  "coverImage": "url",
  "tags": ["tag1", "tag2"],
  "status": "DRAFT" | "PUBLISHED"
}
```

### 获取博文列表

```
GET /posts?page=1&pageSize=20&tag=xxx&sort=latest | popular
```

### 获取博文详情

```
GET /posts/:id
```

## 用户相关

### 获取用户信息

```
GET /users/:id
```

### 更新用户信息

```
PUT /users/:id
Authorization: Bearer <token>

{
  "displayName": "string",
  "bio": "string",
  "website": "string"
}
```

### 关注/取关

```
POST /users/:id/follow
DELETE /users/:id/follow
```

## 分页规范

```
GET /resource?page=1&pageSize=20

Response:
{
  "data": [ ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "totalPages": 5
  }
}
```
