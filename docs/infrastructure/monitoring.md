# 监控告警

## 监控体系

```
┌────────────────────────────────────────────────────────────┐
│                       监控层次                              │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐  │
│  │   应用监控    │   │   中间件监控  │   │   基础设施    │  │
│  │              │   │              │   │              │  │
│  │ - JVM/Go     │   │ - Redis      │   │ - PostgreSQL │  │
│  │ - 接口性能    │   │ - Kafka      │   │ - CPU/Mem   │  │
│  │ - 业务指标    │   │ - RabbitMQ   │   │ - 网络      │  │
│  └──────────────┘   └──────────────┘   └──────────────┘  │
│         │                   │                   │          │
│         └───────────────────┴───────────────────┘          │
│                             │                              │
│                   ┌─────────▼─────────┐                   │
│                   │   Prometheus     │                   │
│                   │   (时序数据库)     │                   │
│                   └─────────┬─────────┘                   │
│                             │                              │
│                   ┌─────────▼─────────┐                   │
│                   │   Grafana        │                   │
│                   │   (可视化)         │                   │
│                   └───────────────────┘                   │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## Prometheus指标

### 应用指标

#### JVM指标 (Java服务)
```yaml
# Micrometer配置
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.95,0.99
```

#### 关键指标
```promql
# 请求QPS
rate(http_server_requests_seconds_count{job="blog-service"}[5m])

# 响应时间P95
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="blog-service"}[5m]))

# 错误率
rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])
```

#### Go指标
```go
// 添加Prometheus指标
import (
    "github.com/prometheus/client_golang/prometheus"
    "github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
    httpDuration = prometheus.NewHistogramVec(
        prometheus.HistogramOpts{
            Name: "http_request_duration_seconds",
            Help: "HTTP request latency",
        },
        []string{"method", "path", "status"},
    )
)

func init() {
    prometheus.MustRegister(httpDuration)
}

// 在handler中记录
httpDuration.WithLabelValues("GET", "/api/v1/posts", "200").Observe(duration)
```

### 业务指标

#### 自定义指标
```java
// Micrometer自定义指标
@Service
public class PostMetrics {

    private final Counter postCreateCounter;
    private final Counter postViewCounter;

    public PostMetrics(MeterRegistry registry) {
        postCreateCounter = Counter.builder("post.create.total")
            .description("Total posts created")
            .register(registry);

        postViewCounter = Counter.builder("post.view.total")
            .description("Total post views")
            .tag("type", "blog")
            .register(registry);
    }

    public void recordPostCreate() {
        postCreateCounter.increment();
    }
}
```

#### 关键业务指标
```promql
# 博文创建速率
rate(post_create_total[5m])

# 浏览量
rate(post_view_total[5m])

# 活跃用户数
count(user_session_active) by (service)

# 广告展示
rate(ad_impression_total[5m])

# 广告点击率
rate(ad_click_total[5m]) / rate(ad_impression_total[5m])
```

## Grafana仪表板

### 服务概览仪表板

```yaml
Dashboard: WenxinBlog Service Overview

Panels:
  - Row: 服务状态
    Panels:
      - 服务健康状态 (Stat)
      - 当前QPS (Stat)
      - 错误率 (Gauge)
      - P95延迟 (Gauge)

  - Row: 请求指标
    Panels:
      - QPS趋势 (Graph)
      - 响应时间分布 (Heatmap)
      - 状态码分布 (Pie Chart)

  - Row: JVM指标
    Panels:
      - 堆内存使用 (Graph)
      - GC次数 (Graph)
      - 线程数 (Graph)

  - Row: 业务指标
    Panels:
      - 博文创建趋势 (Graph)
      - 活跃用户数 (Stat)
      - 广告填充率 (Gauge)
```

### 数据库仪表板

```yaml
Dashboard: PostgreSQL Overview

Panels:
  - Row: 连接信息
    Panels:
      - 当前连接数
      - 空闲连接数
      - 最大连接数使用率

  - Row: 查询性能
    Panels:
      - QPS
      - 慢查询数量
      - 查询时长P95

  - Row: 存储信息
    Panels:
      - 数据库大小
      - 各表大小排行
      - 索引使用率

  - Row: 复制/备份
    Panels:
      - 复制延迟
      - WAL大小
      - 备份状态
