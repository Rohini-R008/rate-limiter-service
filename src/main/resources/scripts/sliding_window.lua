-- KEYS[1] = rate-limit key (a Redis sorted set)
-- ARGV[1] = now (epoch millis)
-- ARGV[2] = window length in millis
-- ARGV[3] = limit (max entries in window)
-- ARGV[4] = tokens requested (each token = one member)
-- ARGV[5] = unique member prefix for this request
-- Returns 1 if allowed, 0 if rejected.
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local tokens = tonumber(ARGV[4])
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
local count = redis.call('ZCARD', KEYS[1])
if count + tokens > limit then
    return 0
end
for i = 1, tokens do
    redis.call('ZADD', KEYS[1], now, ARGV[5] .. '-' .. i)
end
redis.call('PEXPIRE', KEYS[1], window)
return 1