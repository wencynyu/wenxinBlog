# Ad Service

广告服务 - 负责广告投放、计费、报表

## 功能

- 广告位管理
- 广告计划管理
- 程序化投放 (RTB)
- 点击/转化追踪
- 预算控制
- 效果报表
- 反作弊

## 技术栈

- Java 25
- Spring Boot 4.0.4 (WebFlux)
- PostgreSQL (blog_db - ad表)
- Redis (缓存、限流)
- Kafka (广告事件)

## 数据库设计

### ad_positions (广告位)
```sql
CREATE TABLE ad_positions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    position_type VARCHAR(20) NOT NULL, -- BANNER, FEED, SIDEBAR, POPUP
    width INT,
    height INT,
    max_ads INT DEFAULT 1,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 预置广告位
INSERT INTO ad_positions (name, position_type, width, height) VALUES
('feed_between', 'FEED', NULL, NULL),
('sidebar_top', 'SIDEBAR', 300, 250),
('content_bottom', 'BANNER', 728, 90);
```

### ad_campaigns (广告计划)
```sql
CREATE TABLE ad_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advertiser_id UUID NOT NULL REFERENCES auth_db.users(id),
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, ACTIVE, PAUSED, COMPLETED

    -- 预算与排期
    budget_daily DECIMAL(10,2) NOT NULL,
    budget_total DECIMAL(10,2),
    start_date DATE NOT NULL,
    end_date DATE,

    -- 定向条件
    targeting JSONB, -- { age: [], gender: [], interests: [], locations: [] }

    -- 出价
    bid_strategy VARCHAR(20), -- CPC, CPM, CPA
    bid_amount DECIMAL(10,2) NOT NULL,

    -- 创意
    creatives JSONB, -- [{ image, title, description, landing_url }]

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### ad_creatives (广告创意)
```sql
CREATE TABLE ad_creatives (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES ad_campaigns(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL, -- IMAGE, VIDEO, HTML
    asset_id UUID REFERENCES media_assets(id),
    title VARCHAR(100),
    description TEXT,
    landing_url VARCHAR(500),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### ad_events (广告事件)
```sql
CREATE TABLE ad_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES ad_campaigns(id),
    creative_id UUID REFERENCES ad_creatives(id),
    position_id INTEGER REFERENCES ad_positions(id),

    -- 事件信息
    event_type VARCHAR(20) NOT NULL, -- IMPRESSION, CLICK, CONVERSION
    user_id UUID REFERENCES auth_db.users(id),
    session_id VARCHAR(100),

    -- 上下文
    context JSONB, -- { page, referrer, device, location }

    -- 计费
    cost DECIMAL(10,4), -- 这次事件的成本

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ad_events_campaign ON ad_events(campaign_id, created_at);
CREATE INDEX idx_ad_events_type ON ad_events(event_type, created_at);
```

## API

### 广告主接口
```
POST   /api/v1/ads/campaigns            - 创建广告计划
GET    /api/v1/ads/campaigns            - 获取广告计划列表
GET    /api/v1/ads/campaigns/:id        - 获取广告计划详情
PUT    /api/v1/ads/campaigns/:id        - 更新广告计划
DELETE /api/v1/ads/campaigns/:id        - 删除广告计划
POST   /api/v1/ads/campaigns/:id/pause  - 暂停计划
POST   /api/v1/ads/campaigns/:id/resume - 恢复计划
```

### 创意管理
```
POST   /api/v1/ads/creatives            - 上传创意
GET    /api/v1/ads/creatives            - 获取创意列表
PUT    /api/v1/ads/creatives/:id        - 更新创意
DELETE /api/v1/ads/creatives/:id        - 删除创意
```

### 投放接口 (内部调用)
```
POST   /internal/ads/decision           - 广告决策
Body: {
  positionId: 1,
  userId: "uuid",
  context: { page, referrer, device }
}

Response: {
  ad: {
    creativeId: "uuid",
    campaignId: "uuid",
    creative: { },
    trackingUrl: "https://ad.wenxinblog.com/t/xxx"
  }
}
```

### 追踪接口
```
GET    /api/v1/ads/t/impression/:eventId - 曝光追踪
GET    /api/v1/ads/t/click/:eventId     - 点击追踪
POST   /api/v1/ads/t/conversion         - 转化回传
```

### 报表接口
```
GET    /api/v1/ads/reports/campaigns/:id
       ?dateFrom=2024-01-01
       &dateTo=2024-01-31
       &dimensions=day,creative

Response: {
  "data": [
    {
      "date": "2024-01-01",
      "creativeId": "uuid",
      "impressions": 10000,
      "clicks": 200,
      "conversions": 10,
      "ctr": 0.02,
      "cpc": 0.5,
      "cost": 100
    }
  ]
}
```

## 广告决策流程

```python
def get_ad(position_id, user_id, context):
    # 1. 获取该广告位的活跃广告
    active_campaigns = get_active_campaigns(position_id)

    # 2. 用户定向匹配
    matched_campaigns = filter_by_targeting(active_campaigns, user)

    # 3. 预算检查 (Redis)
    valid_campaigns = check_budget(matched_campaigns)

    # 4. 频次控制 (Redis)
    valid_campaigns = apply_frequency_capping(valid_campaigns, user_id)

    # 5. 竞价排序
    ranked_ads = rank_by_bid(valid_campaigns)

    # 6. 返回最高价广告
    return ranked_ads[0] if ranked_ads else None
```

## Redis数据结构

### 预算控制
```
# 每日预算
Key: ad:budget:daily:{campaign_id}:{date}
Type: STRING
Value: 已花费金额

# 检查 & 更新
EVAL script budget_check 1 campaign_id date cost
```

### 频次控制
```
# 用户看过某广告的次数
Key: ad:freq:{user_id}:{campaign_id}:{date}
Type: STRING
Value: 展示次数
Expire: 86400
```

### 广告缓存
```
# 活跃广告缓存
Key: ad:active:{position_id}
Type: LIST
TTL: 300

# 热门创意缓存
Key: ad:creatives:hot
Type: ZSET (score = ctr)
```

## 反作弊

### 点击率异常检测
```python
# 计算点击率
ctr = clicks / impressions

# 异常判断
if ctr > threshold:  # e.g., 5%
    # 进一步检查
    if is_suspicious(user_events):
        mark_as_fraud()
```

### IP限流
```
# 同一IP短时间内多次点击
Key: ad:ratelimit:click:{ip}:{creative_id}
Limit: 10 clicks / minute
```

### 设备指纹
```python
# 检测虚假流量
fingerprint = generate_fingerprint(user_agent, ip, canvas, webgl)
if fingerprint in known_bots:
    reject()
```

## Kafka事件

```yaml
Topics:
  - wenxinblog.ad.impression  # 曝光事件
  - wenxinblog.ad.click       # 点击事件
  - wenxinblog.ad.conversion  # 转化事件

Event Format:
{
  "eventId": "uuid",
  "campaignId": "uuid",
  "creativeId": "uuid",
  "userId": "uuid",
  "eventType": "CLICK",
  "context": { },
  "timestamp": "2024-01-01T00:00:00Z"
}

Consumers:
  - ad-service: 计费、统计
  - recommendation-service: 用户兴趣更新
```

## 环境变量

```yaml
server:
  port: 8006

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5434/blog_db

ad:
  tracking:
    domain: https://ad.wenxinblog.com
    click-window: 30s  # 点击归因窗口
  anti-fraud:
    ctr-threshold: 0.05
    rate-limit: 10/minute
  budget:
    check-interval: 60s
    overspend-margin: 0.05  # 允许超支5%
```

## 运行

```bash
cd services/ad-service
mvn spring-boot:run
```