```

## 告警规则

### Prometheus告警规则

```yaml
# alert_rules.yml
groups:
  - name: service_alerts
    interval: 30s
    rules:
      # 服务宕机
      - alert: ServiceDown
        expr: up{job=~"wenxinblog-.*"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.job }} 宕机"
          description: "{{ $labels.instance }} 已宕机超过1分钟"

      # 错误率过高
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          / rate(http_server_requests_seconds_count[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "服务 {{ $labels.job }} 错误率过高"
          description: "错误率: {{ $value | humanizePercentage }}"

      # 响应时间过长
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95,
            rate(http_server_requests_seconds_bucket{job="wenxinblog-.*"}[5m])
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "服务 {{ $labels.job }} P95延迟过高"
          description: "P95延迟: {{ $value }}s"

      # CPU使用率
      - alert: HighCPUUsage
        expr: rate(process_cpu_seconds_total[5m]) > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "CPU使用率过高"

  - name: database_alerts
    rules:
      # 数据库连接数
      - alert: HighDBConnections
        expr: pg_stat_database_numbackends{datname=~"auth_db|user_db|blog_db"} > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "数据库连接数过高"

      # 慢查询
      - alert: SlowQueries
        expr: rate(pg_stat_statements_calls_total[5m]) > 100
        for: 10m
        labels:
          severity: info
        annotations:
          summary: "检测到慢查询"

  - name: business_alerts
    rules:
      # 广告收入
      - alert: LowAdRevenue
        expr: rate(ad_revenue_total[1h]) < 100
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: "广告收入过低"

      # 用户增长
      - alert: LowUserGrowth
        expr: increase(user_registered_total[1d]) < 10
        for: 1d
        labels:
          severity: info
        annotations:
          summary: "日新增用户过低"
```

### AlertManager配置

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'

  routes:
    # Critical告警立即发送
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true

    # Warning告警聚合发送
    - match:
        severity: warning
      receiver: 'slack-warnings'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://localhost:8001/webhook'

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '<PAGERDUTY_KEY>'

  - name: 'slack-warnings'
    slack_configs:
      - api_url: '<SLACK_WEBHOOK_URL>'
        channel: '#wenxinblog-alerts'
        title: '{{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
```

## 链路追踪

### Spring Cloud Sleuth配置

```yaml
# application.yml
spring:
  sleuth:
    zipkin:
      base-url: http://localhost:9411
    sampler:
      probability: 0.1  # 10%采样

  application:
    name: ${spring.application.name}
```

### 追踪信息展示

```
Trace: abc123xyz
├── Gateway: 50ms
│   └── Auth Service: 20ms
│       └── Database: 10ms
├── Blog Service: 100ms
│   ├── Content Service: 30ms
│   └── Database: 40ms
└── Recommendation Service: 80ms
    └── Milvus: 50ms
```

## 日志聚合

### 阿里云SLS配置

```yaml
# logback-spring.xml
<appender name="SLS" class="com.aliyun.openservices.log.logback.LoghubAppender">
  <endpoint>https://cn-hangzhou.log.aliyuncs.com</endpoint>
  <accessKeyId>${LOG_ACCESS_KEY_ID}</accessKeyId>
  <accessKeySecret>${LOG_ACCESS_KEY_SECRET}</accessKeySecret>
  <project>wenxinblog-logs</project>
  <logStore>wenxinblog-store</logStore>
  <topic>${spring.application.name}</topic>
  <source>${HOSTNAME}</source>
  <encoder>
    <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
  </encoder>
  <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
    <level>INFO</level>
  </filter>
</appender>
```

### 日志格式规范

```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "level": "INFO",
  "service": "blog-service",
  "traceId": "abc123",
  "spanId": "def456",
  "userId": "uuid",
  "message": "Post created",
  "data": {
    "postId": "uuid",
    "title": "Hello World"
  }
}
```

## 性能分析

### pprof (Go)

```go
import (
    _ "net/http/pprof"
    "runtime"
)

// 启用pprof端点
// http://localhost:8001/debug/pprof/
```

### Java Flight Recorder

```bash
# 启动时开启JFR
java -XX:StartFlightRecording=filename=recording.jfr,duration=60s ...

# 分析JFR文件
jfr recording.jfr
```

## 告警通知渠道

| 渠道 | 触发条件 | 用途 |
|------|----------|------|
| 钉钉群 | Warning+ | 开发团队 |
| 钉钉个人 | Critical | on-call人员 |
| 短信 | Critical | 紧急故障 |
| 邮件 | 每日报告 | 运营汇总 |
| PagerDuty | Critical | 值班轮换 |

## 监控最佳实践

1. **分层监控**
   - 基础设施: CPU、内存、磁盘、网络
   - 中间件: Redis、Kafka、PostgreSQL
   - 应用: 接口性能、错误率
   - 业务: 核心指标(DAU、收入)

2. **告警收敛**
   - 同类告警聚合
   - 告警去重
   - 告警静默期
   - 分级通知

3. **可观测性**
   - Metric: 数值指标
   - Log: 事件日志
   - Trace: 调用链
   - 三者关联分析
