package com.portfolio.ratelimiter.ratelimit;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-instance concurrency test. Requires the full stack running:
 *     docker compose up --build
 * Then run explicitly (it won't run in a normal `mvn test` - the IT suffix
 * keeps Surefire from auto-picking it):
 *     mvn test -Dtest=DistributedRateLimitIT
 *
 * Fires REQUESTS concurrent requests at the nginx load balancer, which
 * round-robins them across app1 and app2 sharing one Redis. Naive limiter =>
 * more than LIMIT get through. Lua limiter => exactly LIMIT.
 */
class DistributedRateLimitIT {

    private static final String LB_URL = "http://localhost:8080/api/products";
    private static final int LIMIT = 10;
    private static final int REQUESTS = 60;

    @Test
    void limitHoldsGloballyAcrossInstances() throws Exception {
        String apiKey = "dist-" + UUID.randomUUID();   // fresh key => fresh window
        HttpClient client = HttpClient.newHttpClient();

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(REQUESTS);
        AtomicInteger allowed = new AtomicInteger();
        ConcurrentHashMap<String, Integer> servedBy = new ConcurrentHashMap<>();

        for (int i = 0; i < REQUESTS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(LB_URL))
                            .header("X-API-Key", apiKey)
                            .GET().build();
                    HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                    if (resp.statusCode() == 200) {
                        allowed.incrementAndGet();
                    }
                    resp.headers().firstValue("X-Instance-Id")
                            .ifPresent(id -> servedBy.merge(id, 1, Integer::sum));
                } catch (Exception e) {
                    // treat failures as not-allowed
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.println("Allowed: " + allowed.get() + " (limit=" + LIMIT + ")");
        System.out.println("Served by instances: " + servedBy);
        assertTrue(allowed.get() <= LIMIT,
                "Global limit exceeded: " + allowed.get() + " > " + LIMIT);
    }
}