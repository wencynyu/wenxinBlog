-- Minimal Lua script for rate limiting (for testing only)
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

return {1}
