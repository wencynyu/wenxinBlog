# WenxinBlog

一个支持文本、图片、视频的现代化博文平台，采用微服务架构设计。

## 项目结构

```
wenxinBlog/
├── DESIGN.md           # 完整设计文档
├── docker-compose.yml  # 本地开发环境
├── proj-desc.md        # 项目需求描述
│
├── services/           # 后端微服务
│   ├── auth-service/      # Go + Fiber (认证服务)
│   ├── user-service/      # Go + Fiber (用户服务)
│   ├── blog-service/      # Java + Spring Boot (博文服务)
│   ├── content-service/   # Java + Spring Boot (内容服务)
│   ├── search-service/    # Java + Spring Boot (搜索服务)
│   ├── recommendation-service/ # Java + Spring Boot (推荐服务)
│   ├── ad-service/        # Java + Spring Boot (广告服务)
│   └── gateway/           # Spring Cloud Gateway (API网关)
│
├── web/               # Web前端 (Next.js 14 + Semi-Design)
├── mobile/            # iOS客户端 (React Native + Expo)
├── docs/              # 开发文档
│   ├── backend/       # 后端服务文档
│   ├── frontend/      # 前端文档
│   ├── api/           # API文档
│   └── infrastructure/# 基础设施文档
│
└── README.md          # 本文档
```

## 技术栈

### 后端
| 服务 | 语言 | 框架 | 数据库 |
|------|------|------|--------|
| auth-service | Go 1.23 | Fiber v2.52 | PostgreSQL (auth_db) |
| user-service | Go 1.23 | Fiber v2.52 | PostgreSQL (user_db) |
| blog-service | Java 25 | Spring Boot 4.0 | PostgreSQL (blog_db) |
| content-service | Java 25 | Spring Boot 4.0 | PostgreSQL + OSS |
| search-service | Java 25 | Spring Boot 4.0 | OpenSearch |
| recommendation-service | Java 25 | Spring Boot 4.0 | Milvus |
| ad-service | Java 25 | Spring Boot 4.0 | PostgreSQL |
| gateway | Java 25 | Spring Cloud Gateway | - |

### 前端
| 平台 | 技术栈 |
|------|--------|
| Web | Next.js 14 + Semi-Design |
| iOS | React Native 0.74 + Expo 51 |

### 基础设施
| 组件 | 选型 |
|------|------|
| 数据库 | PostgreSQL 15 |
| 缓存 | Redis 7 |
| 搜索 | OpenSearch 2.11 |
| 向量 | Milvus 2.6 |
| 消息队列 | RabbitMQ + Kafka |
| 对象存储 | MinIO (本地) / 阿里云OSS (生产) |

## 快速开始

### 1. 启动基础设施

```bash
docker-compose up -d
```

这将启动：
- PostgreSQL x3 (auth_db, user_db, blog_db)
- Redis
- OpenSearch + Dashboards
- RabbitMQ + Management UI
- Kafka + Kafka UI
- Milvus + MinIO

### 2. 启动后端服务

```bash
# Go服务
cd services/auth-service && go run cmd/server/main.go
cd services/user-service && go run cmd/server/main.go

# Java服务
cd services/blog-service && mvn spring-boot:run
cd services/content-service && mvn spring-boot:run
# ... 其他服务
```

### 3. 启动前端

```bash
# Web端
cd web && npm install && npm run dev

# iOS端
cd mobile && npm install && npm start
```

### 4. 访问

| 服务 | 地址 |
|------|------|
| Web前端 | http://localhost:3000 |
| API网关 | http://localhost:8080 |
| OpenSearch | http://localhost:9200 |
| RabbitMQ管理 | http://localhost:15672 (guest/guest) |
| Kafka UI | http://localhost:8085 |
| MinIO控制台 | http://localhost:9001 (minioadmin/minioadmin) |

## 功能特性

- 📝 **博文管理** - 支持Markdown、标签分类、草稿箱
- 🔍 **全文搜索** - OpenSearch + IK分词
- 🎯 **智能推荐** - Milvus向量搜索 + 协同过滤
- 📷 **多媒体支持** - 图片压缩、视频转码、CDN分发
- 👤 **用户系统** - OAuth2登录、关注关系、个人主页
- 💰 **广告系统** - 程序化投放、计费统计
- 🔐 **权限管理** - RBAC角色继承
- 📱 **跨端支持** - Web + iOS优先，Android/visionOS后续

## 开发文档

- [系统设计](DESIGN.md) - 完整架构设计
- [后端服务](docs/backend/) - 各服务详细文档
- [API文档](docs/api/) - REST API规范
- [部署指南](docs/infrastructure/deployment.md) - 部署流程

## 许可证

MIT License
