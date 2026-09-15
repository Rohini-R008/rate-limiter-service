package com.portfolio.ratelimiter.abuse;

/**
 * When a rule runs. Velocity and enumeration decide from the request alone,
 * so they run BEFORE forwarding. Credential stuffing needs the response
 * status (401/403), so it runs AFTER the downstream endpoint replies.
 */
public enum RulePhase {
    PRE_REQUEST,
    POST_RESPONSE
}