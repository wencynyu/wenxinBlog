-- =====================================================
-- 000001_init_auth_schema
-- 1) users（与既有手工 schema 对齐，IF NOT EXISTS 兼容既有库）
-- 2) RBAC：roles / permissions / role_permissions / user_roles
-- 3) 种子：5 个系统角色 + 21 个权限 + role_permissions 矩阵
-- =====================================================

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(36) PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    VARCHAR(500) DEFAULT '',
    status        VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    two_fa_enabled BOOLEAN DEFAULT FALSE NOT NULL,
    two_fa_secret VARCHAR(255) DEFAULT '',
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

-- 角色表（支持继承）
CREATE TABLE IF NOT EXISTS roles (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id   INTEGER REFERENCES roles(id) ON DELETE SET NULL,
    level       INTEGER DEFAULT 0 NOT NULL,
    is_system   BOOLEAN DEFAULT FALSE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 权限表（code = resource:action[:scope]）
CREATE TABLE IF NOT EXISTS permissions (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(100) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    resource    VARCHAR(50) NOT NULL,
    action      VARCHAR(50) NOT NULL,
    scope       VARCHAR(20),
    description TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 角色-权限关联（存"角色自身权限"，继承由运行期递归解析）
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    granted_by    VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);

-- 用户-角色关联（支持 expires_at 临时角色）
CREATE TABLE IF NOT EXISTS user_roles (
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    expires_at  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_expires_at ON user_roles(expires_at) WHERE expires_at IS NOT NULL;

-- ---------- 种子：5 个系统角色（继承链 + level） ----------
INSERT INTO roles (id, code, name, description, parent_id, level, is_system) VALUES
(1, 'guest',     '访客',     '未登录用户，只读公开内容', NULL, 0, TRUE),
(2, 'user',      '普通用户', '已注册用户，可创建内容',     1,   1, TRUE),
(3, 'author',    '作者',     '可发布内容，有编辑权限',     2,   2, TRUE),
(4, 'moderator', '版主',     '可审核内容，管理评论',       3,   3, TRUE),
(5, 'admin',     '管理员',   '全部权限，系统管理',         4,   4, TRUE)
ON CONFLICT (code) DO UPDATE SET
  name = EXCLUDED.name, description = EXCLUDED.description,
  parent_id = EXCLUDED.parent_id, level = EXCLUDED.level, is_system = EXCLUDED.is_system;
SELECT setval('roles_id_seq', 5, TRUE);

-- ---------- 种子：21 个权限 ----------
INSERT INTO permissions (id, code, name, resource, action, scope, description) VALUES
(1,  'post:read',             '查看博文',       'post',          'read',       NULL, '浏览公开博文'),
(2,  'post:create',           '创建博文',       'post',          'create',      NULL, '创建新博文'),
(3,  'post:publish',          '发布博文',       'post',          'publish',     NULL, '将草稿发布为公开'),
(4,  'post:feature',          '设为精华',       'post',          'feature',     NULL, '将博文设为精华'),
(5,  'post:update:own',       '更新自己的博文', 'post',          'update',      'own', '更新自己创建的博文'),
(6,  'post:update:any',       '更新任意博文',   'post',          'update',      'any', '更新任意用户的博文'),
(7,  'post:delete:own',       '删除自己的博文', 'post',          'delete',      'own', '删除自己创建的博文'),
(8,  'post:delete:any',       '删除任意博文',   'post',          'delete',      'any', '删除任意用户的博文'),
(9,  'comment:create',        '发表评论',       'comment',       'create',      NULL, '发表评论'),
(10, 'comment:update:own',    '更新自己的评论', 'comment',       'update',      'own', '更新自己的评论'),
(11, 'comment:delete:own',    '删除自己的评论', 'comment',       'delete',      'own', '删除自己的评论'),
(12, 'comment:moderate',      '审核评论',       'comment',       'moderate',    NULL, '审核/隐藏评论'),
(13, 'user:update:own',       '更新自己的资料', 'user',          'update',      'own', '更新自己的资料'),
(14, 'user:update:any',       '更新任意用户资料','user',         'update',      'any', '更新任意用户资料'),
(15, 'user:ban',              '封禁用户',       'user',          'ban',         NULL, '封禁/解封用户'),
(16, 'user:assign_role',      '分配角色',       'user',          'assign_role', NULL, '为用户分配/移除角色'),
(17, 'category:manage',       '管理分类',       'category',      'manage',      NULL, '管理文章分类'),
(18, 'experiment:manage',     '管理实验',       'experiment',    'manage',      NULL, '创建/启停 A/B 实验'),
(19, 'analytics:read',        '查看分析数据',   'analytics',     'read',        NULL, '查询行为分析'),
(20, 'ad:manage',             '管理广告',       'ad',            'manage',      NULL, '管理广告计划'),
(21, 'recommendation:manage', '管理推荐',       'recommendation','manage',      NULL, '触发推荐管理操作(backfill)')
ON CONFLICT (id) DO NOTHING;
SELECT setval('permissions_id_seq', 21, TRUE);

-- ---------- 种子：role_permissions 矩阵（直接权限；继承由运行期解析） ----------
WITH matrix(role_code, perm_code) AS (VALUES
  ('guest',     'post:read'),
  ('user',      'post:create'),        ('user', 'post:update:own'), ('user', 'post:delete:own'),
  ('user',      'comment:create'),     ('user', 'comment:update:own'), ('user', 'comment:delete:own'),
  ('user',      'user:update:own'),
  ('author',    'post:publish'),
  ('moderator', 'post:feature'),       ('moderator', 'post:update:any'), ('moderator', 'post:delete:any'),
  ('moderator', 'comment:moderate'),
  ('admin',     'user:update:any'),    ('admin', 'user:ban'), ('admin', 'user:assign_role'),
  ('admin',     'category:manage'),    ('admin', 'experiment:manage'), ('admin', 'analytics:read'),
  ('admin',     'ad:manage'),          ('admin', 'recommendation:manage')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM matrix m
JOIN roles r ON r.code = m.role_code
JOIN permissions p ON p.code = m.perm_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
