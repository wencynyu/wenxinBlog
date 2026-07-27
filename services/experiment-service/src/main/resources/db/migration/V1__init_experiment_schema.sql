-- Layers（实验层 = 互斥域）
CREATE TABLE IF NOT EXISTS layers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 预置 4 个标准 Layer
INSERT INTO layers (name, description) VALUES
    ('recommendation', '推荐算法实验层（混合权重/去重/nprobe）'),
    ('ads', '广告策略实验层（位置/CTR/定向）'),
    ('search', '搜索排序实验层'),
    ('ui', '界面展示实验层')
ON CONFLICT (name) DO NOTHING;

-- Experiments（实验）
CREATE TABLE IF NOT EXISTS experiments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    layer_id    UUID NOT NULL REFERENCES layers(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    traffic_pct INT NOT NULL DEFAULT 100,
    config      TEXT NOT NULL,
    started_at  TIMESTAMP,
    ended_at    TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_experiments_layer ON experiments(layer_id);
CREATE INDEX idx_experiments_status ON experiments(status);
