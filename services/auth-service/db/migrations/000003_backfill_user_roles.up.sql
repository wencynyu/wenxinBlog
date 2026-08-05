-- =====================================================
-- 000003_backfill_user_roles
-- 回填 RBAC 落地前注册的存量用户：给所有无角色记录的 ACTIVE 用户
-- 授予默认 'user' 角色。RBAC 落地时 Register.AssignRole 只对新注册用户生效，
-- bootstrapAdmin 只处理 admin，存量普通用户被遗漏 → user_roles 为空 → 无权限。
-- 幂等：NOT EXISTS（只补缺失的）+ ON CONFLICT DO NOTHING。
-- 前提：roles 表已有 code='user'（000001 种子）。
-- =====================================================
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'user'
WHERE u.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id)
ON CONFLICT (user_id, role_id) DO NOTHING;
