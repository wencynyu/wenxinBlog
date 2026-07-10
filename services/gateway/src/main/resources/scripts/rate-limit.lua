-- 滑动窗口限流脚本
-- KEYS[1]: 限流key (rate-limit:user:{userId} 或 rate-limit:ip:{ip})
-- ARGV[1]: 限流数量
-- ARGV[2]: 时间窗口(秒)
-- ARGV[3]: 当前时间戳(毫秒)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2]) * 1000  -- 转换为毫秒
local now = tonumber(ARGV[3])

-- 移除窗口外的记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 获取当前计数
local current = redis.call('ZCARD', key)

if current < limit then
    -- 添加当前请求
    redis.call('ZADD', key, now, tostring(now))
    -- 设置过期时间
    redis.call('EXPIRE', key, tonumber(ARGV[2]) + 1)
    return 1  -- 允许
else
    -- 获取窗口中最早的请求时间
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if #oldest > 0 then
        local retryAfter = math.ceil((oldest[2] + window - now) / 1000)
        return {0, retryAfter}  -- 拒绝，返回重试时间
    else
        return 0  -- 拒绝
    end
end
