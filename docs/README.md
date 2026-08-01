# WenxinBlog 开发文档

> 最近更新：2026-08-02（对照实际代码核对）

## 文档结构

```
docs/
├── README.md                    # 本文档
├── architecture/                # 架构设计
│   ├── overview.md              # 系统架构总览
│   ├── microservices.md         # 微服务设计
│   └── security.md              # 安全设计
│
├── backend/                     # 后端文档
│   ├── auth-service.md          # 认证服务
│   ├── user-service.md          # 用户服务
│   ├── blog-service.md          # 博文服务
│   ├── content-service.md       # 内容服务
│   ├── search-service.md        # 搜索服务
│   ├── recommendation-service.md # 推荐服务
│   ├── ad-service.md            # 广告服务
│   └── gateway.md               # API网关
│
├── frontend/                    # 前端文档
│   ├── web.md                   # Web端 (Next.js)
│   └── mobile.md                # iOS端 (React Native)
│
├── api/                         # API文档
│   ├── rest-api.md              # REST API规范
│   └── events.md                # 事件定义(Kafka)
│
└── infrastructure/              # 基础设施
    ├── database.md              # 数据库设计
    ├── deployment.md            # 部署指南
    └── monitoring.md            # 监控告警
```

## 快速导航

### 我想了解...

**系统架构**

- [系统架构总览](architecture/overview.md) - 整体架构图和技术栈
- [微服务设计](architecture/microservices.md) - 服务划分和通信
- [安全设计](architecture/security.md) - 认证授权和安全防护

**后端服务**

- [认证服务](backend/auth-service.md) - OAuth2、JWT、会话管理
- [用户服务](backend/user-service.md) - 用户资料、关注关系
- [博文服务](backend/blog-service.md) - 博文CRUD、评论系统
- [内容服务](backend/content-service.md) - 图片视频上传处理
- [搜索服务](backend/search-service.md) - Elasticsearch全文搜索
- [推荐服务](backend/recommendation-service.md) - 个性化推荐算法
- [广告服务](backend/ad-service.md) - 广告投放计费
- [API网关](backend/gateway.md) - 路由、限流、熔断

**前端开发**

- [Web端](frontend/web.md) - Next.js 14 + Semi-Design（主力实现）
- [iOS端](frontend/mobile.md) - React Native + Expo（⚠️ 早期脚手架 / POC，大量功能未实现，详见该文档）

**API接口**

- [REST API](api/rest-api.md) - 接口规范和示例
- [Kafka事件](api/events.md) - 事件流定义

**基础设施**

- [数据库设计](infrastructure/database.md) - 表结构和索引
- [部署指南](infrastructure/deployment.md) - 容器化部署
- [监控告警](infrastructure/monitoring.md) - Prometheus + Grafana

## 技术栈总览

| 层级     | 技术选型                                               |
| -------- | ------------------------------------------------------ |
| 前端Web  | Next.js 14 + Semi-Design（主力实现）                   |
| 前端iOS  | React Native 0.74 + Expo 51（⚠️ 脚手架 / POC，未完成） |
| 后端认证 | Go 1.23 + Fiber 2.52                                   |
| 后端业务 | Java 25 + Spring Boot 4.0                              |
| 网关     | Spring Cloud Gateway                                   |
| 数据库   | PostgreSQL 15                                          |
| 缓存     | Redis 7                                                |
| 搜索     | Elasticsearch 9.3.8                                    |
| 向量     | Milvus 2.6                                             |
| 消息队列 | Kafka（唯一消息骨干，RabbitMQ 已移除）                 |
| 对象存储 | 阿里云OSS/MinIO                                        |

## 开发指南

### 1. 环境准备

```bash
# 克隆项目
git clone https://github.com/wenxinblog/wenxinblog.git
cd wenxinblog

# 启动基础设施
docker-compose up -d

# 验证服务
curl http://localhost:9200   # Elasticsearch
curl http://localhost:9091/healthz  # Milvus
```

### 2. 后端开发

```bash
# Go服务
cd services/auth-service
go mod download
go run cmd/server/main.go

# Java服务
cd services/blog-service
mvn spring-boot:run
```

### 3. 前端开发

```bash
# Web端（主力实现）
cd web
npm install
npm run dev

# iOS端（⚠️ 仅早期脚手架，依赖与页面均不完整，详见 frontend/mobile.md）
cd mobile
npm install
npm start
# 按 'i' 打开iOS模拟器（需补齐缺失依赖与原生环境）
```

## 文档规范

### 新增服务文档

1. 在 `docs/backend/` 创建 `<service-name>.md`
2. 包含以下章节:
   - 功能概述
   - 技术栈
   - 数据库设计
   - API接口
   - 环境配置
   - 运行指南

### 更新API文档

1. 新增API: 更新 `api/rest-api.md`
2. 新增事件: 更新 `api/events.md`
3. 保持示例代码可运行

## 贡献指南

### 文档贡献

1. Fork项目
2. 创建文档分支: `git checkout -b docs/xxx`
3. 更新文档
4. 提交PR: `docs: 添加xxx文档`

### 文档审查

- 内容准确性
- 示例代码可运行
- 格式规范
- 链接有效

## 联系方式

- 文档问题: 提交Issue
- 技术讨论: GitHub Discussions
