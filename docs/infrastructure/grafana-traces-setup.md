# Grafana Traces 看板配置详解

> 面向手动在 Grafana UI 里配置/修改 Traces 看板的参考文档。
> 所有内容均基于 wenxinBlog 实际环境（ES 9.3.8 + OTel Collector + Grafana 13.1.1）验证。
> 配套看板文件：`infra/grafana/provisioning/dashboards/json/wenxinblog-traces.json`

---

## 1. 数据源（Datasource）

| 项                        | 值                                                |
| ------------------------- | ------------------------------------------------- |
| Type                      | Elasticsearch                                     |
| UID                       | `elasticsearch-traces`                            |
| URL                       | `http://elasticsearch:9200`                       |
| Database（index pattern） | `traces-generic.otel-default`（OTel data stream） |
| Time field                | `@timestamp`                                      |
| ES version                | `8.0+`                                            |
| XPACK                     | 关闭（`xpack: false`）                            |

配置文件：`infra/grafana/provisioning/datasources/datasource.yml`

> Traces 存在 OTel Collector 的 `elasticsearch/traces` exporter 自动建的 data stream 里，
> 无需手动建索引模板。

---

## 2. Trace 文档字段速查

每条 span 是一个 ES 文档。关键字段：

| 字段                               | 类型                  | 说明 / 单位                                                                                                                    |
| ---------------------------------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `@timestamp`                       | date                  | **span 开始时间**，epoch ms（带 µs 小数，如 `1785528717758.31`）。OTel ES **没有独立的 end_time 字段**，end = start + duration |
| `duration`                         | long                  | **span 持续时长，单位纳秒**（`104833917` = 104.8ms）。用 Grafana unit `ns` 自动转 ms/s                                         |
| `trace_id`                         | keyword               | 链路 ID，一条链路所有 span 共享                                                                                                |
| `span_id`                          | keyword               | 当前 span ID                                                                                                                   |
| `parent_span_id`                   | keyword               | 父 span ID（根 span 无此字段）→ 决定父子层级                                                                                   |
| `name`                             | keyword               | span 操作名（如 `GET /api/v1/posts`、`SELECT blog_db.posts`）                                                                  |
| `kind`                             | keyword               | `SERVER` / `CLIENT` / `INTERNAL`（CLIENT 含 DB/HTTP 出站调用）                                                                 |
| `service.name`                     | keyword（顶层 alias） | 服务名，等价于 `resource.attributes.service.name`                                                                              |
| `resource.attributes.service.name` | keyword               | 服务名（完整路径）                                                                                                             |
| `scope.name`                       | keyword               | 产生 span 的 instrumentation 模块                                                                                              |
| `status.code`                      | keyword               | `ERROR` 等。**当前多数 span 此字段为空**（成功 span 不写）                                                                     |
| `attributes.*`                     | keyword               | db.system / http.response.status_code / url.path 等                                                                            |

查字段映射：`curl 'localhost:9200/traces-generic.otel-default/_mapping?pretty'`

---

## 3. ES 查询模型核心规则（最容易踩的坑）

Grafana 的 ES 数据源走 `/api/ds/query`，对 query model 有**严格校验**。每个查询必须有：

```json
{
  "query": "<lucene>",
  "timeField": "@timestamp",
  "metrics": [...],      // 必须非空
  "bucketAggs": [...]    // 必须非空，除非 metrics 是 raw_data / logs
}
```

### ⚠️ 坑 1：`bucketAggs` 不能为空（除非 raw_data/logs）

`metrics` 是 count/cardinality/percentiles 而 `bucketAggs: []` → **500 错误**
`"received invalid query. invalid query, missing metrics and aggregations"`。

**解法**：所有 stat 卡都要带一个 `date_histogram` 桶（即使你只想要单个数字）。
这跟 logs 看板的 stat 卡一致。

### ⚠️ 坑 2：raw_data 查询可以空 bucketAggs

表格用 `metrics: [{type: "raw_data"}]` + `bucketAggs: []` 是合法的（返回原始文档行）。

### metrics 类型对照

| type                  | 用途                     | 备注                                                     |
| --------------------- | ------------------------ | -------------------------------------------------------- |
| `count`               | 计数                     | 可加（跨桶 sum = 总数）                                  |
| `cardinality`         | 去重计数（如 trace 数）  | field 必填；**不可加**，但 trace 不跨时间桶，sum 仍=总数 |
| `percentiles`         | 分位（延迟 P50/P95/P99） | settings.percents: `["50","95","99"]`；不可加            |
| `max` / `min` / `avg` | 聚合                     | field 必填                                               |
| `raw_data`            | 原始文档（表格用）       | settings.size                                            |
| `logs`                | 日志流                   | 日志面板                                                 |

### bucketAggs 类型对照

| type             | 用途                                    |
| ---------------- | --------------------------------------- |
| `date_histogram` | 时间分桶（时序图、stat 的时间桶）       |
| `terms`          | 按字段分桶（按服务/类型/trace_id 分组） |

