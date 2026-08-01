# 微服务架构设计

> 最近更新：2026-08-02（对照实际架构核对）

## 服务划分

### 划分原则

1. **业务边界清晰** - 每个服务对应一个业务能力
2. **数据独立** - 每个服务独占数据库
3. **低耦合** - 服务间通过API通信
4. **高内聚** - 相关功能聚合在同一服务

### 服务清单

```
┌────────────────────────────────────────────────────────────┐
│                      微服务列表                             │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  认证域 (Auth Domain)                                      │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ auth-service - 认证服务 (:8001, Go/Fiber)             │ │
│  │ - 用户注册/登录（注册成功后同步建 user 到 user-service）│ │
│  │ - OAuth2/SSO                                         │ │
│  │ - JWT 签发与验证（access / refresh 双 token）          │ │
│  │ - Token 校验端点供网关调用                             │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  用户域 (User Domain)                                      │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ user-service - 用户服务 (:8002, Go/Fiber)             │ │
│  │ - 用户资料管理                                        │ │
│  │ - 关注关系                                            │ │
│  │ - 用户主页                                            │ │
│  │ - 用户搜索                                            │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  内容域 (Content Domain)                                   │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ blog-service - 博文服务 (:8003)                       │ │
│  │ - 博文CRUD（含作者属主校验）                          │ │
│  │ - 标签管理                                            │ │
│  │ - 评论系统（含评论作者属主校验）                      │ │
│  │ - 点赞/收藏、博文事件发布到 Kafka                     │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ content-service - 媒体服务 (:8004)                    │ │
│  │ - 文件上传（含属主校验）                              │ │
│  │ - 图片处理                                            │ │
│  │ - 视频转码                                            │ │
│  │ - MinIO 对象存储                                      │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  发现域 (Discovery Domain)                                 │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ search-service - 搜索服务 (:8005)                     │ │
│  │ - 全文搜索（Elasticsearch 9.3.8，reactive 客户端）    │ │
│  │ - 搜索建议 / 搜索历史                                 │ │
│  │ - 消费 Kafka 博文事件维护索引                         │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ recommendation-service - 推荐服务 (:8006)             │ │
│  │ - 首页推荐 / 相关推荐                                 │ │
│  │ - Milvus 向量检索                                     │ │
│  │ - 网关为 GET 注入 X-User-Id 供个性化                   │ │
│  │ - 依赖 embedding-service 生成向量                     │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  向量化 (Embedding)                                        │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ embedding-service - 文本向量化 (:8008, Python/FastAPI)│ │
│  │ - 在独立仓库 /AIProjects/embedding-service（非本仓库）│ │
│  │ - 被 recommendation-service 调用来生成博文/用户向量    │ │
│  │ - 已知问题：出站 URL 校验（SSRF）待修                 │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  商业域 (Business Domain)                                  │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ ad-service - 广告服务 (:8007)                        │ │
│  │ - 广告位/计划管理（含属主校验）                       │ │
│  │ - 广告投放                                            │ │
│  │ - 计费统计                                            │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  分析与实验域 (Analytics & Experiment Domain)              │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ analytics-service - 行为分析服务 (:8010)             │ │
│  │ - 用户行为 BI 查询（ClickHouse OLAP）                 │ │
│  └──────────────────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ experiment-service - A/B 实验服务 (:8009)            │ │
│  │ - layer 分层正交 + 同层单 RUNNING 互斥分流            │ │
│  │ - 实验分配 / 生命周期管理                              │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  基础设施域 (Infrastructure Domain)                        │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ gateway - API网关 (:8080，管理端点 8081)              │ │
│  │ - 路由转发（直连各服务，未启用注册中心）              │ │
│  │ - 鉴权（调 auth-service 验 token）+ 身份头防伪注入     │ │
│  │ - 限流 + Resilience4j 熔断降级                        │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## 服务通信

### 同步通信 (HTTP/REST)

```
适用场景:
- 需要立即返回结果
- 简单的CRUD操作
- 服务间调用链不深

