-- =====================================================
-- 000004_oauth_and_phone
-- 1) 放宽 users：email / password_hash 可空（社交/手机号用户无邮箱或无密码）
-- 2) 新增 phone 列 + 部分唯一索引（手机号验证码登录）
-- 3) 新增 oauth_accounts 表（Google/微信第三方账号绑定）
--
-- 注意：本迁移在单事务内执行（见 internal/migrate/migrate.go），
--       故不使用 CREATE INDEX CONCURRENTLY（非事务安全）。
--       存量 users 行都已有 email+password，部分唯一索引仍覆盖它们。
-- =====================================================

-- 1) 放宽 email/password_hash 约束，改为「可空 + 部分唯一」
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users (email) WHERE email IS NOT NULL;

-- 2) 手机号列 + 部分唯一索引（多 NULL 不冲突）
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS users_phone_unique ON users (phone) WHERE phone IS NOT NULL;

-- 3) 第三方账号绑定表
--    provider_user_id：微信=unionid(优先)/openid，Google=sub
--    raw_openid：微信原始 openid，留作多应用去重
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id                VARCHAR(36) PRIMARY KEY,
    user_id           VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(32) NOT NULL,
    provider_user_id  VARCHAR(128) NOT NULL,
    raw_openid        VARCHAR(128),
    access_token      TEXT,
    refresh_token     TEXT,
    expires_at        TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX IF NOT EXISTS idx_oauth_accounts_user_id ON oauth_accounts(user_id);
