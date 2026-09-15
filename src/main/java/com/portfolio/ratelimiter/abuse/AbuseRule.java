package com.portfolio.ratelimiter.abuse;

import java.util.Optional;

/**
 * One abuse-detection rule. Each rule is its own class (no giant if/else):
 * the detection service just iterates over every AbuseRule bean Spring finds.
 */
public interface AbuseRule {

    /** Stable machine name, written to the abuse_events.rule_name column. */
    String name();

    /** Which phase this rule runs in. */
    RulePhase phase();

    /**
     * Evaluate the request. Returns a human-readable detail string if the
     * rule is triggered, or empty if the request looks fine.
     */
    Optional<String> evaluate(RequestContext ctx);
}