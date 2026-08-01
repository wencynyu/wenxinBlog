# Gateway

API 网关 - 负责路由、JWT 鉴权注入、安全头剥离、熔断降级、健康聚合。

> 最近更新：2026-08-02（对照实际代码核对）

## 实现现状（体检）

| 模块                              | 状态              | 说明                                                                               |
| --------------------------------- | ----------------- | ---------------------------------------------------------------------------------- |
| 路由转发（直连各服务）            | ✅                | 10 条路由，`http://localhost:NNNN`（**不走 lb://，无注册中心**）                   |
| 安全头剥离（防伪造身份）          | ✅                | `default-filters` 全局 `RemoveRequestHeader=X-User-Id, X-User-Roles, X-User-Email` |
| AuthenticationFilter              | ✅                | JWT 校验（委托 auth-service `/validate`）后才注入真实身份                          |
| FallbackHandler                   | ✅                | `@Order(-2)`，5xx/超时/熔断/限流 → 503/504，按路径给中文提示                       |
| 健康聚合（7 服务 UP/DEGRADED）    | ✅                | `GET /health` 扇出探活 8001-8007                                                   |
| RateLimitFilter（Redis 滑动窗口） | ⚠️ 已实现但未挂载 | Lua 脚本真实存在，但**无任何路由引用它**，当前休眠                                 |
| Resilience4j 熔断                 | ⚠️ 半成           | 声明了 blog/recommendation 两个实例，但无路由接入 `CircuitBreaker` 算子            |
| 服务发现（Eureka/Nacos/Consul）   | ❌ 未使用         | 无依赖，`discovery.locator.enabled: false`，旧文档整段是虚构的                     |
| AccessLogFilter（Kafka 访问日志） | ⚠️ 已实现但未挂载 | 10% 采样，无路由引用，休眠                                                         |
| `/internal/ads/**` + InternalOnly | ❌ 未配置         | predicate 类存在但无路由用                                                         |

## 技术栈

- Java 25 + Spring Boot 4.0.4
- **Spring Cloud `2025.1.1`**（不是旧文档的 4.1）
- `spring-cloud-starter-gateway-server-webflux`（新的 WebFlux 变体；配置命名空间是 **`spring.cloud.gateway.server.webflux.*`**，不是 `spring.cloud.gateway.*`）
- `spring-cloud-starter-circuitbreaker-reactor-resilience4j` + `resilience4j-micrometer`
- `spring-boot-starter-data-redis-reactive`、`spring-kafka`
- Actuator + `micrometer-registry-prometheus`
- OTel Java Agent 2.30.0

**端口：8080**（主）；**management 端口 8081**（actuator 单独端口，避开网关流水线）。

## 路由配置（真实）

所有 URI 直连 `http://localhost:NNNN`（MVP 本地联调，注释明说"不走注册中心"）：

| id                     | uri  | Path 谓词                                                | 过滤器                                  |
| ---------------------- | ---- | -------------------------------------------------------- | --------------------------------------- |
| auth-service           | 8001 | `/api/v1/auth/**`                                        | StripPrefix=0                           |
| user-service           | 8002 | `/api/v1/users/**, /api/v1/me/**`                        | StripPrefix=0, **AuthenticationFilter** |
| blog-service           | 8003 | `/api/v1/posts/**, /api/v1/comments/**, /api/v1/tags/**` | StripPrefix=0, **AuthenticationFilter** |
| content-service        | 8004 | `/api/v1/content/**`                                     | StripPrefix=0, **AuthenticationFilter** |
| search-service         | 8005 | `/api/v1/search/**`                                      | StripPrefix=0, **AuthenticationFilter** |
| recommendation-service | 8006 | `/api/v1/recommend/**`                                   | StripPrefix=0, **AuthenticationFilter** |
| experiment-service     | 8009 | `/api/v1/experiments/**, /api/v1/layers/**`              | StripPrefix=0, **AuthenticationFilter** |
| analytics-service      | 8010 | `/api/v1/analytics/**`                                   | StripPrefix=0（无 AuthFilter）          |
| ad-tracking            | 8007 | `/api/v1/ads/t/**`                                       | StripPrefix=0                           |
| health                 | 8001 | `/health/**`                                             | StripPrefix=0                           |

> 旧文档的 `lb://...` URI、`RateLimitFilter=20,1` 等路由级限流、`/internal/ads/**` InternalOnly 路由**均不存在**；旧文档还漏了 analytics-service、experiment-service、`/api/v1/me/**`、`/api/v1/layers/**`。

## default-filters（核心安全控制）

```yaml
default-filters:
  # 全局剥离客户端可能伪造的身份头（下游只信任网关注入的 X-User-*）
  - RemoveRequestHeader=X-User-Id, X-User-Roles, X-User-Email
  - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
```

**每个请求在进入任何下游前，客户端自带的 `X-User-Id` / `X-User-Roles` / `X-User-Email` 都被先剥掉**，下游只能拿到网关注入的值。这是防 IDOR / 身份伪造的关键。

