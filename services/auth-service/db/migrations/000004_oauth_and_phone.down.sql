-- 000004_oauth_and_phone 回滚
-- 警告：恢复 email NOT NULL UNIQUE 仅在所有 users.email 都非空时才安全；
--       若已存在社交/手机号用户（email IS NULL），重建约束会失败。回滚前请先清理这些用户。

DROP TABLE IF EXISTS oauth_accounts;
DROP INDEX IF EXISTS users_phone_unique;
ALTER TABLE users DROP COLUMN IF EXISTS phone;

DROP INDEX IF EXISTS users_email_unique;
-- 仅当无 NULL email 时可恢复原约束（否则执行报错，符合预期——提示数据不兼容）。
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_key ON users (email);
