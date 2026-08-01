# 监控告警

> 最近更新：2026-08-02（对照实际配置核对）

## 可观测性体系（OpenTelemetry 统一管道）

wenxinBlog 的 metrics / traces / logs 三支柱统一走 **OpenTelemetry**：所有服务用 OTLP 协议把遥测数据发给 **OTel Collector**，Collector 再分发到对应后端。不再有「每服务各自埋点 + 多套采集器」的旧模型。

```
┌─────────────────────────────────────────────────────────────────┐
│   应用服务（按语言注入 OTel）                                       │
│                                                                 │
│   Java 服务   → OTel Java Agent（-javaagent，自动 instrumentation │
│                 + Micrometer 桥把 Spring 指标转成 OTLP）           │
│   Go 服务     → OTel SDK（OTel fiber/http 中间件）                 │
│   Python 服务 → opentelemetry-instrument（自动注入）               │
└───────────────────────────┬─────────────────────────────────────┘
                            │ OTLP（gRPC :4317 / HTTP :4318）
                            ▼
              ┌──────────────────────────────┐
              │   OTel Collector 0.157       │
              │   infra/otel-collector/      │
              │   config.yaml                │
              └───┬─────────────┬──────────┬─┘
        traces    │     logs    │  metrics │
                  ▼             ▼          ▼
         ┌──────────────┐ ┌──────────┐ ┌──────────────┐
         │Elasticsearch │ │Elastic-  │ │ Prometheus   │
         │9.3.8         │ │search    │ │ (Collector   │
         │traces-generic│ │logs-     │ │  :8889 暴露) │
       .otel-default    │generic.   │ └──────┬───────┘
         │              │otel-     │        │ scrape
         │              │default   │        ▼
         │              └──────┬───┘   ┌──────────┐
         │                     │       │Prometheus│
         └──────────┬──────────┘       │  :9090   │
                    ▼                  └────┬─────┘
              ┌───────────┐                │
              │  Grafana  │◀───────────────┘
              │  13.1     │  (ES + Prometheus 数据源)
              │  :3001    │
              └───────────┘
```

### Collector 配置要点（`infra/otel-collector/config.yaml`）

- **接收**：OTLP gRPC `:4317`、HTTP `:4318`。
- **处理器**：`memory_limiter`（80% / 25% spike）、`batch`（5s / 512）、`resource`（注入 `deployment.environment=dev`）、`transform`（把日志 `scope.name` 即类名拼进 body，便于 Grafana 日志流直接显示）。
- **导出**：
  - traces → `elasticsearch/traces`
  - logs → `elasticsearch/logs`
  - metrics → `prometheus`（`resource_to_telemetry_conversion.enabled: true`，把 `service.name` 暴露为 Prometheus 标签 `service_name`）。
- ES exporter 走 otel 模式，**自动建 data stream + index template**，无需手动建索引。

## Prometheus 指标

### 采集链路

**已统一**：不再逐服务抓 `/actuator/prometheus` 或 `/metrics`。所有指标经
`OTLP → Collector → Prometheus exporter(:8889)`，Prometheus 只抓一个目标 `otel-collector:8889`
（见 `infra/prometheus/prometheus.yml`）。用 `service_name` 标签区分服务。

### Java 服务（OTel Java Agent + Micrometer 桥）

- Java Agent（`scripts/start-dev.sh` 用 `-javaagent` 注入 `infra/otel/opentelemetry-javaagent.jar`）
  自动 instrumentation：HTTP/DB/JVM 等。
- Micrometer 桥（`OTEL_INSTRUMENTATION_MICROMETER_ENABLED=true`）把 Spring 的 Micrometer meter
  原样转成 OTLP，**指标名不变**：`http_server_requests_seconds_*`、`jvm_threads_live_threads`、
  `process_cpu_usage`、`jvm_memory_*` 等，保留 `method/status/uri/outcome` 等原标签。

### Go 服务（OTel SDK）

- Go 服务（auth/user）用 OTel SDK 的 fiber/http 中间件产生指标，经 OTLP 上报，不再用
  `prometheus/client_golang` + `promhttp`。

### 关键 PromQL（注意用 `service_name` 标签）

```promql
# 请求 QPS（按服务过滤）
rate(http_server_requests_seconds_count{service_name="blog-service"}[5m])

# 响应时间 P95
histogram_quantile(0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{service_name="blog-service"}[5m])))

# 错误率（5xx 占比）
sum(rate(http_server_requests_seconds_count{service_name="blog-service", status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count{service_name="blog-service"}[5m]))
```

