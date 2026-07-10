# Gateway

API网关 - 负责路由、限流、鉴权、熔断

## 功能

- 统一入口
- 路由转发
- JWT鉴权
- 限流保护
- 熔断降级
- 负载均衡
- 请求/响应日志
- 跨域处理

## 技术栈

- Java 25
- Spring Cloud Gateway 4.1
- Spring Cloud CircuitBreaker
- Redis (限流、缓存)
- Kafka (访问日志)

## 路由配置

### 内部服务路由
```yaml
spring:
  cloud:
    gateway:
      routes:
        # 认证服务
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - StripPrefix=0

        # 用户服务 (需认证)
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - StripPrefix=0
            - AuthenticationFilter

        # 博文服务 (需认证)
        - id: blog-service
          uri: lb://blog-service
          predicates:
            - Path=/api/v1/posts/**,/api/v1/comments/**,/api/v1/tags/**
          filters:
            - StripPrefix=0
            - AuthenticationFilter
            - RateLimitFilter=20,1  # 20 req/min

        # 内容服务 (需认证)
        - id: content-service
          uri: lb://content-service
          predicates:
            - Path=/api/v1/content/**
          filters:
            - StripPrefix=0
            - AuthenticationFilter
            - RateLimitFilter=10,1  # 10 req/min (上传限制)

        # 搜索服务
        - id: search-service
          uri: lb://search-service
          predicates:
            - Path=/api/v1/search/**
          filters:
            - StripPrefix=0
            - RateLimitFilter=60,1

        # 推荐服务
        - id: recommendation-service
          uri: lb://recommendation-service
          predicates:
            - Path=/api/v1/recommend/**
          filters:
            - StripPrefix=0

        # 广告服务 (内部)
        - id: ad-service
          uri: lb://ad-service
          predicates:
            - Path=/internal/ads/**
            - InternalOnly  # 只允许内部调用
          filters:
            - StripPrefix=0

        # 广告追踪 (公开)
        - id: ad-tracking
          uri: lb://ad-service
          predicates:
            - Path=/api/v1/ads/t/**

        # 健康检查 (公开)
        - id: health
          uri: lb://auth-service
          predicates:
            - Path=/health/**
          filters:
            - StripPrefix=0
```

### 服务发现配置
```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
```

## 鉴权流程

```python
# 1. 提取Token
token = extract_token(request)

# 2. 白名单检查
if path in ["/api/v1/auth/login", "/api/v1/auth/register", "/health"]:
    pass_through()

# 3. Token验证
if token:
    user_info = auth_service.verify_token(token)
    if user_info.valid:
        # 添加用户信息到请求头
        request.headers[X-User-Id] = user_info.id
        request.headers[X-User-Roles] = user_info.roles
        pass_to_service()
    else:
        return 401 Unauthorized
else:
    return 401 Unauthorized
```

## 限流策略

### 基于用户
```
Key: rate-limit:user:{userId}
Limit: 60 req/min
Window: Sliding (60s)
```

### 基于IP
```
Key: rate-limit:ip:{ip}
Limit: 100 req/min
Window: Sliding (60s)
```

### 基于API
```
Key: rate-limit:api:{api_path}
Limit: 根据API配置
Window: Sliding
```

### Redis实现
```lua
-- 滑动窗口限流脚本
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 移除窗口外的记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 获取当前计数
local current = redis.call('ZCARD', key)

if current < limit then
    -- 添加当前请求
    redis.call('ZADD', key, now, now)
    redis.call('EXPIRE', key, window)
    return 1  -- 允许
else:
    return 0  -- 拒绝
end
```

## 熔断降级

### Resilience4j配置
```yaml
resilience4j:
  circuitbreaker:
    instances:
      blog-service:
        sliding-window-size: 100
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 10
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
      recommendation-service:
        fallback-uri: forward:/api/v1/recommend/fallback  # 降级处理
```

### 降级策略
```python
# 推荐服务降级
if recommendation_service.is_down():
    return cached_recommendations()
    # 或
    return popular_posts()  # 返回热门内容

# 搜索服务降级
if search_service.is_down():
    return basic_search()  # 数据库LIKE查询
```

## 请求/响应日志

### Kafka日志格式
```json
{
  "traceId": "uuid",
  "requestId": "uuid",
  "timestamp": "2024-01-01T00:00:00Z",
  "method": "GET",
  "path": "/api/v1/posts",
  "query": "page=1",
  "headers": { },
  "userId": "uuid",
  "clientIp": "1.2.3.4",
  "userAgent": "Mozilla/5.0...",
  "service": "blog-service",
  "statusCode": 200,
  "responseTime": 125,
  "responseSize": 5678
}
```

### 日志级别
```
ERROR: 5xx错误
WARN: 4xx错误 (限流、熔断)
INFO: 正常请求 (采样率: 10%)
DEBUG: 详细请求头和响应 (开发环境)
```

## CORS配置

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins:
              - http://localhost:3000
              - https://wenxinblog.com
            allowed-methods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: true
            max-age: 3600
```

## 环境变量

```yaml
server:
  port: 8080

spring:
  application:
    name: gateway
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin

  redis:
    host: localhost
    port: 6379
    password: redis

  kafka:
    bootstrap-servers: localhost:9092

# 服务发现
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

# 或使用Nacos
spring.cloud.nacos.discovery.server-addr: localhost:8848

# JWT配置
jwt:
  secret: ${JWT_SECRET}
  auth-service-url: http://auth-service:8001

# 限流配置
rate-limit:
  default: 60
  burst: 10
```

## 运行

```bash
cd services/gateway
mvn spring-boot:run
```

## 监控指标

```
# Gateway Metrics
gateway_requests_total{service, method, status}
gateway_request_duration_seconds{service}
gateway_rate_limit_rejections_total{user, ip}
gateway_circuit_breaker_state{service}

# Alert Rules
Alert: gateway_error_rate > 0.05 for 5m
Alert: gateway_latency_p95 > 1s for 5m
Alert: gateway_rate_limit_rejections > 100/min for 5m
```
