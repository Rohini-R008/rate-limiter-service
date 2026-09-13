package com.portfolio.ratelimiter.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Log-based sliding window: each key keeps a deque of (timestamp, cost)
 * entries for every admitted request in the last `windowMillis`. Each call
 * evicts entries older than the window, then admits if the remaining cost
 * total still leaves room.
 *
 * Log-based vs counter-based: I went log-based. A counter-based approach
 * (blend two adjacent fixed windows by elapsed fraction) is O(1) memory per
 * key but approximate. Log-based is an exact rolling window with no
 * approximation error to defend, at the cost of O(requests-in-window)
 * memory per key - acceptable here since window sizes are small (seconds
 * to minutes) and per-key volume is itself bounded by this limiter.
 *
 * Thread-safe from the start: a key's Window is only ever touched inside
 * `synchronized(w)`, so eviction + admission is atomic per key. Different
 * keys never contend with each other.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    private record Entry(long timestamp, int cost) {}

    private static class Window {
        final Deque<Entry> entries = new ArrayDeque<>();
        int currentCost = 0;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int capacity;
    private final long windowMillis;

    public SlidingWindowRateLimiter(int capacity, long windowMillis) {
        this.capacity = capacity;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            evictExpired(w, now);
            if (w.currentCost + tokens > capacity) {
                return false;
            }
            w.entries.addLast(new Entry(now, tokens));
            w.currentCost += tokens;
            return true;
        }
    }

    private void evictExpired(Window w, long now) {
        long cutoff = now - windowMillis;
        while (!w.entries.isEmpty() && w.entries.peekFirst().timestamp() < cutoff) {
            Entry expired = w.entries.pollFirst();
            w.currentCost -= expired.cost();
        }
    }
}