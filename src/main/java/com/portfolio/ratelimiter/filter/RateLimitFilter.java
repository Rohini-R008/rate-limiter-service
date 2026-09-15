package com.portfolio.ratelimiter.filter;

import com.portfolio.ratelimiter.abuse.AbuseDetectionService;
import com.portfolio.ratelimiter.abuse.EndpointCostResolver;
import com.portfolio.ratelimiter.abuse.EscalationService;
import com.portfolio.ratelimiter.abuse.RequestContext;
import com.portfolio.ratelimiter.abuse.RulePhase;
import com.portfolio.ratelimiter.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    // captures the {id} in /api/products/{id}
    private static final Pattern PRODUCT_ID = Pattern.compile("^/api/products/([^/]+)$");

    private final RateLimiter rateLimiter;
    private final String instanceId;
    private final EndpointCostResolver costResolver;
    private final AbuseDetectionService detection;
    private final EscalationService escalation;
    private final com.portfolio.ratelimiter.metrics.MetricsService metrics;

    public RateLimitFilter(RateLimiter rateLimiter,
                           @Value("${app.instance-id:local}") String instanceId,
                           EndpointCostResolver costResolver,
                           AbuseDetectionService detection,
                           EscalationService escalation,
                           com.portfolio.ratelimiter.metrics.MetricsService metrics) {
        this.rateLimiter = rateLimiter;
        this.instanceId = instanceId;
        this.costResolver = costResolver;
        this.detection = detection;
        this.escalation = escalation;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // dashboard endpoints are read-only and must not consume rate budget
        return request.getRequestURI().startsWith("/api/dashboard");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Instance-Id", instanceId);

        metrics.recordRequest();
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing " + API_KEY_HEADER + " header", null);
            return;
        }

        // 1. Already banned by a prior escalation? (shared across instances)
        if (escalation.isBanned(apiKey)) {
            reject(response, 403, "Temporarily blocked for abusive behavior", "60");
            return;
        }

        RequestContext ctx = buildContext(request, apiKey);

        // 2. Cost-weighted rate limit
        int cost = costResolver.costOf(request.getMethod(), request.getRequestURI());
        if (!rateLimiter.tryAcquire(apiKey, cost)) {
            reject(response, 429, "Rate limit exceeded", "60");
            return;
        }

        // 3. Pre-request abuse rules (velocity, enumeration). May set a ban.
        detection.runPhase(ctx, RulePhase.PRE_REQUEST);
        if (escalation.isBanned(apiKey)) {
            reject(response, 403, "Temporarily blocked for abusive behavior", "60");
            return;
        }

        // 4. Forward to the endpoint
        chain.doFilter(request, response);

        // 5. Post-response abuse rules (credential stuffing) using the status
        ctx.setResponseStatus(response.getStatus());
        detection.runPhase(ctx, RulePhase.POST_RESPONSE);
    }

    private RequestContext buildContext(HttpServletRequest request, String apiKey) {
        String path = request.getRequestURI();
        String resourceId = null;
        Matcher m = PRODUCT_ID.matcher(path);
        if (m.matches()) {
            resourceId = m.group(1);
        }
        return new RequestContext(apiKey, request.getRemoteAddr(),
                request.getMethod(), path, resourceId, System.currentTimeMillis());
    }

    private void reject(HttpServletResponse response, int status, String message,
                        String retryAfter) throws IOException {
        response.setStatus(status);
        if (retryAfter != null) {
            response.setHeader("Retry-After", retryAfter);
        }
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}