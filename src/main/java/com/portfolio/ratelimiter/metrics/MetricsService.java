package com.portfolio.ratelimiter.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks total request volume in fixed time buckets so the dashboard can draw
 * "requests over time". Each request INCRs a counter for the current bucket
 * (bucket = epochMillis / bucketMs). Reading the last N buckets needs no key
 * scan: the bucket names are deterministic, so we compute them and MGET.
 */
@Service
public class MetricsService {

    private static final String PREFIX = "metrics:vol:";

    private final StringRedisTemplate redis;
    private final long bucketMs;

    public MetricsService(StringRedisTemplate redis,
                          @Value("${dashboard.volume.bucket-ms:10000}") long bucketMs) {
        this.redis = redis;
        this.bucketMs = bucketMs;
    }

    /** Count one request against the current time bucket. */
    public void recordRequest() {
        long bucket = System.currentTimeMillis() / bucketMs;
        String key = PREFIX + bucket;
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            // keep buckets around long enough to chart, then let them expire
            redis.expire(key, Duration.ofMillis(bucketMs * 500));
        }
    }

    /** The last `count` buckets, oldest first, as (bucketStartMs, requestCount). */
    public List<VolumePoint> recentVolume(int count) {
        long currentBucket = System.currentTimeMillis() / bucketMs;
        List<String> keys = new ArrayList<>(count);
        for (int i = count - 1; i >= 0; i--) {
            keys.add(PREFIX + (currentBucket - i));
        }
        List<String> values = redis.opsForValue().multiGet(keys);

        List<VolumePoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long bucket = currentBucket - (count - 1 - i);
            String raw = (values == null) ? null : values.get(i);
            long requests = (raw == null) ? 0L : Long.parseLong(raw);
            points.add(new VolumePoint(bucket * bucketMs, requests));
        }
        return points;
    }

    public record VolumePoint(long timestampMs, long requests) {}
}