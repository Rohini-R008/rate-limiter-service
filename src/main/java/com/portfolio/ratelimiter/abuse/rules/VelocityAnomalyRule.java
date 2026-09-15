package com.portfolio.ratelimiter.abuse.rules;

import com.portfolio.ratelimiter.abuse.AbuseRule;
import com.portfolio.ratelimiter.abuse.RulePhase;
import com.portfolio.ratelimiter.abuse.RequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity anomaly:
 * Flag a key when its request count in the current time bucket exceeds its
 * OWN recent average by more than N standard deviations - not a fixed global
 * threshold, so a naturally busy key isn't punished while a key that suddenly
 * spikes relative to its own history is.
 *
 * Per key we keep a rolling history of recent completed-bucket counts. Once
 * we have enough samples, we flag if current > mean + sigma * stddev. A small
 * stddev floor stops a perfectly steady key from flapping on a tiny increase.
 *
 * State is in-memory per instance (see PHASE3_NOTES for why detection state is
 * local while bans are shared in Redis).
 */
@Component
public class VelocityAnomalyRule implements AbuseRule {

    private final long bucketMs;
    private final int historySize;
    private final int minSamples;
    private final double sigma;
    private final double stdFloor;

    private final ConcurrentHashMap<String, KeyState> states = new ConcurrentHashMap<>();

    public VelocityAnomalyRule(
            @Value("${abuse.velocity.bucket-ms}") long bucketMs,
            @Value("${abuse.velocity.history-size}") int historySize,
            @Value("${abuse.velocity.min-samples}") int minSamples,
            @Value("${abuse.velocity.sigma}") double sigma,
            @Value("${abuse.velocity.std-floor}") double stdFloor) {
        this.bucketMs = bucketMs;
        this.historySize = historySize;
        this.minSamples = minSamples;
        this.sigma = sigma;
        this.stdFloor = stdFloor;
    }

    @Override public String name() { return "velocity_anomaly"; }
    @Override public RulePhase phase() { return RulePhase.PRE_REQUEST; }

    @Override
    public Optional<String> evaluate(RequestContext ctx) {
        KeyState st = states.computeIfAbsent(ctx.apiKey(), k -> new KeyState());
        synchronized (st) {
            long bucket = ctx.timestampMs() / bucketMs;
            if (bucket != st.currentBucket) {
                if (st.currentBucket != -1) {
                    st.history.addLast(st.currentCount);
                    while (st.history.size() > historySize) st.history.removeFirst();
                }
                st.currentBucket = bucket;
                st.currentCount = 0;
            }
            st.currentCount++;

            if (st.history.size() >= minSamples) {
                double mean = st.history.stream().mapToInt(i -> i).average().orElse(0);
                double var = st.history.stream()
                        .mapToDouble(i -> (i - mean) * (i - mean)).sum() / st.history.size();
                double std = Math.max(Math.sqrt(var), stdFloor);
                double threshold = mean + sigma * std;
                if (st.currentCount > threshold) {
                    return Optional.of(String.format(
                            "rate %d in bucket exceeds mean %.1f + %.1f*std %.1f",
                            st.currentCount, mean, sigma, std));
                }
            }
            return Optional.empty();
        }
    }

    private static class KeyState {
        final Deque<Integer> history = new ArrayDeque<>();
        long currentBucket = -1;
        int currentCount = 0;
    }
}