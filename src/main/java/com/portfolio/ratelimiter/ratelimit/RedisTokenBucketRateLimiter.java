package com.portfolio.ratelimiter.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * FIXED distributed version.
 *
 * The GET + check + INCRBY is now a single Lua script sent with one EVAL.
 * Redis runs a script to completion atomically - no other command, from any
 * instance, interleaves with it - so the check and the increment can never be
 * split by a concurrent caller. The race that let multiple instances pass the
 * same stale check is gone.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final int capacity;
    private final long windowSeconds;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, RedisScript<Long> script,
                                       int capacity, long windowSeconds) {
        this.redis = redis;
        this.script = script;
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        Long result = redis.execute(
                script,
                List.of("rl:tb:" + key),
                String.valueOf(capacity),
                String.valueOf(tokens),
                String.valueOf(windowSeconds));
        return result != null && result == 1L;
    }
}