# Ad Service

广告服务。**双定位**：既是一个用于学习的完整自服务广告平台（广告主门户 / 竞价 / CPC·CPM·CPA 计费），又是一个面向现实变现的**发布者广告中介层**（house ads + 外部广告联盟 waterfall）。

> 小型博客平台难以吸引大的广告主来自助投放，因此纯自服务广告平台是"空中楼阁"。
> 本服务的现实落地路径是 **mediation/waterfall**：自营广告优先 → 外部联盟填充。
> 自服务门户与竞价保留，作为学习完整广告系统架构的载体。

---

## 1. 实现现状（体检）

| 模块                                                   | 状态      | 说明                                                                                                          |
| ------------------------------------------------------ | --------- | ------------------------------------------------------------------------------------------------------------- |
| 广告主管理（campaign CRUD / pause / activate / stats） | ✅ 完整   | `/api/v1/campaigns`，含 IDOR 属主校验                                                                         |
| 决策投放（`AdDecisionService.decide`）                 | ⚠️ 半成   | 预算/日期/频控/出价排序都有；**定向是 stub**（永远通过）；`/internal/ads/decision` **零调用方**（没接进产品） |
| 追踪（click / conversion）                             | ⚠️ 有 bug | 去重 + 存事件 + 发 Kafka；但 `campaignId` 错用 `creativeId`（mock）                                           |
| 计费                                                   | ⚠️ 不闭环 | impression 走 `debitSpend`（CPM 已实现）；**click/conversion 不扣费**，CPC/CPA 未实现                         |
| 事件管道（Kafka `ad-events`）                          | ❌ 断裂   | ad-service 是**生产者**，value 是 `Map.toString()`（非 JSON），**无消费者**                                   |
| 前端广告位                                             | ❌ 无     | 前端零广告组件、无投放点                                                                                      |
| 中介层（house ads / 外部联盟）                         | ❌ 未做   | 见第 7 节方案                                                                                                 |

**结论**：后端是个"能演示管理、但不能真投放"的半成品管道，且没接进产品。

---

## 2. 技术栈

- Java 25 + Spring Boot 4.0.4（WebFlux / R2DBC）
- PostgreSQL（blog_db，ad 表；Flyway 管理迁移，独立 history table）
- Redis（频次控制、点击去重）
- Kafka（`ad-events` topic）

端口：**8007**（注意：不是 8006，8006 是 recommendation-service）

---

## 3. 数据模型（真实，Flyway 管理）

### ad_campaigns

```sql
id              BIGSERIAL PK
advertiser_id   VARCHAR        -- 广告主标识（house 广告用固定值 "house"）
name            VARCHAR
description     TEXT
budget          DECIMAL(10,2)  -- 总预算
daily_budget    DECIMAL(10,2)  -- 每日预算
spent           DECIMAL(10,2)  -- 累计已花（debitSpend 原子扣减）
daily_spent     DECIMAL(10,2)  -- 当日已花（@Scheduled 每日重置）
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

### 投放决策（内部，目前无调用方）

```
POST   /internal/ads/decision
Body: { positionType, userId, count, ipAddress, userAgent }
Resp: [{ creativeId, campaignId, title, imageUrl, landingUrl, creativeType, bidAmount }]
```

### 追踪（经网关 `/api/v1/ads/t/**`）

```
POST   /api/v1/ads/click         body: { creativeId }   -- 24h 去重
POST   /api/v1/ads/conversion    body: { creativeId }
```

---

## 5. 决策流程（真实逻辑）

```
decide(positionType, userId, count)
  1. 查 status=ACTIVE 的 campaign
  2. 过滤：hasBudget（总/日预算剩余 > 0）
  3. 过滤：isWithinDateRange（在 start/end 之间）
  4. 过滤：matchesTargeting   ← stub，永远 true（待实现）
  5. 每个 campaign 取一个 active creative
  6. 按 bidAmount 降序排序（出价高者得，一价近似，无真竞价）
  7. take(count)
  8. 过滤：checkFrequencyCap（Redis，每用户每 campaign ≤5 次/小时）
  9. recordImpression：debitSpend 原子扣费（CPM = bid/1000）→ 存 IMPRESSION 事件
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

### Kafka `ad-events`（断裂，待修）

- 生产者：`AdTrackingService.publishEvent`，value = `Map.of(...).toString()` → **Java Map toString 格式，非合法 JSON**，消费者无法解析。
- 消费者：**无**。ad 效果数据（曝光/点击/CTR/转化）收不上来。
- 计划修：value 改 `objectMapper.writeValueAsString(...)`；analytics-service 加消费者落 ClickHouse。

### 待修 bug 清单

1. **campaignId 归因**：click/conversion 把 `creativeId` 当 `campaignId` 写入（mock），需反查真实 campaign。
2. **CPC/CPA 扣费**：recordClick/recordConversion 补 `debitSpend`。
3. **Kafka value JSON 化** + **ad-events 消费者**。

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
