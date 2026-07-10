# Auth Service

认证服务 - 负责 OAuth2.0、SSO、JWT 签发

## 功能

- OAuth2.0 登录 (Google, GitHub, 微信)
- 用户注册/登录
- JWT 签发与验证
- 二步验证 (2FA)
- 会话管理 (Redis)

## 技术栈

- Go 1.23
- Fiber v2.52.0
- PostgreSQL (auth_db)
- Redis

## 数据库 (auth_db)

### users 表
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    email_verified BOOLEAN DEFAULT FALSE,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    two_factor_secret VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### oauth_accounts 表
```sql
CREATE TABLE oauth_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL, -- google, github, wechat
    provider_user_id VARCHAR(255) NOT NULL,
    access_token TEXT,
    refresh_token TEXT,
    expires_at TIMESTAMP,
    UNIQUE(provider, provider_user_id)
);
```

### sessions 表
```sql
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## API

```
POST   /api/v1/auth/register       - 用户注册
POST   /api/v1/auth/login          - 密码登录
GET    /api/v1/auth/oauth/:provider  - OAuth授权
GET    /api/v1/auth/callback/:provider - OAuth回调
POST   /api/v1/auth/refresh        - 刷新Token
POST   /api/v1/auth/logout         - 登出
GET    /api/v1/auth/2fa/enable     - 启用2FA
POST   /api/v1/auth/2fa/verify     - 验证2FA
```

## 环境变量

```bash
PORT=8001
DATABASE_URL=postgres://postgres:postgres@localhost:5432/auth_db
REDIS_URL=redis://localhost:6379
JWT_SECRET=your-secret-key
JWT_EXPIRY=86400

# OAuth
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
WECHAT_APP_ID=
WECHAT_APP_SECRET=
```

## 运行

```bash
cd services/auth-service
go run cmd/server/main.go
```
