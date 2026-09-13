package com.portfolio.ratelimiter.config;

import com.portfolio.ratelimiter.ratelimit.RateLimiter;
import com.portfolio.ratelimiter.ratelimit.RedisTokenBucketRateLimiter;
import com.portfolio.ratelimiter.ratelimit.SlidingWindowRateLimiter;
import com.portfolio.ratelimiter.ratelimit.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RateLimiterConfig {

    @Value("${ratelimiter.implementation:token-bucket}")
    private String implementation;

    @Value("${ratelimiter.capacity:10}")
    private int capacity;

    @Value("${ratelimiter.window-ms:60000}")
    private long windowMillis;

    @Bean
    public RateLimiter rateLimiter(StringRedisTemplate redis) {
        long windowSeconds = Math.max(1, windowMillis / 1000);
        return switch (implementation) {
            case "token-bucket"   -> new TokenBucketRateLimiter(capacity, windowMillis);
            case "sliding-window" -> new SlidingWindowRateLimiter(capacity, windowMillis);

            // Fixed distributed version.
            case "redis-token-bucket" -> new RedisTokenBucketRateLimiter(
                redis, loadScript("scripts/token_bucket.lua"), capacity, windowSeconds);

            default -> throw new IllegalStateException(
                    "Unknown ratelimiter.implementation: " + implementation);
        };
    }

    private RedisScript<Long> loadScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpathLocation)));
        script.setResultType(Long.class);
        return script;
    }
}