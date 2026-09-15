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
 * Credential stuffing:
 * Flag a key when the fraction of its recent responses that are 401/403
 * exceeds R, measured over at least M attempts - the signature of someone
 * trying many credentials and mostly failing. The minimum-attempts floor
 * stops a couple of honest typos from tripping the rule.
 *
 * Runs POST_RESPONSE because it needs the status code. In-memory per instance.
 */
@Component
public class CredentialStuffingRule implements AbuseRule {

    private final long windowMs;
    private final int minAttempts;
    private final double failureRatio;

    private final ConcurrentHashMap<String, Deque<Entry>> windows = new ConcurrentHashMap<>();

    public CredentialStuffingRule(
            @Value("${abuse.credential-stuffing.window-ms}") long windowMs,
            @Value("${abuse.credential-stuffing.min-attempts}") int minAttempts,
            @Value("${abuse.credential-stuffing.failure-ratio}") double failureRatio) {
        this.windowMs = windowMs;
        this.minAttempts = minAttempts;
        this.failureRatio = failureRatio;
    }

    @Override public String name() { return "credential_stuffing"; }
    @Override public RulePhase phase() { return RulePhase.POST_RESPONSE; }

    @Override
    public Optional<String> evaluate(RequestContext ctx) {
        Integer status = ctx.responseStatus();
        if (status == null) {
            return Optional.empty();
        }
        boolean failure = (status == 401 || status == 403);
        Deque<Entry> window = windows.computeIfAbsent(ctx.apiKey(), k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new Entry(ctx.timestampMs(), failure));
            long cutoff = ctx.timestampMs() - windowMs;
            while (!window.isEmpty() && window.peekFirst().ts < cutoff) {
                window.removeFirst();
            }
            int attempts = window.size();
            long failures = window.stream().filter(e -> e.failure).count();
            if (attempts >= minAttempts && (double) failures / attempts > failureRatio) {
                return Optional.of(String.format("%d/%d auth failures in window", failures, attempts));
            }
            return Optional.empty();
        }
    }

    private record Entry(long ts, boolean failure) {}
}