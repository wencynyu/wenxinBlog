# REST API 规范

## 基础规范

### 请求格式

- Base URL: `http://localhost:8080/api/v1`
- Content-Type: `application/json`
- 认证: `Authorization: Bearer <token>`

### 响应格式

成功响应:
```json
{
  "success": true,
  "data": { }
}
```

错误响应:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "错误描述",
    "details": { }
  }
}
```

### HTTP状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 429 | 请求过于频繁 |
| 500 | 服务器错误 |

## 认证相关

### 注册
```
POST /auth/register
Content-Type: application/json

{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

### 登录
```
POST /auth/login
Content-Type: application/json

{
  "email": "string",
  "password": "string"
}

Response:
{
  "token": "jwt-token",
  "refreshToken": "refresh-token",
  "user": { }
}
```

### OAuth授权
```
GET /auth/oauth/{provider}?redirect_uri=xxx&state=xxx
```

## 博文相关

### 创建博文
```
POST /posts
Authorization: Bearer <token>

{
  "title": "string",
  "content": "markdown",
  "summary": "string",
  "coverImage": "url",
  "tags": ["tag1", "tag2"],
  "status": "DRAFT" | "PUBLISHED"
}
```

### 获取博文列表
```
GET /posts?page=1&pageSize=20&tag=xxx&sort=latest | popular
```

### 获取博文详情
```
GET /posts/:id
```

## 用户相关

### 获取用户信息
```
GET /users/:id
```

### 更新用户信息
```
PUT /users/:id
Authorization: Bearer <token>

{
  "displayName": "string",
  "bio": "string",
  "website": "string"
}
```

### 关注/取关
```
POST /users/:id/follow
DELETE /users/:id/follow
```

## 分页规范

```
GET /resource?page=1&pageSize=20

Response:
{
  "data": [ ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 100,
    "totalPages": 5
  }
}
```
