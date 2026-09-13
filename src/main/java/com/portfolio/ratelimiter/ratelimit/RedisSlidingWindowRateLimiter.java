package com.portfolio.ratelimiter.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

/**
 * Distributed sliding window using a Redis sorted set (ZSET).
 *
 * Each admitted token is a ZSET member scored by its timestamp. On each call
 * the Lua script drops members older than the window (ZREMRANGEBYSCORE),
 * counts what's left (ZCARD), and only if there's room adds the new members
 * (ZADD) - all atomically in one EVAL. This is an exact rolling window shared
 * across every instance, with no fixed-window boundary to burst across.
 */
public class RedisSlidingWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final int capacity;
    private final long windowMillis;

    public RedisSlidingWindowRateLimiter(StringRedisTemplate redis, RedisScript<Long> script,
                                         int capacity, long windowMillis) {
        this.redis = redis;
        this.script = script;
        this.capacity = capacity;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        long now = System.currentTimeMillis();
        String member = now + "-" + UUID.randomUUID();
        Long result = redis.execute(
                script,
                List.of("rl:sw:" + key),
                String.valueOf(now),
                String.valueOf(windowMillis),
                String.valueOf(capacity),
                String.valueOf(tokens),
                member);
        return result != null && result == 1L;
    }
}