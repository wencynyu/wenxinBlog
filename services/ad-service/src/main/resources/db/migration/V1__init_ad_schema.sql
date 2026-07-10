CREATE TABLE IF NOT EXISTS ad_positions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    position_type VARCHAR(50) NOT NULL,
    description TEXT,
    max_ads INT DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ad_campaigns (
    id BIGSERIAL PRIMARY KEY,
    advertiser_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    budget DECIMAL(12,2) NOT NULL,
    daily_budget DECIMAL(12,2),
    spent DECIMAL(12,2) DEFAULT 0,
    daily_spent DECIMAL(12,2) DEFAULT 0,
    bid_strategy VARCHAR(50) DEFAULT 'CPM',
    bid_amount DECIMAL(10,2) NOT NULL,
    targeting VARCHAR(2000) DEFAULT '{}',
    status VARCHAR(20) DEFAULT 'DRAFT',
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ad_creatives (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT REFERENCES ad_campaigns(id) ON DELETE CASCADE,
    title VARCHAR(200),
    description TEXT,
    image_url VARCHAR(500),
    landing_url VARCHAR(500),
    creative_type VARCHAR(50) DEFAULT 'IMAGE',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ad_events (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    creative_id BIGINT,
    user_id VARCHAR(36),
    event_type VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    referrer TEXT,
    metadata VARCHAR(2000) DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ad_campaigns_status ON ad_campaigns(status);
CREATE INDEX idx_ad_campaigns_advertiser ON ad_campaigns(advertiser_id);
CREATE INDEX idx_ad_creatives_campaign ON ad_creatives(campaign_id);
CREATE INDEX idx_ad_events_campaign ON ad_events(campaign_id);
CREATE INDEX idx_ad_events_type ON ad_events(event_type);
CREATE INDEX idx_ad_events_created ON ad_events(created_at);

INSERT INTO ad_positions (name, position_type, description, max_ads) VALUES
    ('Feed Banner', 'FEED', 'Feed信息流横幅', 1),
    ('Sidebar', 'SIDEBAR', '侧边栏广告', 2),
    ('Banner', 'BANNER', '页面顶部横幅', 1),
    ('Popup', 'POPUP', '弹窗广告', 1);
