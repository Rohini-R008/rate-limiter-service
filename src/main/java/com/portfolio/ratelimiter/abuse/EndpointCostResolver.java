package com.portfolio.ratelimiter.abuse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cost-weighted limiting (rule 4). Not an AbuseRule - it doesn't detect or
 * flag anything. It PRICES endpoints so the existing rate limiter charges
 * more tokens for expensive operations: a single POST /orders costs as much
 * as five GETs, so a burst of expensive calls drains the budget just as fast
 * as many cheap ones.
 */
@Component
public class EndpointCostResolver {

    private final int defaultCost;
    private final int postOrdersCost;

    public EndpointCostResolver(
            @Value("${abuse.cost.default}") int defaultCost,
            @Value("${abuse.cost.post-orders}") int postOrdersCost) {
        this.defaultCost = defaultCost;
        this.postOrdersCost = postOrdersCost;
    }

    public int costOf(String method, String path) {
        if ("POST".equalsIgnoreCase(method) && path.startsWith("/api/orders")) {
            return postOrdersCost;
        }
        return defaultCost;
    }
}