示例:
Web -> Gateway -> Auth Service (登录)
              -> User Service (获取用户信息)
```

### 异步通信 (消息队列)

```
适用场景:
- 不需要立即返回
- 耗时操作 (图片处理、视频转码)
- 事件驱动 (用户行为追踪)
- 解耦服务

项目统一使用 Kafka 作为消息骨干（未引入 RabbitMQ）:
- 博文发布 -> blog-service 发布事件 -> search-service 消费 -> 更新 Elasticsearch 索引
- 用户行为 -> 各服务发布 -> analytics-service / recommendation-service 消费
- 注册成功 -> auth-service 同步建用户到 user-service (HTTP 同步，非消息)
```

## 服务发现

### 客户端发现

```
客户端查询服务注册表获取可用实例
- 优点: 去中心化
- 缺点: 客户端复杂

实现:
- Eureka Client
- Consul Client
- K8s Service + CoreDNS
```

### 服务端发现 (推荐)

```
客户端通过负载均衡器访问
负载均衡器查询服务注册表
- 优点: 客户端简单
- 缺点: 多一跳

实现:
- K8s Service (ClusterIP)
- Nginx + Consul Template
- ALB/SLB
```

## API网关

### 网关职责

```
┌─────────────────────────────────────────────────────────┐
│                      API Gateway                        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. 路由转发                                             │
│     - 根据URL路径转发到不同服务                          │
│     - /api/v1/auth/* -> auth-service                    │
│     - /api/v1/posts/* -> blog-service                   │
│                                                          │
│  2. 鉴权认证                                             │
│     - JWT验证                                            │
│     - 权限检查                                           │
│     - 用户信息透传                                       │
│                                                          │
│  3. 限流保护                                             │
│     - 用户级限流                                         │
│     - IP级限流                                           │
│     - API级限流                                          │
│                                                          │
│  4. 熔断降级                                             │
│     - 服务异常时返回降级数据                             │
│     - 防止雪崩                                           │
│                                                          │
│  5. 日志审计                                             │
│     - 记录所有请求                                       │
│     - 关联追踪ID                                         │
│                                                          │
│  6. 跨域处理                                             │
│     - CORS配置                                           │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### 网关实现

```yaml
# Spring Cloud 2025 Gateway 配置（路由前缀为 server.webflux.routes；
# MVP 本地联调直连各服务 http://localhost:<port>，未启用服务注册中心）
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            # 认证服务 (公开，无需认证)
            - id: auth-service
              uri: http://localhost:8001
              predicates:
                - Path=/api/v1/auth/**

            # 博文服务 (需认证：挂 AuthenticationFilter)
            - id: blog-service
              uri: http://localhost:8003
              predicates:
                - Path=/api/v1/posts/**,/api/v1/comments/**,/api/v1/tags/**
              filters:
                - name: AuthenticationFilter

          # 全局剥离客户端自报的 X-User-* 头，下游只信任网关注入值
          default-filters:
            - RemoveRequestHeader=X-User-Id, X-User-Roles, X-User-Email
```

## 数据一致性

### 分布式事务策略

```
1. 最终一致性 (推荐)
   - Saga模式
   - 事件驱动
   - 补偿机制

   示例: 发布博文 + 更新搜索索引
   blog-service 发布博文
   -> 发送 Kafka 事件
   -> search-service 消费事件
   -> 更新 Elasticsearch 索引

2. 强一致性
   - 2PC/XA (性能差)
   - TCC (Try-Confirm-Cancel)
   - 不推荐使用
```

### Saga模式

```python
# 发布博文的Saga流程
1. BlogService: 创建博文 (PENDING)
2. ContentService: 处理图片
3. SearchService: 创建搜索索引
4. NotificationService: 通知关注者

# 如果步骤3失败
1. SearchService: 回滚 (删除索引)
2. ContentService: 回滚 (删除图片)
3. BlogService: 回滚 (删除博文或标记失败)
```

## 服务拆分策略

### 数据库拆分

```
原则:
- 一个服务一个数据库
- 跨库查询通过API
- 只读场景可使用FDW

拆分方式:
1. 垂直拆分 (按业务域)
   auth_db, user_db, blog_db

2. 水平拆分 (按数据量)
   posts_2024, posts_2025 (分表)
   posts_shard_0, posts_shard_1 (分片)
```

### 服务接口设计

```
RESTful API设计规范:
- 使用名词复数: /api/v1/posts
- 使用HTTP动词: GET, POST, PUT, DELETE
- 版本控制: /api/v1/
- 统一响应格式
  {
    "success": true,
    "data": { },
    "error": { }
  }
```

## 性能优化

### 服务优化

```
1. 连接池优化
   - 数据库连接池
   - Redis连接池
   - HTTP客户端连接池

2. 异步处理
   - WebFlux (Java)
   - Goroutine (Go)
   - 消息队列异步

3. 缓存策略
   - 本地缓存 (Caffeine)
   - 分布式缓存 (Redis)
   - 缓存预热
   - 缓存更新
```

### 查询优化

```
1. 数据库索引
   - 主键索引
   - 唯一索引
   - 复合索引
   - GIN索引 (全文搜索)

2. 分页查询
   - 使用LIMIT/OFFSET
   - 使用游标分页 (大数据量)

3. 慢查询优化
   - EXPLAIN ANALYZE
   - 避免N+1查询
   - 使用JOIN或聚合
```

## 服务监控

### 健康检查

```
每个服务暴露/health端点:
- 检查数据库连接
- 检查Redis连接
- 检查依赖服务
- 返回服务状态

K8s使用健康检查:
- livenessProbe: 存活探针
- readinessProbe: 就绪探针
- startupProbe: 启动探针
```

### 链路追踪

```
Trace ID 贯穿整个调用链（基于 OpenTelemetry 统一管道）:
Gateway (trace-123)
  -> Auth Service (trace-123, span-1)
    -> Database (trace-123, span-2)
  -> Blog Service (trace-123, span-3)
    -> Search Service (trace-123, span-4)

实现:
- Java 服务: OTel Java Agent 自动埋点
- Go 服务: OTel SDK 手动埋点
- 数据经由 OTel Collector → Elasticsearch (traces-apm.default data stream)
- 指标经 Collector → Prometheus，Grafana 统一查看 traces / logs / overview / api 看板
```

## 服务治理

### 版本管理

```
多版本并存:
blog-service-v1 (稳定版)
blog-service-v2 (灰度版)

流量分配:
- 90% -> v1
- 10% -> v2

实现:
- K8s Service (权重配置)
- Gateway路由规则
- Istio VirtualService
```

### 灰度发布

```
1. 金丝雀发布
   - 新版本先发布少量实例
   - 观察指标
   - 逐步扩大流量

2. 蓝绿部署
   - 新版本全量部署
   - 切换流量
   - 出问题快速回滚

3. 滚动发布
   - 逐个替换实例
   - 保持服务可用
```

## 故障处理

### 熔断机制

```
当服务异常率达到阈值:
1. 打开熔断器
2. 快速失败返回降级数据
3. 一段时间后进入半开状态
4. 尝试调用，成功则关闭熔断器

实现:
- Resilience4j
- Hystrix (已停止维护)
- Sentinel
```

### 限流降级

```
限流策略:
- 令牌桶算法
- 漏桶算法
- 滑动窗口

降级策略:
- 返回缓存数据
- 返回默认数据
- 返回友好错误提示
```

## 微服务挑战

### 分布式事务

```
问题: 跨服务事务一致性
方案:
- Saga模式 (推荐)
- 事件溯源
- CQRS
```

### 服务调试

```
问题: 调用链复杂，难以定位
方案:
- 链路追踪
- 集中式日志
- 分布式Tracing
```

### 数据聚合

```
问题: 需要聚合多个服务的数据
方案:
- API Gateway聚合
- BFF层 (Backend For Frontend)
- GraphQL
```
