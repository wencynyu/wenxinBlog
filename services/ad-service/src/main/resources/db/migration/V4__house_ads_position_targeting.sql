-- House ads 广告位定向：决策服务已支持 targeting.positions（为空 = 不限所有广告位）。
-- FEED 池：发现优质博文 + 个性化推荐流；SIDEBAR 池：开始创作。
UPDATE ad_campaigns SET targeting = '{"positions":["FEED"]}'
WHERE advertiser_id = 'house' AND name IN ('House: 发现优质博文', 'House: 个性化推荐流');

UPDATE ad_campaigns SET targeting = '{"positions":["SIDEBAR"]}'
WHERE advertiser_id = 'house' AND name = 'House: 开始创作';

-- 给 campaign 1 加第二条创意，验证多创意随机轮播（决策不再永远取第一个 creative）
INSERT INTO ad_creatives (campaign_id, title, description, image_url, landing_url, creative_type, is_active)
SELECT id, '发现优质博文 · 本周热门', '浏览社区里的最新与技术文章', 'https://picsum.photos/seed/ad-house-posts-b/640/360', '/posts', 'IMAGE', true
FROM ad_campaigns
WHERE advertiser_id = 'house' AND name = 'House: 发现优质博文'
  AND NOT EXISTS (SELECT 1 FROM ad_creatives WHERE title = '发现优质博文 · 本周热门');
