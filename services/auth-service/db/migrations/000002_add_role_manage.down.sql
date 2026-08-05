-- 回滚 000002：撤销 admin 的 role:manage 并删除该权限定义。
DELETE FROM role_permissions
WHERE permission_id = (SELECT id FROM permissions WHERE code = 'role:manage');
DELETE FROM permissions WHERE code = 'role:manage';
