# Grafana 仪表盘：OTel 指标迁移说明（已对照 live 数据）

metrics 从「Micrometer → 各服务 /actuator/prometheus 被 Prometheus 直抓」改为
「Micrometer → OTel Java Agent 的 Micrometer 桥 → OTLP → Collector → Prometheus(:8889)」。
Grafana 数据源仍是 Prometheus。**以下结论已用 blog-service live 流量验证（2026-07-31）。**

## ✅ 已验证

- **`service_name` 标签**：Collector 的 `resource_to_telemetry_conversion.enabled: true` 把
  resource `service.name` 暴露为 Prometheus 标签 `service_name`（如 `service_name="blog-service"`）。
  两个仪表盘里 `service=~` 已改 `service_name=~`（api 6 处、overview 12 处）。
- **Micrometer 指标名被保留**：Micrometer 桥（`otel_scope_name="io.opentelemetry.micrometer-1.5"`）
  原样转发 Micrometer meter，名字不变：`http_server_requests_seconds_*`（`_bucket/_sum/_count`）、
  `jvm_threads_live_threads`、`process_cpu_usage`、`jvm_memory_*` 等。**原面板指标名基本不用改。**
- **Micrometer 原有标签保留**：`http_server_requests_seconds_*` 带 `method`、`status`、`uri`、
  `outcome`、`exception` 等原标签（额外多了 `service_name`、`deployment_environment` 等 OTel resource 标签）。
- **三支柱都通**：traces（OpenSearch `otel-traces`，56 条 blog-service span）、
  logs（`otel-logs`，Logback→OTLP）、metrics（Prometheus 80 个指标族）。

## ⚠️ 需注意：HTTP 指标有双份

同时存在两套 HTTP 指标（不报错，只是冗余）：

1. **Micrometer 桥**：`http_server_requests_seconds_*`（标签 method/status/uri…）—— **原面板用这套**。
2. **Agent 自带 Netty instrumentation**：`http_server_request_duration_seconds_*`
   （标签 http_request_method/http_response_status_code/http_route…，OTel 语义约定）。

面板沿用 Micrometer 名字即可；如想去掉冗余，可只关注 `http_server_requests_seconds_*`。

## 仍需现场微调

- **`up{service_name=...}` 面板**：现在只有一个抓取目标（otel-collector:8889），`up` 不带每服务标签。
  改为 `up{job="otel-collector"}`（反映 Collector 健康）。
- **业务指标**（blog__/comment__/embedding_* 等）：经 Micrometer 桥同样会转发，但只有对应代码路径
  执行后才注册/出现（GET /api/v1/posts 不一定触发）。验证时跑对应业务流量再看。
- **Traces/Logs 仪表盘**：数据源 `OpenSearch-Traces`/`OpenSearch-Logs` 已 provision。
  span 字段：`traceId`/`spanId`/`parentSpanId`/`name`/`kind`/`resource.service.name`；
  日志字段：`body`/`severity`/`resource.service.name`，请求范围内的日志会带 `traceId`（启动日志无）。
- **聚合可用，无需 index template**：opensearch exporter 自带模板已给关键字段加了 `.keyword` 子字段。
  实测 `resource.service.name.keyword` / `kind.keyword` / `severity.text.keyword` 的 `terms` 聚合全部正常
  （traces：blog-service 303 / gateway 12 / recommendation 10 / embedding 6；logs：按服务、按 INFO/WARN 级别均可分组）。
  Traces/Logs 仪表盘已据此加「按服务/按类型/按级别」分布饼图 + 趋势按服务分色，**不需要再手动建 keyword 模板**。
