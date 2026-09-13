package com.portfolio.ratelimiter.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FIXED VERSION.
 *
 * Same per-key token count as before, but each key's value is now an
 * AtomicInteger, and the read-check-write is a compare-and-swap retry loop
 * instead of a plain get/put. Two threads can still call tryAcquire on the
 * same key at the same instant - that's fine, only one of them can ever
 * win a given compareAndSet. The other sees its read was stale and retries
 * against the now-current value, so no decrement is ever lost.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, AtomicInteger> remainingTokens = new ConcurrentHashMap<>();
    private final int capacity;
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private final long windowMillis;

    public TokenBucketRateLimiter(int capacity, long windowMillis) {
        this.capacity = capacity;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        maybeResetWindow();
        AtomicInteger counter = remainingTokens.computeIfAbsent(key, k -> new AtomicInteger(capacity));

        while (true) {
            int current = counter.get();
            if (current < tokens) {
                return false;
            }
            if (counter.compareAndSet(current, current - tokens)) {
                return true;
            }
            // another thread updated this key between our get() and
            // compareAndSet() - our `current` is stale, loop and retry
        }
    }

    private void maybeResetWindow() {
        long now = System.currentTimeMillis();
        long start = windowStart.get();
        if (now - start >= windowMillis) {
            if (windowStart.compareAndSet(start, now)) {
                remainingTokens.clear();
            }
        }
    }
}