> 注意：同时存在两套 HTTP 指标（不报错，仅冗余）——
> ① Micrometer 桥的 `http_server_requests_seconds_*`（面板沿用这套）；
> ② Agent 自带 Netty instrumentation 的 `http_server_request_duration_seconds_*`（OTel 语义约定标签）。
> 详见 `infra/grafana/OTEL_METRICS_MIGRATION.md`。

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

## Grafana 仪表板

### 部署方式

- 镜像 `grafana/grafana:13.1`，端口 `3001:3000`（admin/admin）。
- 数据源与仪表板均走 **provisioning**（启动即注入，`infra/grafana/provisioning/`），无需手动在 UI 配置。
- 实验特性 `elasticsearchESQLQuery` 已开启（traces 链路时序图需要）。

### 数据源（`provisioning/datasources/datasource.yml`）

| 数据源               | 类型          | 指向                                                          | 用途            |
| -------------------- | ------------- | ------------------------------------------------------------- | --------------- |
| Prometheus           | prometheus    | `http://prometheus:9090`                                      | metrics（默认） |
| Elasticsearch-Traces | elasticsearch | `http://elasticsearch:9200`，DB `traces-generic.otel-default` | traces          |
| Elasticsearch-Logs   | elasticsearch | `http://elasticsearch:9200`，DB `logs-generic.otel-default`   | logs            |

日志数据源配置了 `logMessageField=body.text`、`logLevelField=severity_text`，并从 `scope.name` 派生 `logger` 字段（OTel 日志模型字段名）。

### 仪表板（`provisioning/dashboards/json/`，folder `WenxinBlog`）

仓库内已 provisioned **4 个**仪表板：

| 仪表板 JSON                | 内容                                                                          |
| -------------------------- | ----------------------------------------------------------------------------- |
| `wenxinblog-overview.json` | 服务总览：QPS / 错误率 / P95 / JVM 堆内存 / GC / 业务指标                     |
| `wenxinblog-api.json`      | API 维度：HTTP 请求量、状态码分布、响应时间分布、按 `service_name` 过滤       |
| `wenxinblog-logs.json`     | 日志流（ES Logs 数据源），按服务 / 级别筛选，请求范围内的日志带 `traceId`     |
| `wenxinblog-traces.json`   | 链路（ES Traces 数据源），按服务 / span 类型聚合，`traceId`/`spanId` 联动日志 |

> **traces 瀑布图渲染受限**：Grafana 的 ES 数据源对 trace 视图支持有限（近似，非完整瀑布）。
> 完整的 trace 瀑布图需要 **Tempo** 作为 traces 后端（当前未接入，是后续可选项）。

## 告警规则

> **当前状态**：本地 dev 环境只部署了 Prometheus + Grafana，**未接 AlertManager**。
> 以下规则是面向生产环境的模板，迁移到生产时再用 `service_name` 标签接入 AlertManager
> （metrics 现已统一从 Collector :8889 抓，标签用 `service_name`，不再是 `job="<service>"`）。

### Prometheus告警规则

