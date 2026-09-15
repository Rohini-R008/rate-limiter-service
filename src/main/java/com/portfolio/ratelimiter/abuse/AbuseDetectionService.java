package com.portfolio.ratelimiter.abuse;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs every AbuseRule for a given phase. On a trigger it writes an
 * abuse_events row and escalates (which may set a shared ban in Redis).
 * Spring injects every AbuseRule bean into the list automatically, so adding
 * a new rule is just adding a new @Component - no change here.
 */
@Service
public class AbuseDetectionService {

    private final List<AbuseRule> rules;
    private final AbuseEventRepository repository;
    private final EscalationService escalation;

    public AbuseDetectionService(List<AbuseRule> rules,
                                 AbuseEventRepository repository,
                                 EscalationService escalation) {
        this.rules = rules;
        this.repository = repository;
        this.escalation = escalation;
    }

    public void runPhase(RequestContext ctx, RulePhase phase) {
        for (AbuseRule rule : rules) {
            if (rule.phase() != phase) {
                continue;
            }
            rule.evaluate(ctx).ifPresent(detail -> {
                EscalationService.Action action = escalation.escalate(ctx.apiKey());
                repository.record(ctx.apiKey(), rule.name(), action.name());
            });
        }
    }
}