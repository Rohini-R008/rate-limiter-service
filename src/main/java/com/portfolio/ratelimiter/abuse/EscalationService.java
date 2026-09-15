package com.portfolio.ratelimiter.abuse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Escalation policy, shared across instances via Redis.
 *
 * Each triggered rule increments a per-key violation counter (with a rolling
 * TTL window). The counter's value decides the action:
 *   1st trigger                      -> LOG_ONLY (record it, don't block)
 *   >= temp-block threshold          -> TEMP_BLOCK (short ban)
 *   >= long-ban threshold            -> LONG_BAN (long ban)
 *
 * Bans are Redis keys with a TTL, so every instance sees the same ban and it
 * expires on its own. Thresholds and durations are all configurable.
 */
@Service
public class EscalationService {

    public enum Action { LOG_ONLY, TEMP_BLOCK, LONG_BAN }

    private final StringRedisTemplate redis;
    private final long windowMs;
    private final int tempBlockThreshold;
    private final int longBanThreshold;
    private final long tempBlockSeconds;
    private final long longBanSeconds;

    public EscalationService(
            StringRedisTemplate redis,
            @Value("${abuse.escalation.window-ms}") long windowMs,
            @Value("${abuse.escalation.temp-block-threshold}") int tempBlockThreshold,
            @Value("${abuse.escalation.long-ban-threshold}") int longBanThreshold,
            @Value("${abuse.escalation.temp-block-seconds}") long tempBlockSeconds,
            @Value("${abuse.escalation.long-ban-seconds}") long longBanSeconds) {
        this.redis = redis;
        this.windowMs = windowMs;
        this.tempBlockThreshold = tempBlockThreshold;
        this.longBanThreshold = longBanThreshold;
        this.tempBlockSeconds = tempBlockSeconds;
        this.longBanSeconds = longBanSeconds;
    }

    /** Record one violation for this key and return the action to take. */
    public Action escalate(String apiKey) {
        String counterKey = "abuse:violations:" + apiKey;
        Long count = redis.opsForValue().increment(counterKey);
        if (count != null && count == 1L) {
            redis.expire(counterKey, Duration.ofMillis(windowMs)); // start the rolling window
        }
        long c = (count == null) ? 1L : count;

        if (c >= longBanThreshold) {
            ban(apiKey, longBanSeconds);
            return Action.LONG_BAN;
        }
        if (c >= tempBlockThreshold) {
            ban(apiKey, tempBlockSeconds);
            return Action.TEMP_BLOCK;
        }
        return Action.LOG_ONLY;
    }

    public boolean isBanned(String apiKey) {
        return Boolean.TRUE.equals(redis.hasKey("abuse:ban:" + apiKey));
    }

    private void ban(String apiKey, long seconds) {
        redis.opsForValue().set("abuse:ban:" + apiKey, "1", Duration.ofSeconds(seconds));
    }
}