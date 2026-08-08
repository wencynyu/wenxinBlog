# Kafka 事件定义

## 事件规范

所有事件格式遵循 CloudEvents 规范:

```json
{
  "specversion": "1.0",
  "type": "wenxinblog.blog.created",
  "source": "/blog-service",
  "id": "unique-event-id",
  "time": "2024-01-01T00:00:00Z",
  "datacontenttype": "application/json",
  "data": {}
}
```

## 博文事件

Topic: `wenxinblog.blog`

### 博文创建

```json
{
  "type": "wenxinblog.blog.created",
  "data": {
    "postId": "uuid",
    "authorId": "uuid",
    "title": "string",
    "tags": ["tag1"],
    "publishedAt": "2024-01-01T00:00:00Z"
  }
}
```

### 博文更新

```json
{
  "type": "wenxinblog.blog.updated",
  "data": {
    "postId": "uuid",
    "authorId": "uuid",
    "changes": ["title", "content"]
  }
}
```

### 博文删除

```json
{
  "type": "wenxinblog.blog.deleted",
  "data": {
    "postId": "uuid",
    "authorId": "uuid"
  }
}
```

## 用户事件

Topic: `wenxinblog.user`

### 用户注册

```json
{
  "type": "wenxinblog.user.registered",
  "data": {
    "userId": "uuid",
    "username": "string",
    "email": "string",
    "registeredAt": "2024-01-01T00:00:00Z"
  }
}
```

### 关注用户

```json
{
  "type": "wenxinblog.user.followed",
  "data": {
    "followerId": "uuid",
    "followingId": "uuid",
    "followedAt": "2024-01-01T00:00:00Z"
  }
}
```

## 事件消费服务

| Topic           | 消费者                 | 用途         |
| --------------- | ---------------------- | ------------ |
| wenxinblog.blog | search-service         | 更新搜索索引 |
| wenxinblog.blog | recommendation-service | 更新推荐模型 |
| wenxinblog.user | recommendation-service | 更新推荐模型 |
| wenxinblog.blog | ad-service             | 广告匹配     |
| wenxinblog.user | ad-service             | 用户画像     |

## 消息骨干

项目统一使用 **Kafka** 作为唯一消息骨干（**未引入 RabbitMQ**）——事件流、异步处理、访问日志均走 Kafka。实际 topic 与生产/消费者见 `docs/api/events.md`（权威）。
