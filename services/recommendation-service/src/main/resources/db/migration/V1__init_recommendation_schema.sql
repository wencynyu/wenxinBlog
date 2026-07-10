CREATE TABLE IF NOT EXISTS recommendation_config (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    algorithm_type VARCHAR(50) NOT NULL DEFAULT 'hybrid',
    weights JSONB DEFAULT '{"content_based": 0.4, "collaborative": 0.3, "popularity": 0.2, "freshness": 0.1}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_interest_tags (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    weight DOUBLE PRECISION DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, tag)
);

CREATE INDEX idx_recommendation_config_user ON recommendation_config(user_id);
CREATE INDEX idx_user_interest_tags_user ON user_interest_tags(user_id);
