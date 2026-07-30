CREATE TABLE IF NOT EXISTS media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    post_id UUID,
    type VARCHAR(20) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INT,
    height INT,
    duration INT,
    storage_provider VARCHAR(20),
    bucket VARCHAR(100),
    object_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'PENDING',
    processing_errors JSONB,
    moderation_status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_media_user ON media_assets(user_id);
CREATE INDEX idx_media_post ON media_assets(post_id);
CREATE INDEX idx_media_status ON media_assets(status);

CREATE TABLE IF NOT EXISTS media_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    variant_type VARCHAR(20) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    size_bytes INT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    cdn_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
