-- KEYS[1] = rate-limit key
-- ARGV[1] = capacity (max requests per window)
-- ARGV[2] = tokens requested by this call
-- ARGV[3] = window length in seconds (TTL)
-- Returns 1 if allowed, 0 if rejected.
local current = redis.call('GET', KEYS[1])
if current == false then
    current = 0
else
    current = tonumber(current)
end
if current + tonumber(ARGV[2]) > tonumber(ARGV[1]) then
    return 0
end
redis.call('INCRBY', KEYS[1], ARGV[2])
if current == 0 then
    redis.call('EXPIRE', KEYS[1], ARGV[3])
end
return 1