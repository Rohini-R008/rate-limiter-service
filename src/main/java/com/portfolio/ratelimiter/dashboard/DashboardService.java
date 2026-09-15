package com.portfolio.ratelimiter.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads live rate-limit and ban state straight from Redis for the dashboard.
 *
 * Uses SCAN (cursor-based, non-blocking) rather than KEYS (which blocks Redis
 * while it walks the whole keyspace). For a large production keyspace you'd
 * keep an index set of active keys instead of scanning; SCAN is fine at this
 * project's scale and doesn't stall other clients.
 */
@Service
public class DashboardService {

    private static final String RATE_PREFIX = "rl:tb:";
    private static final String BAN_PREFIX  = "abuse:ban:";

    private final StringRedisTemplate redis;
    private final int capacity;

    public DashboardService(StringRedisTemplate redis,
                            @Value("${ratelimiter.capacity}") int capacity) {
        this.redis = redis;
        this.capacity = capacity;
    }

    public List<KeyRate> currentRates() {
        List<KeyRate> rates = new ArrayList<>();
        for (String key : scan(RATE_PREFIX + "*")) {
            String used = redis.opsForValue().get(key);
            Long ttl = redis.getExpire(key);
            rates.add(new KeyRate(
                    key.substring(RATE_PREFIX.length()),
                    used == null ? 0 : Integer.parseInt(used),
                    capacity,
                    ttl == null ? -1 : ttl));
        }
        return rates;
    }

    public List<ActiveBan> activeBans() {
        List<ActiveBan> bans = new ArrayList<>();
        for (String key : scan(BAN_PREFIX + "*")) {
            Long ttl = redis.getExpire(key);
            bans.add(new ActiveBan(
                    key.substring(BAN_PREFIX.length()),
                    ttl == null ? -1 : ttl));
        }
        return bans;
    }

    private List<String> scan(String pattern) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }

    public record KeyRate(String apiKey, int used, int capacity, long ttlSeconds) {}
    public record ActiveBan(String apiKey, long ttlSeconds) {}
}