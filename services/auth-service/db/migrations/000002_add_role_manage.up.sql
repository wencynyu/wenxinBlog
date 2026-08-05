-- =====================================================
-- 000002_add_role_manage
-- 新增 role:manage 权限（管理角色与权限定义），授予 admin 角色。
-- 供角色/权限 CRUD 与角色↔权限动态配置端点使用。
-- =====================================================

INSERT INTO permissions (id, code, name, resource, action, scope, description) VALUES
(22, 'role:manage', '管理角色与权限', 'role', 'manage', NULL, '配置角色权限、查看与管理权限定义')
ON CONFLICT (id) DO NOTHING;
SELECT setval('permissions_id_seq', 22, TRUE);

-- 授予 admin 角色（幂等）。
WITH rp(role_code, perm_code) AS (VALUES ('admin', 'role:manage'))
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM rp
JOIN roles r ON r.code = rp.role_code
JOIN permissions p ON p.code = rp.perm_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
