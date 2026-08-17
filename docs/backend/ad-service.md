# Ad Service

广告服务。**双定位**：既是一个用于学习的完整自服务广告平台（广告主门户 / 竞价 / CPC·CPM·CPA 计费），又是一个面向现实变现的**发布者广告中介层**（house ads + 外部广告联盟 waterfall）。

> 小型博客平台难以吸引大的广告主来自助投放，因此纯自服务广告平台是"空中楼阁"。
> 本服务的现实落地路径是 **mediation/waterfall**：自营广告优先 → 外部联盟填充。
> 自服务门户与竞价保留，作为学习完整广告系统架构的载体。

---

## 1. 实现现状（体检）

| 模块                                                   | 状态          | 说明                                                                                                                                                          |
| ------------------------------------------------------ | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 广告主管理（campaign CRUD / pause / activate / stats） | ✅ 完整       | `/api/v1/campaigns`，含 IDOR 属主校验                                                                                                                         |
| 决策投放（`AdDecisionService.decide`）                 | ✅ 已接入产品 | 预算/日期/定向（`targeting.positions`）/频控/出价排序都有；`POST /api/v1/ads/decision` 经网关公开（IP 限流 60/min），web 前端 `<AdSlot>` 调用；多创意随机轮播 |
| 追踪（click / conversion）                             | ✅ 已修       | 归因 bug 已修（反查 creative → 真实 campaign）；匿名点击去重回退用 IP                                                                                         |
| 计费                                                   | ⚠️ CPM 闭环   | IMPRESSION 走 `debitSpend`（列精度已修到 4 位小数）；**click/conversion 不扣费**，CPC/CPA 未实现                                                              |
| 事件管道（Kafka `ad-events`）                          | ✅ 打通       | IMPRESSION/CLICK/CONVERSION 全部经 `AdEventPublisher` 发 JSON（IMPRESSION 含 positionType）；analytics 消费落 ClickHouse 出报表                               |
| 前端广告位                                             | ✅ 已接入     | web：feed 第 3 卡后 + 侧边栏两处 `<AdSlot>`（house ads）；mobile 未接                                                                                         |
| house ads                                              | ✅ 已上线     | V2 迁移预置 3 条 house campaign（`advertiser_id='house'`），100% 填充                                                                                         |
| 外部联盟中介层                                         | ❌ 未做       | 见第 7 节方案（有真实流量后再做）                                                                                                                             |

**结论**：house ads 的「决策 → 渲染 → 计费 → 点击上报」闭环已打通；自服务门户（真广告主/竞价/CPC·CPA）仍是学习用半成品。

### 已知问题（known issues）

1. **决策即计费，无 viewability**：`decide()` 返回时就记 IMPRESSION 并扣费，不保证前端真的渲染到视口，且曝光无去重（刷新页面重复扣费）。house ads 自营无碍；接真广告主前必须改为前端曝光上报 + 去重。
2. **公开端点依赖频控+限流防刷**：decision/click 无鉴权（IP 限流 60/min + 每用户每 campaign 5 次/h 频控 + 预算上限兜底）；攻击者轮换 IP/UUID 仍可消耗 house 预算，接外部联盟前需加固。
3. **定向仅支持 positions 一个维度**：分类/地域等维度未做；targeting JSON 解析失败 fail-open（按不限定处理）。

---

## 2. 技术栈

- Java 25 + Spring Boot 4.0.4（WebFlux / R2DBC）
- PostgreSQL（blog_db，ad 表；Flyway 管理迁移，独立 history table）
- Redis（频次控制、点击去重）
- Kafka（`ad-events` topic）

端口：**8007**（注意：不是 8006，8006 是 recommendation-service）

---

## 3. 数据模型（真实，Flyway 管理）

> 迁移：`V1__init_ad_schema.sql`（建表+预置 4 个广告位）→ `V2__seed_house_ads.sql`（3 条 house campaign + 创意）→ `V3__ad_spend_precision.sql`（计费列精度 2→4 位小数）→ `V4__house_ads_position_targeting.sql`（house 广告位定向：FEED×2 / SIDEBAR×1，campaign 1 加第二条创意验证轮播）。