**关键**：terms 桶按子聚合排序要用 `orderBy: "<子聚合metric的id>"`。

---

## 4. 各面板配置配方

### 4.1 Stat 卡（Span 总数 / Trace 数 / 错误数 / P95）

```
Query: *
metrics: count（或 cardinality/percentiles，见下）
bucketAggs: date_histogram(@timestamp, interval auto)   ← 不能少！
```

reduceOptions（面板右侧 Options）：

| 指标                        | calcs  | 含义                         |
| --------------------------- | ------ | ---------------------------- |
| count（Span 总数 / 错误数） | `sum`  | 跨桶求和 = 总数              |
| cardinality（Trace 数）     | `sum`  | trace 不跨桶，sum = 总唯一数 |
| percentiles（P95）          | `last` | 最近桶的 P95                 |

P95 卡的 fieldConfig → unit 设 `ns`（duration 是纳秒）。
错误数卡：query 改成 `status.code:ERROR`，thresholds 设 `null→green, 1→red`。

### 4.2 时序图：Span 吞吐（按服务）

```
metrics: count
bucketAggs: terms(service.name, size 10) → date_histogram(@timestamp, auto)
```

fieldConfig → stacking: normal（堆叠看各服务占比）。

### 4.3 时序图：延迟分位 P50/P95/P99

```
metrics: percentiles(duration, percents ["50","95","99"])
bucketAggs: date_histogram(@timestamp, auto)
```

fieldConfig → unit `ns`。这是 trace 看板最核心的生产指标。

### 4.4 BarGauge：各服务 P95 延迟

```
metrics: percentiles(duration, ["95"])
bucketAggs: terms(service.name, size 12)
```

unit `ns`，thresholds `green→10ms→yellow→50ms→red`（值用 ns：`10000000`/`50000000`）。

### 4.5 饼图：按服务 / 按类型分布

```
metrics: count
bucketAggs: terms(service.name 或 kind, size 10)
```

**⚠️ 坑 3**：面板 Options → `Reduce → Values` 必须设为 `All values`（JSON 里 `values: true`）。
默认 `values: false` 会让饼图把所有值 reduce 成一个数字 → **只画 1 个整圆切片**，看不出分布。

### 4.6 表格：最慢的 Trace

```
metrics: count, max(duration)
bucketAggs: terms(trace_id, size 15, orderBy "<max的id>", order desc)
```

- orderBy 填 max(duration) 这个 metric 的 id（如 `"2"`），才能按"最长 span"排序。
- max(duration) 列要显示成 ms：fieldConfig → Overrides → byName `Max duration` → unit `ns`。

### 4.7 表格：链路详情（选 trace 看全部 span）

```
metrics: raw_data(size 500)
bucketAggs: []（raw_data 允许空）
query: trace_id:$trace
```

配 Transformations 见下节。

---

## 5. Transformations（变换）—— 坑最多

链路详情表用一串变换把 raw_data 的几十个字段裁成有用的几列。**顺序敏感**。

### 当前配方（按顺序）

1. `Convert field type` — 把 `@timestamp` 从 time 转成 number（为了显示毫秒，见坑 4）
2. `Filter by name` — 只留 `@timestamp, name, resource.attributes.service.name, kind, duration, parent_span_id, span_id`
3. `Sort by` — `@timestamp` 升序
4. `Organize` — 重命名 + 排列

### ⚠️ 坑 4：时间字段显示毫秒

`@timestamp` 是 `time` 类型。Grafana 表格对 time 字段**无视 unit override、默认只渲染到秒**。
直接给 time 字段套 `dateTimeAsIso` unit 会退化成裸 epoch 数字。

**解法**：先用 `Convert field type` 把 `@timestamp` 转成 `Number`，再用 Override → unit `dateTimeAsIso`
（这个 unit 是给 number 字段 epoch-ms 用的）→ 渲染成 `2026-07-31T12:25:17.758`（带毫秒）。

Convert field type 的配置：

```
Conversions: [ { Target field: @timestamp, Convert to: Number } ]
```

### ⚠️ 坑 5：Override 匹配的是最终字段名

fieldConfig overrides 在所有 transformation **之后**应用，匹配的是重命名后的字段名。
例如 `Organize` 把 `duration` → `耗时`，那 Override 要 match `耗时`，不是 `duration`。
`Max duration` 被重命名成 `最长 span`，Override 也得 match `最长 span`。

### ⚠️ 坑 6：calculateField 运算符位置

用 `Calculate field` 做 `duration / 1000000` 时，运算符必须写在 `binary.operator` 里，
**不能**写在顶层 `operation`（会被忽略，计算失败）。别名 alias 不一定能被 filterFieldsByName 匹配上，
所以本看板改用「raw duration + unit=ns override」显示 ms，更稳，不用 calculateField。

### ⚠️ 坑 7：duration 单位

`duration` 字段是**纳秒**（long）。所有显示 duration 的面板/列都要设 unit `ns`，
Grafana 自动格式化成 `ms`/`s`。`104833917` → `104.83 ms`。

---

## 6. 模板变量（Variables）