## AuthenticationFilter（`AuthenticationFilterGatewayFilterFactory`）

挂在需认证路由上。白名单（前缀匹配）：`/api/v1/auth`、`/health`。

```
1. 先 mutate 请求，移除 X-User-*（与 default-filters 双保险）
2. 白名单路径            → 直接放行（已剥头）
3. GET 且无 token        → 匿名放行（不注入身份）
4. GET 带 token          → 校验；成功注入 X-User-Id/Roles/Email；失败降级为匿名放行
5. POST/PUT/DELETE 无 token → 401 "Missing authorization token"
6. POST/PUT/DELETE 带 token → 校验；失败 401 "Invalid or expired token"；成功注入并转发
7. auth-service 不可达    → 401 "Authentication service unavailable"
```

- **JWT 不在本地校验**：把 Bearer token 转发给 `http://localhost:8001/api/v1/auth/validate`（WebClient），拿回 `{userId, email, roles}`
- roles 以逗号拼接注入 `X-User-Roles`
- 注入的头由 `default-filters` 保证不被客户端伪造

> `jwt.secret` / `jwt.auth-service-url` 在 yml 里配置，但当前 filter 内部 hardcode 了 `localhost:8001`，配置项实际未被读取。

## FallbackHandler

`@Component @Order(-2)`（先于 `@Order(-1)` 的 `GlobalExceptionHandler`），处理：

- `ResponseStatusException` 5xx、`TimeoutException`、`CircuitBreakerOpenException`、`RateLimitExceededException`
- 非兜底类异常 → `Mono.error(ex)` 透传给 GlobalExceptionHandler
- 响应码：503（SERVICE_UNAVAILABLE）/ 504（GATEWAY_TIMEOUT）/ FALLBACK_ACTIVATED，body 带 `error.fallback=true`
- 按路径中文提示：`/api/v1/recommend*` → "推荐服务暂时不可用，已为您显示热门内容"；`/api/v1/search*` → "搜索服务暂时不可用…"；`/api/v1/posts*` → "博文服务暂时不可用…"

## 限流（RateLimitFilter）

真实实现，非虚构：`scripts/rate-limit.lua`（Redis 滑动窗口 ZSET），默认 `limit=60, window=60s`。key 优先级 user > ip > api；Redis 故障 **fail-open**（放行）；超限 429 + `Retry-After: 60`。

> 但**当前没有任何路由挂载 RateLimitFilter**，所以限流实际未生效。旧文档贴的那段 Lua 有 bug（`else:` / `ZADD key now now`），与真实脚本不符。

## 健康聚合（HealthController）

`GET /health` 用 `Mono.zip` 扇出探活，3s 超时：

```
auth-service(8001) user-service(8002) blog-service(8003)
content-service(8004) search-service(8005) recommendation-service(8006) ad-service(8007)
```

任一 DOWN → 整体 `DEGRADED`。另有 `GET /health/gateway`（自身）、`GET /health/service/{name}`（单个）。

> analytics(8010)/experiment(8009) 虽有路由，但**不在健康聚合里**（聚合的是 7 个核心服务）。

## 配置 (application.yml 节选)

```yaml
server:
  port: 8080
management:
  server: { port: 8081 }                       # actuator 单独端口
  endpoints: { web: { exposure: { include: health,info,metrics,gateway,prometheus } } }

spring:
  cloud:
    gateway:
      server:
        webflux:
          discovery: { locator: { enabled: false } }
          default-filters: [ RemoveRequestHeader=X-User-Id, X-User-Roles, X-User-Email, ... ]
          globalcors:
            cors-configurations:
              '[/**]':
                allowed-origins: [http://localhost:3000, https://wenxinblog.com]
                allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
                allowed-headers: "*"
                allow-credentials: true
                max-age: 3600
  redis: { host: localhost, port: 6379, password: ${REDIS_PASSWORD:redis} }
  kafka: { bootstrap-servers: localhost:9092 }
jwt:
  secret: ${JWT_SECRET}                        # 无默认，启动必填
  auth-service-url: http://localhost:8001
rate-limit: { default: 60, burst: 10 }

resilience4j:
  circuitbreaker:
    instances:
      blog-service: { sliding-window-size: 100, failure-rate-threshold: 50, wait-duration-in-open-state: 30s, ... }
      recommendation-service: { sliding-window-size: 50, failure-rate-threshold: 60, ... , fallback-uri: forward:/api/v1/recommend/fallback }
```

> `recommendation-service` 的 `fallback-uri: forward:/api/v1/recommend/fallback` 是**死配置**——没有对应的 controller，FallbackHandler 也不走它。

## 可观测性 / 运行

- OTel Java Agent 2.30.0（Dockerfile `-javaagent`）；`OTEL_SERVICE_NAME=gateway`，OTLP gRPC → collector
- 堆内存：docker-compose 里 `JAVA_TOOL_OPTIONS="-Xmx256m"`；开发环境 `scripts/start-dev.sh` 统一 `-Xmx512m`

```bash
cd services/gateway
mvn spring-boot:run
```