### ad_campaigns

```sql
id              BIGSERIAL PK
advertiser_id   VARCHAR        -- 广告主标识（house 广告用固定值 "house"）
name            VARCHAR
description     TEXT
budget          DECIMAL(12,2)  -- 总预算
daily_budget    DECIMAL(12,2)  -- 每日预算
spent           DECIMAL(12,4)  -- 累计已花（debitSpend 原子扣减；V3 起 4 位小数，对齐 CPM 单次成本）
daily_spent     DECIMAL(12,4)  -- 当日已花（@Scheduled 每日重置）
bid_strategy    VARCHAR        -- CPM / CPC / CPA
bid_amount      DECIMAL(10,2)  -- 出价（CPM=千次曝光价；CPC=单次点击；CPA=单次转化）
targeting       VARCHAR(JSON)  -- 定向条件（当前 stub，未生效）
status          VARCHAR        -- PENDING / ACTIVE / PAUSED / COMPLETED
start_date / end_date  TIMESTAMP
created_at / updated_at TIMESTAMP
```

### ad_creatives

```sql
id              BIGSERIAL PK
campaign_id     BIGINT FK ad_campaigns
title / image_url / landing_url / creative_type   -- 创意内容
is_active       BOOLEAN
```

### ad_events

```sql
id              BIGSERIAL PK
campaign_id / creative_id  BIGINT
user_id / ip_address / user_agent
event_type      VARCHAR        -- IMPRESSION / CLICK / CONVERSION
created_at      TIMESTAMP
-- 索引：(campaign_id, created_at), (event_type, created_at)
```

### ad_positions

```sql
id              SERIAL PK
name / description / position_type   -- FEED / SIDEBAR / BANNER（预置广告位）
```

---

## 4. API（真实 endpoints）

### 广告主管理（经网关，需登录）

```
POST   /api/v1/campaigns            创建 campaign
GET    /api/v1/campaigns            列表（仅自己的，IDOR 校验）
GET    /api/v1/campaigns/{id}       详情（属主校验）
PUT    /api/v1/campaigns/{id}       更新（属主校验）
PUT    /api/v1/campaigns/{id}/pause    暂停（属主校验）
PUT    /api/v1/campaigns/{id}/activate 激活（属主校验）
GET    /api/v1/campaigns/{id}/stats    统计（属主校验）
```

### 投放决策（经网关公开，供前端 `<AdSlot>` 调用）

```
POST   /api/v1/ads/decision      （网关 ad-public 路由，无鉴权 + IP 限流 60/min）
Body: { positionType, userId, count, ipAddress, userAgent }
Resp: [{ creativeId, campaignId, title, imageUrl, landingUrl, creativeType, bidAmount }]
```

> 注意：每次调用即记 IMPRESSION 并扣预算，前端必须每挂载只取一次（`useAdSlot` 已配 staleTime: Infinity + 不重试）。
> 前端未登录时携带 localStorage 匿名 UUID 作为 userId —— userId=null 会绕过频控且点击去重 key 退化为共享 IP。

### 追踪（经网关 `/api/v1/ads/**`，公开）

```
POST   /api/v1/ads/click         body: { creativeId, userId? }   -- 24h 去重（匿名回退 IP）
POST   /api/v1/ads/conversion    body: { creativeId, userId? }
```

---

## 5. 决策流程（真实逻辑）

```
decide(positionType, userId, count)
  1. 查 status=ACTIVE 的 campaign
  2. 过滤：hasBudget（总/日预算剩余 > 0）
  3. 过滤：isWithinDateRange（在 start/end 之间）
  4. 过滤：matchesTargeting（targeting.positions 含 positionType；为空/解析失败 = 不限定）
  5. 每个 campaign 从 active creatives 中随机取一条（多创意轮播，无状态）
  6. 按 bidAmount 降序排序（出价高者得，一价近似，无真竞价）
  7. take(count)
  8. concatMap（保序）：checkFrequencyCap（Redis，每用户每 campaign ≤5 次/小时）
  9. recordImpression：debitSpend 原子扣费（CPM = bid/1000）→ 存 IMPRESSION 事件 + 发 Kafka
```

