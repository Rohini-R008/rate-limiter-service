package com.portfolio.ratelimiter.ratelimit;

/**
 * Strategy interface for rate limiting. Implementations decide, per key,
 * whether a request costing `tokens` is allowed right now.
 *
 * `tokens` exists (rather than a no-arg tryAcquire) so Phase 3's
 * cost-weighted limiting can charge different endpoints different amounts
 * without changing this interface - e.g. GET = 1 token, POST /orders = 5.
 */
public interface RateLimiter {
    boolean tryAcquire(String key, int tokens);
}