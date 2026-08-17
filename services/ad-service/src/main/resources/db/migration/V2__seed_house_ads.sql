-- House ads 种子数据：自营推广（博文/功能入口），advertiser_id 固定为 'house'。
-- 决策逻辑每 campaign 只取第一个 active creative，故每个 campaign 只配一条创意。
-- 注意：定向目前是 stub，positionType 不参与筛选，各广告位共享同一候选池（按出价排序）。

INSERT INTO ad_campaigns (advertiser_id, name, description, budget, daily_budget,
                          bid_strategy, bid_amount, targeting, status,
                          start_date, end_date, updated_at)
SELECT 'house', 'House: 发现优质博文', '站内推广：博客文章列表入口', 99999.00, 9999.00,
       'CPM', 5.00, '{}', 'ACTIVE',
       '2026-01-01 00:00:00', '2030-12-31 23:59:59', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ad_campaigns WHERE advertiser_id = 'house');

INSERT INTO ad_campaigns (advertiser_id, name, description, budget, daily_budget,
                          bid_strategy, bid_amount, targeting, status,
                          start_date, end_date, updated_at)
SELECT 'house', 'House: 个性化推荐流', '站内推广：feed 页个性化推荐', 99999.00, 9999.00,
       'CPM', 4.00, '{}', 'ACTIVE',
       '2026-01-01 00:00:00', '2030-12-31 23:59:59', CURRENT_TIMESTAMP
WHERE (SELECT COUNT(*) FROM ad_campaigns WHERE advertiser_id = 'house') = 1;

INSERT INTO ad_campaigns (advertiser_id, name, description, budget, daily_budget,
                          bid_strategy, bid_amount, targeting, status,
                          start_date, end_date, updated_at)
SELECT 'house', 'House: 开始创作', '站内推广：引导用户写博文', 99999.00, 9999.00,
       'CPM', 3.00, '{}', 'ACTIVE',
       '2026-01-01 00:00:00', '2030-12-31 23:59:59', CURRENT_TIMESTAMP
WHERE (SELECT COUNT(*) FROM ad_campaigns WHERE advertiser_id = 'house') = 2;

INSERT INTO ad_creatives (campaign_id, title, description, image_url, landing_url, creative_type, is_active)
SELECT id, '发现优质博文', '浏览社区里的最新与技术文章', 'https://picsum.photos/seed/ad-house-posts/640/360', '/posts', 'IMAGE', true
FROM ad_campaigns WHERE advertiser_id = 'house' AND name = 'House: 发现优质博文';

INSERT INTO ad_creatives (campaign_id, title, description, image_url, landing_url, creative_type, is_active)
SELECT id, '为你推荐 · 个性化信息流', '基于兴趣标签的博文推荐', 'https://picsum.photos/seed/ad-house-feed/640/360', '/feed', 'IMAGE', true
FROM ad_campaigns WHERE advertiser_id = 'house' AND name = 'House: 个性化推荐流';

INSERT INTO ad_creatives (campaign_id, title, description, image_url, landing_url, creative_type, is_active)
SELECT id, '开始创作你的博文', '分享你的想法与技术心得', 'https://picsum.photos/seed/ad-house-editor/640/360', '/editor', 'IMAGE', true
FROM ad_campaigns WHERE advertiser_id = 'house' AND name = 'House: 开始创作';