**预算扣减是原子的**：`debitSpend(id, cost)` 用 SQL 条件更新（`WHERE spent + cost <= budget`），失败（预算不足）则不计费、不记事件——避免超支。

---

## 6. 计费与事件管道（现状 + 待修）

### 计费现状

| 事件       | 计费                        | 状态      |
| ---------- | --------------------------- | --------- |
| IMPRESSION | CPM：`debitSpend(bid/1000)` | ✅        |
| CLICK      | 应 CPC：`debitSpend(bid)`   | ❌ 未扣费 |
| CONVERSION | 应 CPA：`debitSpend(bid)`   | ❌ 未扣费 |

### Kafka `ad-events`（已打通）

- 生产者：`AdEventPublisher`（统一 payload schema）。IMPRESSION 在 `recordImpression` 计费成功后发（含 `positionType`）；CLICK/CONVERSION 由 `AdTrackingService` 归因后发。
- 消费者：analytics-service `AdEventConsumer` → ClickHouse `ad_events`（MergeTree/按天分区/90 天 TTL/启动时自动建表）。
- 报表：`GET /api/v1/analytics/ads/report`（campaign×creative×天：曝光/点击/转化/CTR）、`GET /api/v1/analytics/ads/report/positions`（position×天），需 `analytics:read`。
- 已知瑕疵：ClickHouse 按服务器时区（UTC）切天，东八区凌晨的报表日期会落在前一天；点击事件不带 positionType（前端点击上报时可补）。

### 已修 bug（2026-08 MVP 接入时）

1. ~~**campaignId 归因**~~：click/conversion 已反查 `AdCreative.campaignId`，creative 不存在则丢弃事件。
2. ~~**匿名去重共享 key**~~：userId 为 null 时 dedup key 回退 `ad:click:ip:{ip}:{creativeId}`。
3. ~~**Kafka value JSON 化**~~。
4. ~~**频控失效**~~：`checkFrequencyCap` 原在 Netty 事件循环里 `.block()` 触发 Reactor 线程校验异常 → 永远 fail-open（单测 mock Redis 测不出）。已改为全程响应式。
5. ~~**计费精度**~~：`spent`/`daily_spent` 原为 DECIMAL(12,2)，低于 CPM 单次成本（bid/1000，如 0.005）——bid=5 每次取整 +0.01（多扣一倍），bid<10 的 campaign 取整 +0.00（永不扣费）。V3 迁移已改 DECIMAL(12,4)。
6. ~~**出价排序被异步计费打乱**~~：sort 之后接的是 `flatMap`（频控+计费异步内层按完成顺序合并），Redis/DB 延迟抖动会把「出价高者在前」打乱——旧链路同步部分掩盖了它，创意轮播引入额外异步层后暴露。已改 `concatMap` 保序。

### 仍待修

1. **CPC/CPA 扣费**：recordClick/recordConversion 补 `debitSpend`。

---

## 7. 改造方案：发布者广告中介层（Mediation / Waterfall）

### 目标

在**保留**自服务门户 / 竞价 / CPC·CPM·CPA（学习载体）的前提下，叠加一层**现实变现能力**，让广告位真正能填充、能变现。

### 架构：waterfall 决策

```
前端 <AdSlot position="feed_between"/>
    │ POST /internal/ads/decision
    ▼
AdDecisionService（改造为 Mediator，按优先级瀑布）
    ① 直售 campaign（自服务门户，真广告主竞价）── 学习用，最高优先级
    ② House Ads（自营：推自己的博文/课程/友链，advertiser_id="house"）── 100% 收益、永远能填
    ③ 外部广告联盟（AdNetworkClient：百度联盟 / AdSense）── 填充兜底
    ④ Affiliate 兜底（分佣链接）
    ▼
命中哪层返回哪层素材；点击统一走 /api/v1/ads/click（house/直售自己记，联盟走联盟后台）
```

