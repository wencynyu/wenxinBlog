-- =====================================================
-- user_db 手工迁移：email/password_hash 改为可空 + 部分唯一索引
-- 背景：auth-service 新增 Google/微信/手机号登录后，会出现无邮箱（微信/手机号）
--       或无密码（社交/手机号）的用户。user_db.users 原 email NOT NULL UNIQUE 会让
--       第二个无邮箱用户在跨库同步时撞 users_email_key。
-- 注意：user-service 无迁移器（技术债，见 docs/GO_SCHEMA_TECH_DEBT.md），
--       本脚本需对 user_db 手工执行一次；db/schema.sql 已同步更新（新库直接用）。
-- =====================================================

BEGIN;

ALTER TABLE public.users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE public.users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE public.users ALTER COLUMN password_hash DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique
    ON public.users (email) WHERE email IS NOT NULL;

COMMIT;
