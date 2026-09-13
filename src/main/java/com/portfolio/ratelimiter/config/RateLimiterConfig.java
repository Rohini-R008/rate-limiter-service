package com.portfolio.ratelimiter.config;

import com.portfolio.ratelimiter.ratelimit.RateLimiter;
import com.portfolio.ratelimiter.ratelimit.SlidingWindowRateLimiter;
import com.portfolio.ratelimiter.ratelimit.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Value("${ratelimiter.implementation:token-bucket}")
    private String implementation;

    @Value("${ratelimiter.capacity:10}")
    private int capacity;

    @Value("${ratelimiter.window-ms:60000}")
    private long windowMillis;

    @Bean
    public RateLimiter rateLimiter() {
        return switch (implementation) {
            case "sliding-window" -> new SlidingWindowRateLimiter(capacity, windowMillis);
            case "token-bucket" -> new TokenBucketRateLimiter(capacity, windowMillis);
            default -> throw new IllegalStateException(
                "Unknown ratelimiter.implementation: " + implementation);
        };
    }
}