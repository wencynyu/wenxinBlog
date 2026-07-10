# WenxinBlog 博文平台 - 系统设计文档

> **文档版本**: 1.0
> **最后更新**: 2026-03-25
> **优先平台**: Web 端 + iOS 端

---

## 目录

1. [项目概述](#1-项目概述)
2. [系统架构](#2-系统架构)
3. [RBAC 权限架构](#3-rbac-权限架构)
4. [数据库设计](#4-数据库设计)
5. [缓存设计](#5-缓存设计)
6. [搜索与推荐](#6-搜索与推荐)
7. [消息队列设计](#7-消息队列设计)
8. [API 设计](#8-api-设计)
9. [前端架构](#9-前端架构)
10. [iOS 应用架构](#10-ios-应用架构)
11. [基础设施设计](#11-基础设施设计)
12. [安全设计](#12-安全设计)
13. [监控与运维](#13-监控与运维)

---

## 1. 项目概述

### 1.1 项目背景

WenxinBlog 是一个现代化的博文平台，支持图文视频内容，采用前后端分离的微服务架构，从设计初期就考虑 SEO、推荐系统和广告接入。

### 1.2 技术选型概览

| 层次 | 技术选择 | 说明 |
|------|----------|------|
| 前端 | Next.js 14 + Semi-Design | 飞书 Universe Design 风格，SSR 优化 SEO |
| iOS | React Native + Expo | 基于 Web 组件跨平台复用 |
| 后端 | Java 25 + Spring Boot 4 | 响应式栈 (WebFlux + R2DBC) |
| 认证服务 | Go + Fiber | 高性能认证服务 |
| 推荐服务 | Python + FastAPI | AI/向量推理场景 |
| 数据库 | PostgreSQL 15 | 主数据库 |
| 缓存 | Redis 7 | 会话、缓存、限流 |
| 搜索 | OpenSearch | 全文检索 |
| 向量库 | **Milvus** | 向量存储和相似度搜索 |
| 任务队列 | **RabbitMQ** | 异步任务、延迟任务 |
| 事件流 | **Kafka** | 事件流、数据管道 |
| 对象存储 | Alibaba Cloud OSS | 图片视频存储 |
| 云平台 | Alibaba Cloud | 阿里云部署 (AWS 备选) |
| 权限架构 | **RBAC** | 基于角色的访问控制 |
| IaC | Terraform | 基础设施即代码 |

### 1.3 平台优先级

| 平台 | 优先级 | 说明 |
|------|--------|------|
| Web | P0 | 核心平台，SSR SEO 优化 |
| iOS | P0 | 核心移动平台 |
| Android | P1 | 后续迭代 |
| visionOS | P2 | 未来探索 |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              客户端层                                    │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              共享组件库 (React + TypeScript)                    │   │
│  │  - UI 组件 (Semi-Design 风格)                                 │   │
│  │  - 业务组件 (博文卡片、评论、用户资料)                        │   │
│  │  - 状态管理 (Zustand + React Query)                          │   │
│  │  - API 客户端                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                    │                    │
│                    ┌─────────┴────┐      ┌──────┴────────┐            │
│                    ▼             ▼      ▼               ▼            │
│         ┌──────────────┐  ┌─────────────┐  ┌──────────────┐          │
│         │  Web 应用     │  │ iOS 应用    │  │Android应用   │          │
│         │ (Next.js)    │  │(Expo/RN)    │  │  (未来)      │          │
│         │ - SEO 优化   │  │ - 推送通知  │  │              │          │
│         │ - 数据埋点   │  │ - 离线缓存  │  │              │          │
│         │ - SSR 渲染   │  │ - 生物识别  │  │              │          │
│         └──────────────┘  └─────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        API 网关 (ALB/Kong)                               │
│  - 限流  │  - 负载均衡  │  - SSL 终止  │  - 路由分发                     │
└─────────────────────────────────────────────────────────────────────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              ▼                       ▼                       ▼
┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
│     认证服务        │   │     用户服务        │   │     博文服务        │
│   (Go + Fiber)      │   │  (Go + Fiber)       │   │ (Java + Spring)     │
│  - OAuth 2.0        │   │  - RBAC 权限管理    │   │  - 博文 CRUD        │
│  - SSO 单点登录     │   │  - 用户资料         │   │  - 评论系统         │
│  - 2FA (可选)       │   │  - 关注系统         │   │  - 搜索功能         │
│  - 推送令牌管理     │   │  - 消息推送         │   │  - 实时更新         │
│  - JWT 令牌         │   │                     │   │                     │
└─────────────────────┘   └─────────────────────┘   └─────────────────────┘
              │                       │                       │
              └───────────────────────┼───────────────────────┘
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          基础设施层                                      │
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────────────┤
│PostgreSQL│  Redis   │OpenSearch│  Milvus  │RabbitMQ+ │     Alibaba OSS   │
│ (RDS)    │ (缓存)   │  (搜索)  │ (向量)   │  Kafka   │    (对象存储)     │
└──────────┴──────────┴──────────┴──────────┴──────────┴──────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   消息消费者 (Python FastAPI)                            │
│  ┌─────────────────────────┐  ┌─────────────────────────┐               │
│  │   RabbitMQ 消费者        │  │    Kafka 消费者         │               │
│  │  - 邮件发送             │  │  - 用户行为分析         │               │
│  │  - 推送通知准备         │  │  - 实时推荐计算         │               │
│  │  - 内容审核             │  │  - 向量嵌入生成         │               │
│  │  - 定时任务             │  │  - 数据仪表盘更新       │               │
│  └─────────────────────────┘  └─────────────────────────┘               │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 微服务划分

| 服务名称 | 端口 | 语言 | 数据库 | 职责 |
|----------|------|------|--------|------|
| auth-service | 8081 | Go + Fiber | auth_db | 用户认证、授权、OAuth、SSO、2FA、推送令牌 |
| user-service | 8082 | Go + Fiber | user_db | 用户资料、设置、关注、RBAC 权限管理 |
| blog-service | 8080 | Java + Spring Boot 4 | blog_db | 博文管理、评论、搜索、实时更新 |
| recommendation-service | 8083 | Python + FastAPI | Milvus | 内容推荐、趋势检测、向量检索 |
| analytics-service | 8084 | Python + FastAPI | ClickHouse | 数据分析、用户行为追踪 |

### 2.3 消息队列职责划分

| 消息中间件 | 用途 | 特点 |
|-----------|------|------|
| **RabbitMQ** | 异步任务队列、可靠投递、延迟任务、推送通知 | 低延迟、确认机制、死信队列 |
| **Kafka** | 事件流处理、数据管道、日志收集 | 高吞吐、持久化、多消费者 |

---

## 3. RBAC 权限架构

### 3.1 权限模型

```
用户 (User) ──拥有──> 角色 (Role) ──拥有──> 权限 (Permission)
                        │
                        └──────继承──────> 父角色
```

### 3.2 角色定义

| 角色代码 | 角色名称 | 级别 | 继承关系 | 描述 |
|----------|----------|------|----------|------|
| `guest` | 访客 | 0 | - | 未登录用户，只读公开内容 |
| `user` | 普通用户 | 1 | guest | 已注册用户，可创建内容 |
| `author` | 作者 | 2 | user | 可发布内容，有编辑权限 |
| `moderator` | 版主/审核员 | 3 | author | 可审核内容，管理评论 |
| `admin` | 管理员 | 4 | moderator | 全部权限，系统管理 |

### 3.3 权限定义

| 权限代码 | 资源 | 操作 | 范围 | 描述 |
|----------|------|------|------|------|
| `post:create` | post | create | - | 创建博文 |
| `post:update:own` | post | update | own | 更新自己的博文 |
| `post:update:any` | post | update | any | 更新任意博文 |
| `post:delete:own` | post | delete | own | 删除自己的博文 |
| `post:delete:any` | post | delete | any | 删除任意博文 |
| `post:publish` | post | publish | - | 发布博文 |
| `post:feature` | post | feature | - | 设为精华 |
| `comment:create` | comment | create | - | 发表评论 |
| `comment:update:own` | comment | update | own | 更新自己的评论 |
| `comment:delete:own` | comment | delete | own | 删除自己的评论 |
| `comment:moderate` | comment | moderate | - | 审核评论 |
| `user:update:own` | user | update | own | 更新自己的资料 |
| `user:update:any` | user | update | any | 更新任意用户资料 |
| `user:ban` | user | ban | - | 封禁用户 |
| `user:assign_role` | user | assign_role | - | 分配角色 |
| `category:manage` | category | manage | - | 管理分类 |

### 3.4 角色-权限矩阵

| 权限 | guest | user | author | moderator | admin |
|------|-------|------|--------|-----------|-------|
| post:read | ✓ | ✓ | ✓ | ✓ | ✓ |
| post:create | - | ✓ | ✓ | ✓ | ✓ |
| post:publish | - | - | ✓ | ✓ | ✓ |
| post:update:own | - | ✓ | ✓ | ✓ | ✓ |
| post:update:any | - | - | - | ✓ | ✓ |
| post:delete:own | - | ✓ | ✓ | ✓ | ✓ |
| post:delete:any | - | - | - | ✓ | ✓ |
| post:feature | - | - | - | ✓ | ✓ |
| comment:moderate | - | - | - | ✓ | ✓ |
| user:ban | - | - | - | - | ✓ |
| user:assign_role | - | - | - | - | ✓ |

---

## 4. 数据库设计

### 4.1 auth_db (认证数据库)

#### 4.1.1 用户表 (users)

```sql
-- =====================================================
-- 表名: users
-- 描述: 用户表 - 存储用户核心认证信息
-- =====================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE
);

COMMENT ON TABLE users IS '用户表 - 存储用户核心认证信息';
COMMENT ON COLUMN users.id IS '用户唯一标识 - UUID格式，主键';
COMMENT ON COLUMN users.username IS '用户名 - 用于登录和显示，全局唯一，2-50字符';
COMMENT ON COLUMN users.email IS '邮箱地址 - 用于登录和通知，全局唯一，需符合邮箱格式';
COMMENT ON COLUMN users.password_hash IS '密码哈希 - 使用bcrypt(cost=12)加密存储，不存储明文密码';
COMMENT ON COLUMN users.email_verified IS '邮箱是否已验证 - true表示已验证，false表示待验证';
COMMENT ON COLUMN users.created_at IS '账户创建时间 - UTC时间戳';
COMMENT ON COLUMN users.updated_at IS '信息最后更新时间 - UTC时间戳';
COMMENT ON COLUMN users.last_login_at IS '最后登录时间 - 用于统计活跃用户';
COMMENT ON COLUMN users.is_active IS '账户是否激活 - false表示被禁用/封禁';

-- 索引
CREATE INDEX idx_users_email ON users(email) WHERE is_active = TRUE;
CREATE INDEX idx_users_username ON users(username) WHERE is_active = TRUE;
CREATE INDEX idx_users_created_at ON users(created_at DESC);
```

#### 4.1.2 OAuth 提供商表 (oauth_providers)

```sql
-- =====================================================
-- 表名: oauth_providers
-- 描述: OAuth提供商表 - 存储第三方登录绑定信息
-- =====================================================
CREATE TABLE oauth_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(provider, provider_user_id)
);

COMMENT ON TABLE oauth_providers IS 'OAuth提供商表 - 存储第三方登录绑定信息';
COMMENT ON COLUMN oauth_providers.id IS 'OAuth绑定记录ID - 主键';
COMMENT ON COLUMN oauth_providers.user_id IS '关联的用户ID - 外键关联users表';
COMMENT ON COLUMN oauth_providers.provider IS 'OAuth提供商名称 - 如google、github、apple、wechat、alipay';
COMMENT ON COLUMN oauth_providers.provider_user_id IS '提供商侧的用户ID - 第三方平台的用户唯一标识';
COMMENT ON COLUMN oauth_providers.access_token IS '访问令牌 - 用于调用第三方API，加密存储';
COMMENT ON COLUMN oauth_providers.refresh_token IS '刷新令牌 - 用于获取新的访问令牌，加密存储';
COMMENT ON COLUMN oauth_providers.expires_at IS '访问令牌过期时间 - UTC时间戳';
COMMENT ON COLUMN oauth_providers.created_at IS '绑定时间 - 首次绑定时间';
COMMENT ON COLUMN oauth_providers.updated_at IS '最后更新时间 - 令牌刷新时间';

-- 索引
CREATE INDEX idx_oauth_providers_user_id ON oauth_providers(user_id);
CREATE INDEX idx_oauth_providers_provider ON oauth_providers(provider);
```

#### 4.1.3 2FA 配置表 (user_2fa)

```sql
-- =====================================================
-- 表名: user_2fa
-- 描述: 双因素认证配置表 - 存储用户2FA设置（可选功能）
-- =====================================================
CREATE TABLE user_2fa (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    secret VARCHAR(255) NOT NULL,
    is_enabled BOOLEAN DEFAULT FALSE,
    backup_codes TEXT[],
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_2fa IS '双因素认证配置表 - 存储用户2FA设置';
COMMENT ON COLUMN user_2fa.user_id IS '用户ID - 主键，外键关联users表';
COMMENT ON COLUMN user_2fa.secret IS 'TOTP密钥 - Base32编码，用于生成6位验证码';
COMMENT ON COLUMN user_2fa.is_enabled IS '是否启用2FA - true表示已启用，登录时需要验证码';
COMMENT ON COLUMN user_2fa.backup_codes IS '备用恢复码 - 数组格式，共10个，每个一次性使用';
COMMENT ON COLUMN user_2fa.verified_at IS '验证时间 - 首次设置2FA的验证时间';
COMMENT ON COLUMN user_2fa.created_at IS '2FA配置创建时间';
COMMENT ON COLUMN user_2fa.updated_at IS '最后更新时间 - 重置备用码时更新';
```

#### 4.1.4 刷新令牌表 (refresh_tokens)

```sql
-- =====================================================
-- 表名: refresh_tokens
-- 描述: 刷新令牌表 - 存储JWT刷新令牌，用于无感刷新
-- =====================================================
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) UNIQUE NOT NULL,
    fingerprint VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE,
    used_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE refresh_tokens IS '刷新令牌表 - 存储JWT刷新令牌';
COMMENT ON COLUMN refresh_tokens.id IS '令牌记录ID - 主键';
COMMENT ON COLUMN refresh_tokens.user_id IS '关联的用户ID - 外键关联users表';
COMMENT ON COLUMN refresh_tokens.token IS '刷新令牌 - SHA256哈希存储，不可逆';
COMMENT ON COLUMN refresh_tokens.fingerprint IS '设备指纹 - 用于检测异常登录';
COMMENT ON COLUMN refresh_tokens.expires_at IS '令牌过期时间 - 通常为签发后7-30天';
COMMENT ON COLUMN refresh_tokens.created_at IS '令牌创建时间';
COMMENT ON COLUMN refresh_tokens.revoked_at IS '令牌撤销时间 - 用户登出时设置，表示令牌已失效';
COMMENT ON COLUMN refresh_tokens.used_at IS '最后使用时间 - 用于刷新令牌滚动更新';

-- 索引
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;
-- 定期清理过期令牌
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens(revoked_at) WHERE revoked_at IS NOT NULL;
```

#### 4.1.5 推送令牌表 (push_tokens)

```sql
-- =====================================================
-- 表名: push_tokens
-- 描述: 推送令牌表 - 存储跨平台推送设备令牌
-- =====================================================
CREATE TABLE push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    token TEXT NOT NULL,
    device_id VARCHAR(255),
    device_name VARCHAR(100),
    app_version VARCHAR(20),
    os_version VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, token)
);

COMMENT ON TABLE push_tokens IS '推送令牌表 - 存储跨平台推送设备令牌';
COMMENT ON COLUMN push_tokens.id IS '推送令牌记录ID - 主键';
COMMENT ON COLUMN push_tokens.user_id IS '关联的用户ID - 外键关联users表';
COMMENT ON COLUMN push_tokens.platform IS '平台类型 - ios(APNs)、android(FCM/HMS)、web(Web Push)';
COMMENT ON COLUMN push_tokens.token IS '推送令牌 - APNs token、FCM token等';
COMMENT ON COLUMN push_tokens.device_id IS '设备唯一标识 - 用于识别同一设备';
COMMENT ON COLUMN push_tokens.device_name IS '设备名称 - 如"iPhone 14 Pro"，用户可见';
COMMENT ON COLUMN push_tokens.app_version IS '应用版本 - 如"1.0.0"';
COMMENT ON COLUMN push_tokens.os_version IS '操作系统版本 - 如"iOS 17.0"';
COMMENT ON COLUMN push_tokens.is_active IS '是否有效 - 用户卸载应用或禁用通知时设为false';
COMMENT ON COLUMN push_tokens.created_at IS '令牌注册时间';
COMMENT ON COLUMN push_tokens.last_used_at IS '最后推送成功时间 - 用于清理无效令牌';

-- 索引
CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);
CREATE INDEX idx_push_tokens_platform ON push_tokens(platform);
CREATE INDEX idx_push_tokens_is_active ON push_tokens(is_active) WHERE is_active = TRUE;
```

---

### 4.2 user_db (用户数据库)

#### 4.2.1 角色表 (roles)

```sql
-- =====================================================
-- 表名: roles
-- 描述: 角色表 - RBAC角色定义，支持角色继承
-- =====================================================
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id INTEGER REFERENCES roles(id) ON DELETE SET NULL,
    level INTEGER DEFAULT 0 NOT NULL,
    is_system BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE roles IS '角色表 - RBAC角色定义，支持角色继承';
COMMENT ON COLUMN roles.id IS '角色ID - 主键，自增';
COMMENT ON COLUMN roles.code IS '角色代码 - 如guest、user、author、moderator、admin，唯一标识';
COMMENT ON COLUMN roles.name IS '角色名称 - 中文显示名称，如"普通用户"';
COMMENT ON COLUMN roles.description IS '角色描述 - 详细说明角色职责';
COMMENT ON COLUMN roles.parent_id IS '父角色ID - 用于角色继承，NULL表示顶级角色';
COMMENT ON COLUMN roles.level IS '角色级别 - 数字越大权限越高，用于权限比较';
COMMENT ON COLUMN roles.is_system IS '是否系统角色 - 系统角色不可删除，如admin、guest';

-- 初始化系统角色
INSERT INTO roles (code, name, description, parent_id, level, is_system) VALUES
('guest', '访客', '未登录用户，只读公开内容', NULL, 0, TRUE),
('user', '普通用户', '已注册用户，可创建内容', 1, 1, TRUE),
('author', '作者', '可发布内容，有编辑权限', 2, 2, TRUE),
('moderator', '版主', '可审核内容，管理评论', 3, 3, TRUE),
('admin', '管理员', '全部权限，系统管理', 4, 4, TRUE);
```

#### 4.2.2 权限表 (permissions)

```sql
-- =====================================================
-- 表名: permissions
-- 描述: 权限表 - RBAC权限定义
-- =====================================================
CREATE TABLE permissions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    scope VARCHAR(20),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE permissions IS '权限表 - RBAC权限定义';
COMMENT ON COLUMN permissions.id IS '权限ID - 主键，自增';
COMMENT ON COLUMN permissions.code IS '权限代码 - 如post:create、user:update:own，格式为资源:操作[:范围]';
COMMENT ON COLUMN permissions.name IS '权限名称 - 中文显示名称';
COMMENT ON COLUMN permissions.resource IS '资源类型 - 如post、user、comment、category';
COMMENT ON COLUMN permissions.action IS '操作类型 - 如create、read、update、delete、publish、moderate';
COMMENT ON COLUMN permissions.scope IS '权限范围 - own(自己的)、any(所有的)、NULL(不限制)';
COMMENT ON COLUMN permissions.description IS '权限描述 - 详细说明权限作用';

-- 初始化基础权限
INSERT INTO permissions (code, name, resource, action, scope, description) VALUES
-- 博文相关
('post:create', '创建博文', 'post', 'create', NULL, '创建新博文'),
('post:update:own', '更新自己的博文', 'post', 'update', 'own', '更新自己创建的博文'),
('post:update:any', '更新任意博文', 'post', 'update', 'any', '更新任意用户的博文'),
('post:delete:own', '删除自己的博文', 'post', 'delete', 'own', '删除自己创建的博文'),
('post:delete:any', '删除任意博文', 'post', 'delete', 'any', '删除任意用户的博文'),
('post:publish', '发布博文', 'post', 'publish', NULL, '将草稿发布为公开博文'),
('post:feature', '设为精华', 'post', 'feature', NULL, '将博文设为精华推荐'),
-- 评论相关
('comment:create', '发表评论', 'comment', 'create', NULL, '发表新评论'),
('comment:moderate', '审核评论', 'comment', 'moderate', NULL, '审核并通过/拒绝评论'),
-- 用户相关
('user:update:own', '更新自己的资料', 'user', 'update', 'own', '更新自己的用户资料'),
('user:update:any', '更新任意用户资料', 'user', 'update', 'any', '更新任意用户的资料'),
('user:ban', '封禁用户', 'user', 'ban', NULL, '封禁或解封用户'),
('user:assign_role', '分配角色', 'user', 'assign_role', NULL, '为用户分配或移除角色');
```

#### 4.2.3 角色权限关联表 (role_permissions)

```sql
-- =====================================================
-- 表名: role_permissions
-- 描述: 角色权限关联表 - 定义角色拥有的权限
-- =====================================================
CREATE TABLE role_permissions (
    role_id INTEGER REFERENCES roles(id) ON DELETE CASCADE,
    permission_id INTEGER REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID REFERENCES auth_db.users(id),
    PRIMARY KEY (role_id, permission_id)
);

COMMENT ON TABLE role_permissions IS '角色权限关联表 - 定义角色拥有的权限';
COMMENT ON COLUMN role_permissions.role_id IS '角色ID - 外键关联roles表';
COMMENT ON COLUMN role_permissions.permission_id IS '权限ID - 外键关联permissions表';
COMMENT ON COLUMN role_permissions.granted_at IS '授权时间 - 记录何时授权';
COMMENT ON COLUMN role_permissions.granted_by IS '授权人ID - 记录谁执行的授权操作，外键关联auth_db.users表';
```

#### 4.2.4 用户角色关联表 (user_roles)

```sql
-- =====================================================
-- 表名: user_roles
-- 描述: 用户角色关联表 - 定义用户拥有的角色
-- =====================================================
CREATE TABLE user_roles (
    user_id UUID REFERENCES auth_db.users(id) ON DELETE CASCADE,
    role_id INTEGER REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES auth_db.users(id),
    expires_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE user_roles IS '用户角色关联表 - 定义用户拥有的角色';
COMMENT ON COLUMN user_roles.user_id IS '用户ID - 外键关联auth_db.users表';
COMMENT ON COLUMN user_roles.role_id IS '角色ID - 外键关联roles表';
COMMENT ON COLUMN user_roles.assigned_at IS '分配时间 - 记录何时分配角色';
COMMENT ON COLUMN user_roles.assigned_by IS '分配人ID - 记录谁执行的角色分配，NULL表示系统自动分配';
COMMENT ON COLUMN user_roles.expires_at IS '角色过期时间 - NULL表示永久有效，可用于临时角色';

-- 索引
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_expires_at ON user_roles(expires_at) WHERE expires_at IS NOT NULL;
```

#### 4.2.5 用户资料表 (user_profiles)

```sql
-- =====================================================
-- 表名: user_profiles
-- 描述: 用户资料表 - 存储用户扩展信息
-- =====================================================
CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES auth_db.users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    bio TEXT,
    avatar_url VARCHAR(500),
    cover_url VARCHAR(500),
    location VARCHAR(100),
    website VARCHAR(255),
    birth_date DATE,
    gender VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_profiles IS '用户资料表 - 存储用户扩展信息';
COMMENT ON COLUMN user_profiles.user_id IS '用户ID - 主键，外键关联auth_db.users表';
COMMENT ON COLUMN user_profiles.display_name IS '显示名称 - 昵称，1-100字符';
COMMENT ON COLUMN user_profiles.bio IS '个人简介 - 用户自我介绍，最多500字符';
COMMENT ON COLUMN user_profiles.avatar_url IS '头像URL - 完整的图片访问地址';
COMMENT ON COLUMN user_profiles.cover_url IS '封面图URL - 用户主页封面图地址';
COMMENT ON COLUMN user_profiles.location IS '所在地 - 城市或地区';
COMMENT ON COLUMN user_profiles.website IS '个人网站 - 个人博客或主页链接';
COMMENT ON COLUMN user_profiles.birth_date IS '生日 - 仅显示年龄，不显示具体日期';
COMMENT ON COLUMN user_profiles.gender IS '性别 - male(男)、female(女)、other(其他)、prefer_not_to_say(不便透露)';
```

#### 4.2.6 用户设置表 (user_settings)

```sql
-- =====================================================
-- 表名: user_settings
-- 描述: 用户设置表 - 存储用户偏好设置
-- =====================================================
CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY REFERENCES auth_db.users(id) ON DELETE CASCADE,
    email_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    push_notifications BOOLEAN DEFAULT TRUE NOT NULL,
    profile_visibility VARCHAR(20) DEFAULT 'public' NOT NULL,
    message_privacy VARCHAR(20) DEFAULT 'everyone' NOT NULL,
    theme VARCHAR(20) DEFAULT 'auto' NOT NULL,
    language VARCHAR(10) DEFAULT 'zh-CN' NOT NULL,
    push_new_followers BOOLEAN DEFAULT TRUE NOT NULL,
    push_likes BOOLEAN DEFAULT TRUE NOT NULL,
    push_comments BOOLEAN DEFAULT TRUE NOT NULL,
    push_mentions BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_settings IS '用户设置表 - 存储用户偏好设置';
COMMENT ON COLUMN user_settings.user_id IS '用户ID - 主键，外键关联auth_db.users表';
COMMENT ON COLUMN user_settings.email_notifications IS '是否接收邮件通知 - 如新关注、评论等';
COMMENT ON COLUMN user_settings.push_notifications IS '是否接收推送通知 - 移动端推送总开关';
COMMENT ON COLUMN user_settings.profile_visibility IS '资料可见性 - public(公开)、followers(仅粉丝)、private(私密)';
COMMENT ON COLUMN user_settings.message_privacy IS '消息隐私 - everyone(所有人)、followers(仅粉丝)、following(互关)、none(关闭)';
COMMENT ON COLUMN user_settings.theme IS '主题设置 - light(浅色)、dark(深色)、auto(自动跟随系统)';
COMMENT ON COLUMN user_settings.language IS '语言设置 - zh-CN(简体中文)、en-US(英语)等';
COMMENT ON COLUMN user_settings.push_new_followers IS '新粉丝推送开关 - true表示推送新粉丝通知';
COMMENT ON COLUMN user_settings.push_likes IS '点赞推送开关 - true表示推送点赞通知';
COMMENT ON COLUMN user_settings.push_comments IS '评论推送开关 - true表示推送评论通知';
COMMENT ON COLUMN user_settings.push_mentions IS '@提醒推送开关 - true表示推送@提及通知';
```

#### 4.2.7 关注关系表 (follows)

```sql
-- =====================================================
-- 表名: follows
-- 描述: 关注关系表 - 存储用户之间的关注关系
-- =====================================================
CREATE TABLE follows (
    follower_id UUID REFERENCES auth_db.users(id) ON DELETE CASCADE,
    following_id UUID REFERENCES auth_db.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    CHECK (follower_id != following_id)
);

COMMENT ON TABLE follows IS '关注关系表 - 存储用户之间的关注关系';
COMMENT ON COLUMN follows.follower_id IS '关注者ID - 发起关注的用户';
COMMENT ON COLUMN follows.following_id IS '被关注者ID - 被关注的用户';
COMMENT ON COLUMN follows.created_at IS '关注时间 - 用于计算关注时长和关注历史';

-- 索引
CREATE INDEX idx_follows_follower_id ON follows(follower_id);
CREATE INDEX idx_follows_following_id ON follows(following_id);
CREATE INDEX idx_follows_created_at ON follows(created_at DESC);
```

#### 4.2.8 屏蔽用户表 (blocked_users)

```sql
-- =====================================================
-- 表名: blocked_users
-- 描述: 屏蔽用户表 - 存储用户屏蔽关系
-- =====================================================
CREATE TABLE blocked_users (
    blocker_id UUID REFERENCES auth_db.users(id) ON DELETE CASCADE,
    blocked_id UUID REFERENCES auth_db.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE
);

COMMENT ON TABLE blocked_users IS '屏蔽用户表 - 存储用户屏蔽关系';
COMMENT ON COLUMN blocked_users.blocker_id IS '屏蔽者ID - 执行屏蔽操作的用户';
COMMENT ON COLUMN blocked_users.blocked_id IS '被屏蔽者ID - 被屏蔽的用户';
COMMENT ON COLUMN blocked_users.created_at IS '屏蔽时间';
COMMENT ON COLUMN blocked_users.reason IS '屏蔽原因 - 可选，用于记录为何屏蔽';
COMMENT ON COLUMN blocked_users.expires_at IS '过期时间 - NULL表示永久屏蔽，可用于临时禁言';

-- 索引
CREATE INDEX idx_blocked_users_blocker_id ON blocked_users(blocker_id);
CREATE INDEX idx_blocked_users_blocked_id ON blocked_users(blocked_id);
```

---

### 4.3 blog_db (博文数据库)

#### 4.3.1 博文表 (posts)

```sql
-- =====================================================
-- 表名: posts
-- 描述: 博文表 - 存储文章内容
-- =====================================================
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES auth_db.users(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    slug VARCHAR(500) UNIQUE NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(20) DEFAULT 'markdown' NOT NULL,
    excerpt TEXT,
    featured_image_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'draft' NOT NULL,
    visibility VARCHAR(20) DEFAULT 'public' NOT NULL,
    allow_comments BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    meta_title VARCHAR(500),
    meta_description TEXT,
    meta_keywords VARCHAR(500),
    view_count INTEGER DEFAULT 0 NOT NULL,
    like_count INTEGER DEFAULT 0 NOT NULL,
    comment_count INTEGER DEFAULT 0 NOT NULL,
    vector_id VARCHAR(255)
);

COMMENT ON TABLE posts IS '博文表 - 存储文章内容';
COMMENT ON COLUMN posts.id IS '博文ID - UUID格式，主键';
COMMENT ON COLUMN posts.author_id IS '作者ID - 外键关联auth_db.users表';
COMMENT ON COLUMN posts.title IS '博文标题 - 1-500字符';
COMMENT ON COLUMN posts.slug IS 'URL别名 - 用于生成友好的URL，如"my-first-post"';
COMMENT ON COLUMN posts.content IS '博文内容 - 支持Markdown和富文本';
COMMENT ON COLUMN posts.content_type IS '内容类型 - markdown(Markdown)、richtext(富文本)';
COMMENT ON COLUMN posts.excerpt IS '摘要 - 用于列表显示，自动生成或手动输入';
COMMENT ON COLUMN posts.featured_image_url IS '特色图片URL - 文章封面图';
COMMENT ON COLUMN posts.status IS '发布状态 - draft(草稿)、published(已发布)、archived(归档)、pending_review(待审核)';
COMMENT ON COLUMN posts.visibility IS '可见性 - public(公开)、private(私密)、unlisted(未列出，仅链接可访问)';
COMMENT ON COLUMN posts.allow_comments IS '是否允许评论 - true表示允许评论';
COMMENT ON COLUMN posts.created_at IS '创建时间 - 首次创建时间';
COMMENT ON COLUMN posts.updated_at IS '最后更新时间 - 每次修改自动更新';
COMMENT ON COLUMN posts.published_at IS '发布时间 - status变更为published时设置';
COMMENT ON COLUMN posts.meta_title IS 'SEO标题 - 用于搜索引擎优化，默认使用title';
COMMENT ON COLUMN posts.meta_description IS 'SEO描述 - 搜索引擎结果显示的描述';
COMMENT ON COLUMN posts.meta_keywords IS 'SEO关键词 - 逗号分隔的关键词';
COMMENT ON COLUMN posts.view_count IS '浏览次数 - 缓存值，定期通过post_views聚合同步';
COMMENT ON COLUMN posts.like_count IS '点赞数 - 缓存值，实时更新';
COMMENT ON COLUMN posts.comment_count IS '评论数 - 缓存值，实时更新';
COMMENT ON COLUMN posts.vector_id IS '向量ID - Milvus中的向量ID，用于推荐系统';

-- 索引
CREATE INDEX idx_posts_author_id ON posts(author_id);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_visibility ON posts(visibility);
CREATE INDEX idx_posts_published_at ON posts(published_at DESC) WHERE status = 'published';
CREATE INDEX idx_posts_slug ON posts(slug);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);
-- 全文搜索索引
CREATE INDEX idx_posts_title_gin ON posts USING gin(to_tsvector('simple', title));
CREATE INDEX idx_posts_content_gin ON posts USING gin(to_tsvector('simple', content));
```

#### 4.3.2 标签表 (tags)

```sql
-- =====================================================
-- 表名: tags
-- 描述: 标签表 - 文章标签
-- =====================================================
CREATE TABLE tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    slug VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    post_count INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE tags IS '标签表 - 文章标签分类';
COMMENT ON COLUMN tags.id IS '标签ID - 主键，自增';
COMMENT ON COLUMN tags.name IS '标签名称 - 中文显示名称，如"技术"';
COMMENT ON COLUMN tags.slug IS 'URL别名 - 用于生成友好的URL，如"tech"';
COMMENT ON COLUMN tags.description IS '标签描述 - 详细说明标签用途';
COMMENT ON COLUMN tags.post_count IS '文章数量 - 缓存值，实时更新';
```

#### 4.3.3 文章标签关联表 (post_tags)

```sql
-- =====================================================
-- 表名: post_tags
-- 描述: 文章标签关联表
-- =====================================================
CREATE TABLE post_tags (
    post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
    tag_id INTEGER REFERENCES tags(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, tag_id)
);

COMMENT ON TABLE post_tags IS '文章标签关联表 - 多对多关系';
COMMENT ON COLUMN post_tags.post_id IS '博文ID - 外键关联posts表';
COMMENT ON COLUMN post_tags.tag_id IS '标签ID - 外键关联tags表';
COMMENT ON COLUMN post_tags.created_at IS '关联时间';

-- 索引
CREATE INDEX idx_post_tags_post_id ON post_tags(post_id);
CREATE INDEX idx_post_tags_tag_id ON post_tags(tag_id);
```

#### 4.3.4 分类表 (categories)

```sql
-- =====================================================
-- 表名: categories
-- 描述: 分类表 - 文章分类，支持多级分类
-- =====================================================
CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    parent_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
    icon VARCHAR(100),
    sort_order INTEGER DEFAULT 0 NOT NULL,
    post_count INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE categories IS '分类表 - 文章分类，支持多级分类';
COMMENT ON COLUMN categories.id IS '分类ID - 主键，自增';
COMMENT ON COLUMN categories.name IS '分类名称 - 中文显示名称';
COMMENT ON COLUMN categories.slug IS 'URL别名 - 用于生成友好的URL';
COMMENT ON COLUMN categories.description IS '分类描述 - 详细说明分类内容';
COMMENT ON COLUMN categories.parent_id IS '父分类ID - NULL表示顶级分类';
COMMENT ON COLUMN categories.icon IS '图标名称 - Semi-Design图标名';
COMMENT ON COLUMN categories.sort_order IS '排序顺序 - 数字越小越靠前';
COMMENT ON COLUMN categories.post_count IS '文章数量 - 缓存值，实时更新';

-- 索引
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_sort_order ON categories(sort_order);
```

#### 4.3.5 评论表 (comments)

```sql
-- =====================================================
-- 表名: comments
-- 描述: 评论表 - 支持嵌套回复
-- =====================================================
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID REFERENCES auth_db.users(id) ON DELETE SET NULL,
    parent_id UUID REFERENCES comments(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'pending' NOT NULL,
    path VARCHAR(1000),
    like_count INTEGER DEFAULT 0 NOT NULL,
    reply_count INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE comments IS '评论表 - 支持嵌套回复';
COMMENT ON COLUMN comments.id IS '评论ID - UUID格式，主键';
COMMENT ON COLUMN comments.post_id IS '博文ID - 外键关联posts表';
COMMENT ON COLUMN comments.author_id IS '评论者ID - 外键关联auth_db.users表，NULL表示已删除用户';
COMMENT ON COLUMN comments.parent_id IS '父评论ID - NULL表示顶层评论';
COMMENT ON COLUMN comments.content IS '评论内容 - 纯文本，支持部分Markdown';
COMMENT ON COLUMN comments.status IS '审核状态 - pending(待审核)、published(已发布)、rejected(已拒绝)、spam(垃圾)';
COMMENT ON COLUMN comments.path IS '评论路径 - 用于快速查询嵌套关系，如/1/2/3，代表1-2-3层级';
COMMENT ON COLUMN comments.like_count IS '点赞数 - 实时更新';
COMMENT ON COLUMN comments.reply_count IS '回复数 - 实时更新';
COMMENT ON COLUMN comments.created_at IS '评论时间';
COMMENT ON COLUMN comments.updated_at IS '最后编辑时间';

-- 索引
CREATE INDEX idx_comments_post_id ON comments(post_id);
CREATE INDEX idx_comments_author_id ON comments(author_id);
CREATE INDEX idx_comments_parent_id ON comments(parent_id);
CREATE INDEX idx_comments_status ON comments(status);
CREATE INDEX idx_comments_created_at ON comments(created_at DESC);
CREATE INDEX idx_comments_path ON comments USING gin(path gin_trgm_ops);
```

#### 4.3.6 媒体资源表 (media_assets)

```sql
-- =====================================================
-- 表名: media_assets
-- 描述: 媒体资源表 - 存储图片、视频、3D模型等文件信息
-- =====================================================
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id UUID REFERENCES auth_db.users(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255),
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_provider VARCHAR(50) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(500),
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    status VARCHAR(20) DEFAULT 'uploading' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE media_assets IS '媒体资源表 - 存储图片、视频、3D模型等文件信息';
COMMENT ON COLUMN media_assets.id IS '媒体资源ID - UUID格式，主键';
COMMENT ON COLUMN media_assets.uploader_id IS '上传者ID - 外键关联auth_db.users表';
COMMENT ON COLUMN media_assets.filename IS '存储文件名 - OSS中的文件名';
COMMENT ON COLUMN media_assets.original_filename IS '原始文件名 - 用户上传时的文件名';
COMMENT ON COLUMN media_assets.mime_type IS 'MIME类型 - 如image/jpeg、video/mp4、model/usdz';
COMMENT ON COLUMN media_assets.size_bytes IS '文件大小(字节) - 用于统计和限制';
COMMENT ON COLUMN media_assets.storage_provider IS '存储提供商 - oss(阿里云OSS)、local(本地存储)';
COMMENT ON COLUMN media_assets.storage_path IS '存储路径 - OSS中的完整路径';
COMMENT ON COLUMN media_assets.url IS '访问URL - CDN加速后的完整访问地址';
COMMENT ON COLUMN media_assets.thumbnail_url IS '缩略图URL - 图片或视频封面';
COMMENT ON COLUMN media_assets.width IS '图片/视频宽度(像素) - NULL表示非图片/视频';
COMMENT ON COLUMN media_assets.height IS '图片/视频高度(像素)';
COMMENT ON COLUMN media_assets.duration_seconds IS '视频时长(秒) - NULL表示非视频';
COMMENT ON COLUMN media_assets.status IS '处理状态 - uploading(上传中)、processing(处理中)、ready(就绪)、failed(失败)';

-- 索引
CREATE INDEX idx_media_assets_uploader_id ON media_assets(uploader_id);
CREATE INDEX idx_media_assets_status ON media_assets(status);
CREATE INDEX idx_media_assets_created_at ON media_assets(created_at DESC);
```

---

## 5. 缓存设计

### 5.1 Redis 数据结构

```redis
# =====================================================
# Redis 数据键设计规范
# =====================================================

# ------------------ 会话管理 ------------------
# TTL: 24小时
session:{session_id} -> Hash {
  user_id: UUID,              # 用户ID
  username: string,           # 用户名
  created_at: timestamp,      # 创建时间
  expires_at: timestamp,      # 过期时间
  ip: string,                 # IP地址
  user_agent: string          # 用户代理
}

# ------------------ 权限缓存 ------------------
# TTL: 5分钟
user:{user_id}:permissions -> Set [permission codes]
user:{user_id}:roles -> Set [role codes]

# ------------------ 用户缓存 ------------------
# TTL: 30分钟
user:{user_id}:profile -> Hash {
  display_name: string,
  bio: text,
  avatar_url: string,
  follower_count: int,
  following_count: int,
  post_count: int
}

# TTL: 1小时
user:{user_id}:followers -> Set (follower IDs)
user:{user_id}:following -> Set (following IDs)

# ------------------ 博文缓存 ------------------
# TTL: 10分钟
post:{post_id} -> Hash {
  id: UUID,
  title: string,
  content: text,
  author_id: UUID,
  view_count: int,
  like_count: int,
  comment_count: int
}

# TTL: 持久，定时更新
post:popular:hourly -> Sorted Set (score = 互动量, member = post_id)
post:popular:daily -> Sorted Set
post:popular:weekly -> Sorted Set

# ------------------ 标签/分类缓存 ------------------
# TTL: 1小时
tag:{tag_id}:posts -> Set (post IDs)
tag:name:{tag_name}:id -> String (tag_id)
category:{category_id}:posts -> Set (post IDs)

# ------------------ 限流 ------------------
# TTL: 动态，根据限流规则
ratelimit:{user_id}:{endpoint} -> String (计数器)
ratelimit:{ip}:{endpoint} -> String (IP限流)

# ------------------ 在线用户 ------------------
# TTL: 5分钟
online:users -> Set (user IDs)
online:user:{user_id}:last_seen -> String (timestamp)

# ------------------ 推荐缓存 ------------------
# TTL: 1小时
recommendations:{user_id} -> List (post IDs)
trending:posts -> Sorted Set (score = 趋势分数)

# ------------------ 点赞去重 ------------------
# TTL: 7天
post:{post_id}:liked:{user_id} -> String (1表示已点赞)
comment:{comment_id}:liked:{user_id} -> String

# ------------------ 浏览去重 ------------------
# TTL: 24小时
post:{post_id}:viewed:{user_id} -> String
```

---

## 6. 搜索与推荐

### 6.1 OpenSearch 索引

**posts_index** - 博文全文搜索:

```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_max_word": {
          "type": "custom",
          "tokenizer": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "ik_max_word",
        "fields": {
          "keyword": {"type": "keyword"},
          "suggest": {"type": "completion"}
        }
      },
      "content": {"type": "text", "analyzer": "ik_max_word"},
      "excerpt": {"type": "text"},
      "author_id": {"type": "keyword"},
      "author_username": {
        "type": "text",
        "fields": {"keyword": {"type": "keyword"}}
      },
      "tags": {"type": "keyword"},
      "categories": {"type": "keyword"},
      "status": {"type": "keyword"},
      "visibility": {"type": "keyword"},
      "created_at": {"type": "date"},
      "published_at": {"type": "date"},
      "view_count": {"type": "integer"},
      "like_count": {"type": "integer"},
      "comment_count": {"type": "integer"}
    }
  }
}
```

### 6.2 Milvus 向量库

**Collection: post_embeddings**

```python
from pymilvus import Collection, FieldSchema, CollectionSchema, DataType

fields = [
    FieldSchema(name="id", dtype=DataType.VARCHAR, max_length=100, is_primary=True),
    FieldSchema(name="post_id", dtype=DataType.VARCHAR, max_length=100),
    FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=768),
    FieldSchema(name="title", dtype=DataType.VARCHAR, max_length=500),
    FieldSchema(name="tags", dtype=DataType.ARRAY, max_capacity=20),
    FieldSchema(name="category", dtype=DataType.VARCHAR, max_length=100),
    FieldSchema(name="author_id", dtype=DataType.VARCHAR, max_length=100),
    FieldSchema(name="created_at", dtype=DataType.INT64)
]

schema = CollectionSchema(fields, description="博文向量嵌入 - 用于语义搜索和推荐")
collection = Collection("post_embeddings", schema)

# IVF_FLAT 索引，适合中等规模
index_params = {
    "index_type": "IVF_FLAT",
    "metric_type": "COSINE",
    "params": {"nlist": 128}
}
collection.create_index("vector", index_params)
```

---

## 7. 消息队列设计

### 7.1 RabbitMQ 队列

| 队列名 | 类型 | 用途 | 消费者 |
|--------|------|------|--------|
| `email.notifications` | Direct | 邮件发送 | Email Service |
| `push.notifications` | Direct | 推送通知准备 | Push Service |
| `content.moderation` | Direct | 内容审核 | Moderation Service |
| `post.scheduled` | Delayed | 定时发布博文 | Blog Service |

### 7.2 Kafka Topics

| Topic | 分区数 | 用途 | 消费者 |
|-------|--------|------|--------|
| `user-behavior-events` | 10 | 用户行为事件 | Analytics, Recommendation |
| `post-created-events` | 3 | 博文创建事件 | Recommendation, Search |
| `post-published-events` | 3 | 博文发布事件 | Notification, Analytics |
| `comment-created-events` | 3 | 评论创建事件 | Notification, Moderation |

---

## 8. API 设计

### 8.1 API 规范

- **基础 URL**: `/api/v1/`
- **认证方式**: Bearer Token (JWT)
- **响应格式**: JSON
- **分页**: `?page=1&limit=20`
- **排序**: `?sort=-created_at`
- **权限控制**: RBAC

### 8.2 核心 API

#### 认证 API

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/auth/register` | POST | 用户注册 | 公开 |
| `/auth/login` | POST | 用户登录 | 公开 |
| `/auth/oauth/{provider}` | POST | OAuth 登录 | 公开 |
| `/auth/refresh` | POST | 刷新令牌 | 公开 |
| `/auth/logout` | POST | 登出 | user |

#### 博文 API

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/posts` | GET | 获取博文列表 | guest |
| `/posts` | POST | 创建博文 | user |
| `/posts/{id}` | GET | 获取博文详情 | guest |
| `/posts/{id}` | PUT | 更新博文 | post:update:own |
| `/posts/{id}` | DELETE | 删除博文 | post:delete:own |
| `/posts/{id}/publish` | POST | 发布博文 | author |
| `/posts/search` | GET | 搜索博文 | guest |
| `/posts/recommended` | GET | 获取推荐 | user |

#### 用户 API

| 端点 | 方法 | 描述 | 权限 |
|------|------|------|------|
| `/users/{id}` | GET | 获取用户资料 | guest |
| `/users/{id}` | PUT | 更新用户资料 | user:update:own |
| `/users/{id}/follow` | POST | 关注用户 | user |
| `/users/{id}/followers` | GET | 获取粉丝列表 | guest |

---

## 9. 前端架构

### 9.1 技术栈

- **框架**: Next.js 14 (App Router)
- **UI 库**: Semi-Design (飞书 Universe Design)
- **状态管理**: Zustand + React Query
- **编辑器**: Markdown + 富文本双模式
- **SEO**: SSR + SSG + 动态 sitemap

### 9.2 项目结构

```
packages/web/
├── src/
│   ├── app/                          # Next.js App Router
│   │   ├── (auth)/                   # 认证路由组
│   │   ├── (main)/                   # 主应用路由组
│   │   │   ├── page.tsx              # 首页
│   │   │   ├── feed/                 # 动态 Feed
│   │   │   ├── posts/[slug]/         # 博文详情 (SSR)
│   │   │   └── users/[username]/     # 用户资料
│   │   ├── sitemap.xml               # 动态站点地图
│   │   └── robots.txt
│   ├── components/
│   │   ├── blog/                     # 博文组件
│   │   ├── editor/                   # 编辑器组件
│   │   └── analytics/                # 数据埋点
│   └── lib/
│       ├── api/                      # API 客户端
│       └── hooks/                    # 自定义 Hooks
└── package.json
```

---

## 10. iOS 应用架构

### 10.1 技术栈

- **框架**: React Native + Expo
- **导航**: React Navigation
- **状态管理**: 与 Web 共享 Zustand + React Query
- **UI 组件**: 与 Web 共享 Semi-Design Mobile
- **推送**: Expo Notifications
- **生物识别**: Expo Local Authentication

### 10.2 项目结构

```
packages/mobile/
├── app.json                          # Expo 配置
├── src/
│   ├── app/                          # Expo Router 入口
│   ├── screens/                      # 屏幕页面
│   │   ├── PostDetail.tsx
│   │   └── UserProfile.tsx
│   ├── navigation/                   # 导航配置
│   └── native/                       # 平台特定代码
└── assets/
```

---

## 11. 基础设施设计

### 11.1 阿里云服务映射

| 功能 | 阿里云服务 | 说明 |
|------|-----------|------|
| 计算 | ECS / ACK | 容器部署 |
| 数据库 | RDS PostgreSQL | 主数据库 |
| 缓存 | Redis (云数据库) | 缓存和会话 |
| 搜索 | OpenSearch | 全文检索 |
| 向量库 | Milvus | 向量检索 |
| 消息队列 | 自建 RabbitMQ + Kafka | 任务和事件 |
| 对象存储 | OSS | 图片视频存储 |
| 推送 | 移动推送 | iOS/Android 推送 |
| CDN | 阿里云 CDN | 静态资源加速 |

### 11.2 本地开发环境

```yaml
# docker-compose.yml (核心服务)
services:
  postgres-auth:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]

  redis-cache:
    image: redis:7-alpine
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3-management-alpine
    ports: ["5672:5672", "15672:15672"]

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on: [zookeeper]
    ports: ["9092:9092"]
```

---

## 12. 安全设计

### 12.1 认证安全

- **密码存储**: bcrypt (cost=12)
- **JWT**: 短期访问令牌 (15分钟) + 刷新令牌 (7天)
- **OAuth 2.0**: Google, GitHub, Apple
- **2FA**: TOTP (可选)

### 12.2 数据安全

- **传输加密**: TLS 1.3
- **存储加密**: 阿里云 RDS/OSS 加密
- **数据脱敏**: 日志中邮箱、手机号掩码

---

## 13. 监控与运维

### 13.1 监控指标

- **应用指标**: QPS、延迟、错误率
- **系统指标**: CPU、内存、磁盘、网络
- **业务指标**: DAU、发帖量、互动率

### 13.2 日志管理

- **应用日志**: 结构化 JSON 日志
- **访问日志**: Nginx/ALB 日志
- **审计日志**: 权限变更、敏感操作

---

*文档版本: 1.0*
*最后更新: 2026-03-25*
*优先平台: Web + iOS*
