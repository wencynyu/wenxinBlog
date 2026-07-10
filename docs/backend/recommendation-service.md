# Recommendation Service

推荐服务 - 负责个性化推荐、相关内容推荐

> ⚠️ **实现状态：占位（Placeholder）**
>
> 当前 Milvus 向量检索尚未接入：`MilvusService` 是占位实现，所有方法返回空并打印 WARN 日志，**不会静默返回伪造数据**。
> 当无真实结果时，推荐流 / 相关推荐 / 趋势 / "可能认识的人" 会返回**演示数据**，并在日志中以 `WARN ... DEMO ...` 显式标注 —— **不应用于生产**。
> 完整实现（embedding 生成、向量入库、ANN 检索、协同过滤、真实趋势信号）是独立的后续任务。
>
> 技术栈已由设计阶段的 **Python + FastAPI** 调整为 **Java 25 + Spring Boot 4**，与实际代码一致（端口 8006）。

## 功能

- 首页推荐流 (基于协同过滤 + 内容相似度)
- 相关博文推荐
- 可能认识的人推荐
- 热门趋势推荐
- 兴趣标签推荐
- A/B测试支持

## 技术栈

- Java 25
- Spring Boot 4.0.4 (WebFlux)
- Milvus 2.6 (向量搜索)
- Kafka (用户行为事件)
- Redis (缓存推荐结果)

## 推荐算法

### 1. 协同过滤 (User-based & Item-based)

```python
# 用户相似度计算
similarity(user1, user2) = cosine(interaction_vectors)

# 物品相似度计算
similarity(item1, item2) = jaccard(users_who_liked_item1, users_who_liked_item2)
```

### 2. 向量相似度 (Content-based)

使用Milvus存储博文embedding:

```
博文 -> Embedding模型 (Sentence-BERT) -> 向量 (768维)
用户兴趣 -> 用户交互过的博文向量平均 -> 用户向量
推荐 -> TopK相似博文
```

### 3. 混合推荐

```python
score = α * collaborative_score + β * content_score + γ * popularity_score

# 动态权重调整
α, β, γ 根据用户类型、时段、场景动态调整
```

## Milvus集合设计

### blog_embeddings (博文向量)

```python
Collection: blog_embeddings
Fields:
  - id: VARCHAR(36) (主键)
  - vector: FLOAT_VECTOR (768维) - 文本embedding
  - tags: VARCHAR(500) - 标签 (用于过滤)
  - category: VARCHAR(50) - 分类
  - created_at: INT64 - 时间戳
  - view_count: INT - 浏览数

Index:
  - IVF_FLAT 或 HNSW (用于近似搜索)

Parameters:
  - metric_type: COSINE
  - nlist: 128
```

### user_embeddings (用户兴趣向量)

```python
Collection: user_embeddings
Fields:
  - user_id: VARCHAR(36)
  - vector: FLOAT_VECTOR (768维)
  - updated_at: INT64
  - interaction_count: INT

Update Strategy:
  - 每次用户产生交互行为后异步更新
  - 取最近100次交互的博文向量加权平均
```

## API

### 推荐流

```
GET    /api/v1/recommend/feed?page=1&pageSize=20
       ?type=for_you | following | trending

Response:
{
  "items": [
    {
      "postId": "uuid",
      "score": 0.95,
      "reason": "基于你的兴趣",
      "type": "CONTENT_BASED"
    }
  ],
  "refreshId": "xxx"  // 用于去重和刷新
}
```

### 相关博文推荐

```
GET    /api/v1/recommend/related/:postId?limit=5

Response:
{
  "recommendations": [
    {
      "postId": "uuid",
      "title": "相关博文",
      "similarity": 0.87,
      "reason": "相似内容"
    }
  ]
}
```

### 用户推荐

```
GET    /api/v1/recommend/users?limit=10

Response:
{
  "users": [
    {
      "userId": "uuid",
      "reason": "你们都关注了xxx",
      "mutualFollowers": 5
    }
  ]
}
```

### 趋势推荐

```
GET    /api/v1/recommend/trending?period=24h|7d|30d

Response:
{
  "posts": [ ],
  "tags": [ ],
  "users": [ ]
}
```

### 兴趣标签

```
GET    /api/v1/recommend/interests

Response:
{
  "tags": [
    { "name": "Java", "relevance": 0.92 },
    { "name": "架构设计", "relevance": 0.85 }
  ]
}
```

## Kafka事件监听

### 用户行为事件

```yaml
Topics:
  - wenxinblog.user.view      # 浏览博文
  - wenxinblog.user.like      # 点赞
  - wenxinblog.user.comment   # 评论
  - wenxinblog.user.follow    # 关注
  - wenxinblog.user.share     # 分享

Event Format:
{
  "userId": "uuid",
  "itemId": "uuid",
  "itemType": "POST" | "USER",
  "action": "VIEW" | "LIKE" | "COMMENT",
  "timestamp": "2024-01-01T00:00:00Z",
  "context": { }
}
```

## Redis缓存设计

### 推荐结果缓存

```
Key: recommend:feed:{userId}:{type}
Type: LIST
TTL: 600 (10分钟)
```

### 用户兴趣向量

```
Key: recommend:interests:{userId}
Type: HASH
Fields:
  - tags: JSON (标签及权重)
  - categories: JSON (分类及权重)
  - updated_at: TIMESTAMP
TTL: 86400
```

### 热门内容缓存

```
Key: recommend:trending:daily
Key: recommend:trending:weekly
Type: ZSET (有序集合)
Score: 热度分数
TTL: 3600
```

## 离线计算

### 批量任务 (定时)

```yaml
Schedule:
  - 每5分钟: 更新用户兴趣向量
  - 每小时: 计算相似用户/相似博文
  - 每天凌晨: 批量更新推荐结果、重排

Tasks: 1. 从Kafka消费用户行为数据
  2. 更新Milvus中的用户向量
  3. 计算协同过滤相似度矩阵
  4. 生成预推荐列表存入Redis
```

## 冷启动策略

### 新用户

```python
# 基于注册信息推荐
- 注册时选择的兴趣标签
- 地理位置
- 推荐热门内容

# 快速学习
- 前10次展示使用探索策略 (多样性)
- 收够交互数据后切换到个性化
```

### 新博文

```python
# 进入推荐池
- 质量分 = 作者粉丝数 * 0.3 + 内容质量分 * 0.7
- 初始推荐给作者的粉丝
- 收集反馈后调整
```

## A/B测试

```yaml
Experiments:
  - name: 'feed_algorithm_v2'
    traffic: 0.2 # 20%流量
    variants:
      - name: 'control'
        algorithm: 'collaborative_filtering_v1'
      - name: 'treatment'
        algorithm: 'hybrid_v2'

Metrics:
  - ctr (点击率)
  - dwell_time (停留时长)
  - engagement_rate (互动率)
```

## 环境变量

```yaml
server:
  port: 8005

spring:
  kafka:
    bootstrap-servers: localhost:9092

milvus:
  host: localhost
  port: 19530
  collections:
    blog-embeddings: blog_embeddings
    user-embeddings: user_embeddings

recommendation:
  feed-size: 20
  cache-ttl: 600
  cold-start:
    popular-items: 50
    exploration-ratio: 0.2
  algorithms:
    collaborative:
      weight: 0.4
      min-interactions: 5
    content-based:
      weight: 0.5
      similarity-threshold: 0.7
    popularity:
      weight: 0.1
      time-window: 24h
```

## 运行

```bash
cd services/recommendation-service
mvn spring-boot:run
```
