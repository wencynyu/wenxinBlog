-- blog_db 中 blog-service 负责的部分表结构，供集成测试预建。
-- 来源：wenxinBlog/services/blog-service/src/main/resources/db/migration/V1__init_blog_schema.sql
--       + V2__add_rbac_columns.sql（RBAC 列）+ authors 缓存表（dev 手动建、不在迁移中）。
-- 注意：blog-service 的迁移若变更，需同步更新此文件（recommendation-service 只读这些表）。

CREATE TABLE IF NOT EXISTS posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    summary VARCHAR(500),
    cover_image VARCHAR(500),
    status VARCHAR(20) DEFAULT 'DRAFT',
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    published_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_posts_author ON posts(author_id);
CREATE INDEX IF NOT EXISTS idx_posts_status ON posts(status);
CREATE INDEX IF NOT EXISTS idx_posts_published ON posts(published_at DESC);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS featured BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    slug VARCHAR(60) UNIQUE NOT NULL,
    description VARCHAR(200),
    post_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS post_tags (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag_id INT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX IF NOT EXISTS idx_post_tags_tag ON post_tags(tag_id);

CREATE TABLE IF NOT EXISTS authors (
    id UUID PRIMARY KEY,
    username VARCHAR(100),
    display_name VARCHAR(100),
    avatar_url VARCHAR(500)
);
