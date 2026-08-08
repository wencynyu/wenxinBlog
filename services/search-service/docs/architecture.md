# Search Service

搜索服务 - 负责全文搜索、智能补全、搜索分析

> ⚠️ 本文件为早期设计稿，部分内容（端口、事件 topic 模型）已过时。**权威现状见 `docs/backend/search-service.md`**。搜索引擎已从 OpenSearch 迁移至 **Elasticsearch 9.3.8**，服务端口 **8005**。

## 功能

- 全文搜索 (博文、用户)
- 搜索建议/自动补全
- 搜索结果高亮
- 搜索历史记录
- 热门搜索词
- 搜索分析 (点击率、转化率)

## 技术栈

- Java 25
- Spring Boot 4.0.4 (WebFlux)
- Elasticsearch 9.3.8
- Kafka (监听博文变更事件)
- Redis (缓存搜索结果)

## Elasticsearch 索引

### blog_index (博文索引)

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_max_word_synonym": {
          "type": "custom",
          "tokenizer": "ik_max_word",
          "filter": ["lowercase", "synonym_filter"]
        }
      },
      "filter": {
        "synonym_filter": {
          "type": "synonym",
          "synonyms_path": "synonyms.txt"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "title": {
        "type": "text",
        "analyzer": "ik_max_word_synonym",
        "search_analyzer": "ik_smart",
        "fields": {
          "keyword": { "type": "keyword" },
          "suggest": { "type": "completion" }
        }
      },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word_synonym",
        "search_analyzer": "ik_smart"
      },
      "summary": {
        "type": "text",
        "analyzer": "ik_max_word"
      },
      "author": {
        "properties": {
          "id": { "type": "keyword" },
          "username": { "type": "keyword" },
          "displayName": { "type": "text" }
        }
      },
      "tags": { "type": "keyword" },
      "category": { "type": "keyword" },
      "status": { "type": "keyword" },
      "viewCount": { "type": "integer" },
      "likeCount": { "type": "integer" },
      "commentCount": { "type": "integer" },
      "createdAt": { "type": "date" },
      "updatedAt": { "type": "date" },
      "publishedAt": { "type": "date" },
      "coverImage": { "type": "keyword" }
    }
  }
}
```

### user_index (用户索引)

```json
{
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "username": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": { "type": "keyword" },
          "suggest": { "type": "completion" }
        }
      },
      "displayName": {
        "type": "text",
        "analyzer": "ik_max_word",
        "fields": {
          "suggest": { "type": "completion" }
        }
      },
      "bio": { "type": "text", "analyzer": "ik_max_word" },
      "followersCount": { "type": "integer" },
      "postCount": { "type": "integer" },
      "createdAt": { "type": "date" }
    }
  }
}
```

## API

### 博文搜索

```
GET    /api/v1/search/blog?q=xxx&page=1&pageSize=20
       ?sort=latest | popular | relevant
       &tags=tag1,tag2
       &category=tech
       &dateFrom=2024-01-01
       &dateTo=2024-12-31

Response:
{
  "results": [
    {
      "id": "uuid",
      "title": "博文标题",
      "summary": "摘要...",
      "highlights": {
        "title": ["高亮<em>关键词</em>"],
        "content": ["内容片段...<em>关键词</em>..."]
      },
      "author": { },
      "stats": { },
      "score": 2.5
    }
  ],
  "total": 100,
  "aggregations": {
    "tags": { "Java": 45, "Go": 32 },
    "categories": { "技术": 78 }
  }
}
```

### 用户搜索

```
GET    /api/v1/search/user?q=xxx&page=1&pageSize=20
```

### 搜索建议

```
GET    /api/v1/search/suggest?q=jav&type=blog|user

Response:
{
  "suggestions": [
    { "text": "Java", "type": "tag" },
    { "text": "JavaScript", "type": "tag" },
    { "text": "Java并发编程实战", "type": "blog" }
  ]
}
```

### 搜索历史

```
GET    /api/v1/search/history        - 获取搜索历史
DELETE /api/v1/search/history        - 清空搜索历史
```

### 热门搜索

```
GET    /api/v1/search/trending       - 热门搜索词

Response:
{
  "daily": ["ChatGPT", "Java 21", "微服务"],
  "weekly": ["Spring Boot", "React", "AI"],
  "monthly": ["架构设计", "性能优化"]
}
```

## Kafka事件监听

### 博文事件

```yaml
Topics:
  - wenxinblog.blog.created  -> 创建索引
  - wenxinblog.blog.updated  -> 更新索引
  - wenxinblog.blog.deleted  -> 删除索引

Group: search-service
```

### 用户事件

```yaml
Topics:
  - wenxinblog.user.registered -> 创建用户索引
  - wenxinblog.user.updated    -> 更新用户索引

Group: search-service-user
```

## Redis缓存设计

### 搜索结果缓存

```
Key: search:blog:{query_hash}
Type: JSON
TTL: 300 (5分钟)
Value: { results, total, aggregations }
```

### 热门搜索词缓存

```
Key: search:trending:daily
Key: search:trending:weekly
Key: search:trending:monthly
Type: LIST
TTL: 3600
```

### 搜索历史

```
Key: search:history:{userId}
Type: LIST
TTL: 2592000 (30天)
Max Length: 50
```

## 搜索相关性配置

### BM25参数

```yaml
search:
  similarity:
    default:
      type: BM25
      k1: 1.2
      b: 0.75
```

### 字段权重

```yaml
search:
  fields:
    title:
      boost: 2.0
    summary:
      boost: 1.5
    content:
      boost: 1.0
    tags:
      boost: 1.8
```

## 环境变量

```yaml
server:
  port: 8005

spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: search-service

elasticsearch:
  uris: http://localhost:9200
  username: ${ELASTICSEARCH_USERNAME:}
  password: ${ELASTICSEARCH_PASSWORD:}
  index:
    blog: wenxinblog-blog
    user: wenxinblog-user

search:
  max-results: 100
  default-page-size: 20
  highlight:
    enabled: true
    fragment-size: 150
    number-of-fragments: 3
```

## 运行

```bash
cd services/search-service
mvn spring-boot:run
```

## 索引管理

```bash
# 创建索引
curl -X PUT http://localhost:9200/wenxinblog-blog -d @blog-index.json

# 重建索引
curl -X POST http://localhost:9200/wenxinblog-blog/_reindex -d '{
  "source": { "index": "wenxinblog-blog-old" },
  "dest": { "index": "wenxinblog-blog" }
}'
```