### Trace 选择变量

```
Type: Query
Datasource: elasticsearch-traces
Query: {"find": "terms", "field": "trace_id", "size": 30}
Selection options: Include All = on
```

**⚠️ 坑 8**：`{find: terms}` 格式**不支持** `order` / `orderBy` 键，加了会报"Variable query error"。
默认按 doc_count 降序（span 最多的链路排前）。terms agg 默认就是 count desc，够用。

### 时间范围影响变量

ES 的 terms 变量查询受**看板时间范围**限制。默认 `now-1h` 时，空闲服务（只在有流量时打日志/trace）
会从下拉里消失。Traces 看板默认时间设 `now-24h`，保证所有服务/链路可见。

---

## 7. 已知限制（重要）

### ❌ ES 数据源无法渲染 Trace 瀑布图（Waterfall）

Grafana 的 trace 瀑布图面板（可展开 span、父子层级、耗时条）**写死绑定 trace 类数据源**
（Tempo / Jaeger / Zipkin）。ES 数据源不实现 TraceAPI，无法激活该面板。这是架构限制，无解。

当前用「按时间排序的 span 表格」**近似**替代（链路详情表），但没有父子层级、不可展开。

### ❌ ES|QL 拿不到 span 行数据

Grafana 13 的 ES 数据源支持 ES|QL（实验特性，feature flag `elasticsearchESQLQuery`），
但 raw 查询（`FROM ... | KEEP ... | LIMIT`）返回**空 frame**——这是已知插件缺陷
（Grafana 注入时间过滤 + 非聚合结果解析不全），只有 `STATS` 聚合能返回数据。
所以 ES|QL 出不了瀑布图，只能做聚合统计。

### 想要真·瀑布图 → 加 Tempo

唯一出路。复用现有 OTel Collector，traces 管道多导一份 OTLP 给 Tempo：

- `docker-compose.yml` 加 `tempo` 服务（本地 parquet 存储）
- `infra/otel-collector/config.yaml` 的 traces pipeline exporters 加 `otlp/tempo`
- Grafana 加 Tempo 数据源 + 一个 `Traces` 面板（真瀑布图）
- ES 里的 traces 数据原样保留，互不影响

---

## 8. 调试技巧

### 查实际查询是否报错（不走 UI）

```bash
# 用 /api/ds/query 复刻面板查询，看真实报错（不是 _msearch，后者太宽松）
curl -s -u admin:admin 'http://localhost:3001/api/ds/query' \
  -H 'Content-Type: application/json' \
  -d '{"queries":[{"refId":"A","datasource":{"type":"elasticsearch","uid":"elasticsearch-traces"},
    "queryType":"lucene","query":"*","timeField":"@timestamp",
    "metrics":[{"id":"1","type":"count"}],
    "bucketAggs":[{"id":"2","type":"date_histogram","field":"@timestamp","settings":{"interval":"auto"}}],
    "maxDataPoints":100,"intervalMs":1000}],"from":"now-1h","to":"now"}' | jq '.results.A'
```

`status: 500` + `error: "missing metrics and aggregations"` = bucketAggs 空了。

### UI 里的 Query Inspector

面板右上角 → `Inspect` → `Data` 标签：看 Grafana 实际发的请求和返回的字段名，
**这是调试 transformation/override 匹配的最佳工具**（fieldConfig override 匹配哪个名字一目了然）。

### 查字段映射 / 样例文档

```bash
curl 'localhost:9200/traces-generic.otel-default/_mapping' | jq        # 字段类型
curl 'localhost:9200/traces-generic.otel-default/_search?size=1' | jq  # 样例 span
```

---

## 9. 当前看板面板清单（wenxinblog-traces）

| 面板                     | 类型       | 核心配置                                                                          |
| ------------------------ | ---------- | --------------------------------------------------------------------------------- |
| Span 总数                | stat       | count + date_histogram, reduce sum                                                |
| Trace 数                 | stat       | cardinality(trace_id) + date_histogram, reduce sum                                |
| 错误 Span                | stat       | count + query `status.code:ERROR` + date_histogram, reduce sum                    |
| P95 延迟（最近）         | stat       | percentiles(duration,95) + date_histogram, reduce last, unit ns                   |
| Span 吞吐趋势（按服务）  | timeseries | count + terms(service) + date_histogram, 堆叠                                     |
| 各服务 P95 延迟          | bargauge   | percentiles(duration,95) + terms(service), unit ns                                |
| 请求延迟分位 P50/P95/P99 | timeseries | percentiles(duration,[50,95,99]) + date_histogram, unit ns                        |
| Span 按服务分布          | piechart   | count + terms(service), **values: true**                                          |
| 最慢的 Trace             | table      | count+max(duration) + terms(trace_id, orderBy max), Max duration override unit ns |
| Span 按类型分布          | piechart   | count + terms(kind), **values: true**                                             |
| 链路详情                 | table      | raw_data + convertFieldType(@timestamp→number) + filter + sort + organize         |

变量：`trace`（trace_id terms，size 30）。默认时间 `now-24h`。
