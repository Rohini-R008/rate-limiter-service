package com.portfolio.ratelimiter.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * NAIVE distributed version - intentionally broken, do not fix yet.
 *
 * Counts used requests per key in Redis with two separate round trips:
 *   1. GET the current count      (round trip 1)
 *   2. check the limit in Java
 *   3. INCRBY to consume tokens    (round trip 2)
 *
 * Between round trip 1 and round trip 3, another app instance sharing the
 * same Redis runs its own GET and sees the same stale count. Multiple
 * instances all pass the check and all INCRBY, so the global count blows
 * past the limit. Prove this with the multi-instance test, then replace
 * this file with the Lua version.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final int capacity;
    private final long windowSeconds;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, int capacity, long windowSeconds) {
        this.redis = redis;
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        String redisKey = "rl:tb:" + key;
        String current = redis.opsForValue().get(redisKey);        // round trip 1
        int count = (current == null) ? 0 : Integer.parseInt(current);
        if (count + tokens > capacity) {                           // check in app code
            return false;
        }
        Long updated = redis.opsForValue().increment(redisKey, tokens); // round trip 2
        if (updated != null && updated == tokens) {
            redis.expire(redisKey, Duration.ofSeconds(windowSeconds));   // TTL on first write
        }
        return true;
    }
}