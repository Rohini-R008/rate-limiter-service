package com.portfolio.ratelimiter.abuse.rules;

import com.portfolio.ratelimiter.abuse.AbuseRule;
import com.portfolio.ratelimiter.abuse.RulePhase;
import com.portfolio.ratelimiter.abuse.RequestContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endpoint enumeration:
 * Flag a key that requests more than T DISTINCT resource IDs within a short
 * window - the signature of someone walking /products/1, /products/2, ... to
 * scrape or map the catalog. Hammering a single ID many times does NOT trip
 * this rule; only breadth of distinct IDs does.
 *
 * Per key we keep a sliding window of (timestamp, resourceId), evict entries
 * older than the window, and count distinct IDs. In-memory per instance.
 */
@Component
public class EndpointEnumerationRule implements AbuseRule {

    private final long windowMs;
    private final int distinctThreshold;

    private final ConcurrentHashMap<String, Deque<Entry>> windows = new ConcurrentHashMap<>();

    public EndpointEnumerationRule(
            @Value("${abuse.enumeration.window-ms}") long windowMs,
            @Value("${abuse.enumeration.distinct-threshold}") int distinctThreshold) {
        this.windowMs = windowMs;
        this.distinctThreshold = distinctThreshold;
    }

    @Override public String name() { return "endpoint_enumeration"; }
    @Override public RulePhase phase() { return RulePhase.PRE_REQUEST; }

    @Override
    public Optional<String> evaluate(RequestContext ctx) {
        if (ctx.resourceId() == null) {
            return Optional.empty();   // no resource id on this path, nothing to enumerate
        }
        Deque<Entry> window = windows.computeIfAbsent(ctx.apiKey(), k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new Entry(ctx.timestampMs(), ctx.resourceId()));
            long cutoff = ctx.timestampMs() - windowMs;
            while (!window.isEmpty() && window.peekFirst().ts < cutoff) {
                window.removeFirst();
            }
            Set<String> distinct = new HashSet<>();
            for (Entry e : window) distinct.add(e.id);
            if (distinct.size() > distinctThreshold) {
                return Optional.of(distinct.size() + " distinct resource IDs in window");
            }
            return Optional.empty();
        }
    }

    private record Entry(long ts, String id) {}
}