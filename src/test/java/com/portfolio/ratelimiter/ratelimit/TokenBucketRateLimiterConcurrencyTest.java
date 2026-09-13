package com.portfolio.ratelimiter.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterConcurrencyTest {

    @Test
    void concurrentRequestsNeverExceedLimit() throws InterruptedException {
        int limit = 10;
        int threadCount = 50;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(limit, 60_000);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger allowed = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (limiter.tryAcquire("shared-key", 1)) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();       // block until all 50 threads are alive and waiting
        start.countDown();   // release them all at the same instant -> max contention
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.println("Allowed: " + allowed.get() + " (limit=" + limit + ")");
        assertTrue(allowed.get() <= limit,
            "Expected at most " + limit + " allowed requests, but got " + allowed.get());
    }
}