### 复用与新增

| 现有                                   | 改造                                                                    |
| -------------------------------------- | ----------------------------------------------------------------------- |
| `AdCampaign`/`AdCreative` + 管理 API   | **保留**：既给真广告主（直售），也存 house ads（advertiser_id="house"） |
| `AdDecisionService.decide`             | **扩展为 Mediator**：按 priority 瀑布直售→house→联盟                    |
| 广告主门户（campaign/budget/bidding）  | **保留**（学习价值）                                                    |
| bidding（当前一价）                    | **保留并可升级**二价（GSP）作为进阶练习                                 |
| CPC/CPA 计费                           | **保留并补全**（修 recordClick/recordConversion 扣费）                  |
| `matchesTargeting` stub                | 实现简单定向（分类/页面位置）即可                                       |
| 🆕 `AdNetworkClient` 接口 + 联盟适配器 | 新增：`BaiduUnionAdapter` / `AdSenseAdapter`（前端嵌 JS 或后端 API）    |
| 🆕 前端 `<AdSlot>` 组件                | 新增：调 /decision，渲染直售/house/联盟素材                             |
| 🆕 analytics 消费 ad-events            | 新增：广告报表（曝光/点击/CTR/转化/花费）                               |

### AdNetworkClient 接口设计

```java
public interface AdNetworkClient {
    String name();                                   // "baidu-union" / "adsense"
    Mono<AdDecisionResponse> requestAd(AdDecisionRequest req);  // 向联盟要一个广告
    Mono<Void> onImpression(AdDecisionResponse ad); // 联盟曝光回传（如需要）
    // 联盟点击直接走联盟 landing url，不经我们 /click
}
```

### Waterfall Mediator 伪代码

```java
decide(req):
    // ① 直售：现有竞价逻辑（出价高者得）
    return directSoldCampaigns(req)
        .next()                                        // 有直售就用
        .switchIfEmpty(houseAds(req).next())           // 否则 house ads
        .switchIfEmpty(adNetwork.requestAd(req))       // 否则外部联盟
        .switchIfEmpty(affiliateFallback(req));        // 否则 affiliate 兜底
```

### 为什么这个架构对

- **学习完整**：直售层保留了 campaign/adgroup/bidding/CPA 的完整广告平台逻辑。
- **现实可变现**：house ads 推自己内容（降跳出、涨 PV），外部联盟填充变现。
- **不浪费现有代码**：campaign/creative/decision/计费 全复用，只加 mediator 编排 + 联盟适配器。
- 符合业界发布者成熟度路径：house ads 是把填充率从 ~20% 拉到 90%+ 的零成本兜底层。

---

## 8. 真实变现建议（按博客受众）

| 受众        | 主力变现                                        | 说明                                        |
| ----------- | ----------------------------------------------- | ------------------------------------------- |
| 中文用户    | 百度联盟 / 360 联盟                             | 中文填充好；AdSense 在国内结算/填充不理想   |
| 海外/英文   | Google AdSense → Ezoic/Mediavine                | 流量上来后接 header bidding                 |
| 技术/开发向 | Affiliate（云服务/课程/书）+ House ads + 赞助位 | 展示广告 RPM 低，affiliate + 自营推广更现实 |

> 行业共识：小流量发布者自建广告平台不划算（无 demand 聚合、开发维护成本高）。
> 现实路径：联盟起步 → 优质网络 → header bidding → 规模化后才考虑自建广告服务器。

---

## 9. 运行

```bash
cd services/ad-service
mvn spring-boot:run          # 端口 8007，OTel Java Agent 已在 start-dev.sh 注入
```

依赖：blog_db（5434，Flyway 自动迁移）、Redis、Kafka 全在 docker compose 基建里。
