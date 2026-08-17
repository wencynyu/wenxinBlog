-- 修正计费精度：spent/daily_spent 原为 DECIMAL(12,2)，而 CPM 单次曝光成本 = bid/1000
-- （如 bid=5.00 → 0.005），低于列精度。每次 UPDATE 就地取整导致：
--   bid=5.00 每次 +0.005 被取整成 +0.01（多扣一倍）；bid<10.00 的 campaign 取整成 +0.00（永不扣费）。
-- 提升到 4 位小数（与 costPerImpression 的 4 位 HALF_UP 对齐）。
ALTER TABLE ad_campaigns ALTER COLUMN spent TYPE DECIMAL(12,4);
ALTER TABLE ad_campaigns ALTER COLUMN daily_spent TYPE DECIMAL(12,4);

-- 清零 house campaign 在 2 位精度期间被污染的花费（当前库中只有 smoke 测试数据）
UPDATE ad_campaigns SET spent = 0, daily_spent = 0 WHERE advertiser_id = 'house';
