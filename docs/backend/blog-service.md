# Blog Service

博文服务 - 负责博文CRUD、标签分类、评论管理

## 功能

- 博文发布/编辑/删除
- Markdown支持
- 标签/分类管理
- 评论系统
- 点赞/收藏
- 草稿箱

## 技术栈

- Java 25
- Spring Boot 4.0.4 (WebFlux + R2DBC)
- PostgreSQL (blog_db)
- Redis (缓存)

## 数据库 (blog_db)

### posts 表
```sql
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES auth_db.users(id),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    summary VARCHAR(500),
    cover_image VARCHAR(500),
    status VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, ARCHIVED
    view_count BIGINT DEFAULT 0,
    like_count BIGINT DEFAULT 0,
    comment_count BIGINT DEFAULT 0,
    is_top BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
);
```

### tags 表
```sql
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    slug VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    post_count INT DEFAULT 0
);
```

### comments 表
```sql
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES auth_db.users(id),
    parent_id UUID REFERENCES comments(id),
    content TEXT NOT NULL,
    like_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## API

```
POST   /api/v1/posts              - 创建博文
GET    /api/v1/posts              - 博文列表
GET    /api/v1/posts/:id          - 获取博文
PUT    /api/v1/posts/:id          - 更新博文
DELETE /api/v1/posts/:id          - 删除博文
POST   /api/v1/posts/:id/publish  - 发布博文
GET    /api/v1/posts/:id/comments - 获取评论
POST   /api/v1/posts/:id/comments - 发表评论
```

## 环境变量

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5434/blog_db
    username: postgres
    password: postgres
```

## 运行

```bash
cd services/blog-service
mvn spring-boot:run
```

## 迁移

```bash
flyway -url=jdbc:postgresql://localhost:5434/blog_db migrate
```