```yaml
# alert_rules.yml（生产模板）
groups:
  - name: service_alerts
    interval: 30s
    rules:
      # Collector / Prometheus 健康度（目前唯一抓取目标）
      - alert: OtelCollectorDown
        expr: up{job="otel-collector"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: 'OTel Collector 宕机（指标采集中断）'

      # 服务错误率过高（用 service_name 标签）
      - alert: HighErrorRate
        expr: |
          sum by (service_name) (
            rate(http_server_requests_seconds_count{status=~"5.."}[5m])
          )
          /
          sum by (service_name) (
            rate(http_server_requests_seconds_count[5m])
          ) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: '服务 {{ $labels.service_name }} 错误率过高'

      # 响应时间过长
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95,
            sum by (le, service_name) (
              rate(http_server_requests_seconds_bucket[5m])
            )
          ) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: '服务 {{ $labels.service_name }} P95延迟过高'

      # CPU使用率
      - alert: HighCPUUsage
        expr: rate(process_cpu_seconds_total[5m]) > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: 'CPU使用率过高'

  - name: database_alerts
    rules:
      # 数据库连接数（4 个库）
      - alert: HighDBConnections
        expr: pg_stat_database_numbackends{datname=~"auth_db|user_db|blog_db|experiment_db"} > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: '数据库连接数过高'

      # 慢查询
      - alert: SlowQueries
        expr: rate(pg_stat_statements_calls_total[5m]) > 100
        for: 10m
        labels:
          severity: info
        annotations:
          summary: '检测到慢查询'

  - name: business_alerts
    rules:
      # 广告收入
      - alert: LowAdRevenue
        expr: rate(ad_revenue_total[1h]) < 100
        for: 1h
        labels:
          severity: warning
        annotations:
          summary: '广告收入过低'

      # 用户增长
      - alert: LowUserGrowth
        expr: increase(user_registered_total[1d]) < 10
        for: 1d
        labels:
          severity: info
        annotations:
          summary: '日新增用户过低'
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

OTel Collector 把 traces 写入 Elasticsearch 的 `traces-generic.otel-default` data stream，
Grafana 用 `Elasticsearch-Traces` 数据源渲染。**不再用 Spring Cloud Sleuth / Zipkin。**

### 注入方式

- **Java 服务**：OTel Java Agent 自动 instrumentation（HTTP 客户端、DB、Kafka、Redis 等全链路 span），
  跨服务透传 W3C TraceContext。`scripts/start-dev.sh` 用 `-javaagent` 注入。
- **Go 服务**：OTel SDK，fiber/http 中间件 + 自动 propagator。
- 采样：dev 环境 **全量**上报（默认 HEAD 采样，根 span 100%）。

### 追踪信息展示

Grafana `wenxinblog-traces` 仪表板里一条 trace 的典型形态（字段取自 ES OTel 文档）：

```
Trace: traceId（128 bit hex）
└── Gateway span      service.name=gateway        kind=server
    └── Blog span     service.name=blog-service   kind=client→server
        ├── DB span   name=SELECT posts           kind=client
        └── Kafka span name=send wenxinblog...    kind=producer
```

span 文档字段：`traceId` / `spanId` / `parentSpanId` / `name` / `kind` /
`resource.service.name` / `duration` / `status.code`。请求范围内的日志会带相同 `traceId`，
可在 logs 仪表板里联动定位。

> **限制**：ES 数据源下 trace 瀑布图为近似渲染；真瀑布图需 Tempo（未接入）。

## 日志聚合

### 链路（OTel，非 SLS）

Logback（Java）/ Go slog / Python logging → **OTLP logs** → Collector（transform 处理器把
`scope.name` 类名拼进 body）→ Elasticsearch `logs-generic.otel-default` data stream →
Grafana `Elasticsearch-Logs` 数据源。

本地 dev **不接阿里云 SLS**；SLS 是面向生产环境可选项。

### 日志字段（OTel 数据模型）

```json
{
  "@timestamp": "2026-08-01T00:00:00.000Z",
  "severity_text": "INFO",
  "scope": { "name": "c.w.blog.service.PostService" },
  "body": { "text": "c.w.blog.service.PostService | Post created postId=..." },
  "resource": { "service.name": "blog-service", "deployment.environment": "dev" },
  "traceId": "abc123...",
  "spanId": "def456..."
}
```

`body` 是 transform 处理器拼好的「类名 | 原始消息」，Grafana 日志流每行直接显示。

## ClickHouse（行为事件 OLAP）

> 严格说属于数据层而非可观测性，但同样在基建栈里，在此一并说明。

- 镜像 `clickhouse/clickhouse-server:24.8-alpine`，HTTP `8123`（JDBC 驱动用）；TCP `9000` 不映射到宿主机（端口被 MinIO 占用），仅 Docker 网络内 `clickhouse:9000` 直连。
- 用途：**analytics-service** 写入/查询用户行为事件（曝光、点击、阅读等），供推荐与报表分析。
- 网络：`infra/clickhouse/users.d/default-user.xml` 把 `default` 用户的来源放宽到 `::/0`（dev 环境，方便 analytics-service 从宿主机经端口映射连入；**生产应收紧为内网网段**）。
- 不参与 OTel 三支柱管道（traces/logs/metrics 都不进 ClickHouse）。

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

> 面向生产环境；本地 dev 未接入。

| 渠道      | 触发条件 | 用途        |
| --------- | -------- | ----------- |
| 钉钉群    | Warning+ | 开发团队    |
| 钉钉个人  | Critical | on-call人员 |
| 短信      | Critical | 紧急故障    |
| 邮件      | 每日报告 | 运营汇总    |
| PagerDuty | Critical | 值班轮换    |

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
