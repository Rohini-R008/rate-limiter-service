package com.portfolio.ratelimiter.dashboard;

import com.portfolio.ratelimiter.abuse.AbuseEventRepository;
import com.portfolio.ratelimiter.abuse.AbuseEventRepository.AbuseEventView;
import com.portfolio.ratelimiter.metrics.MetricsService;
import com.portfolio.ratelimiter.metrics.MetricsService.VolumePoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only dashboard API. No auth, no mutations - it only reports state.
 * These paths bypass the rate-limit filter (see RateLimitFilter.shouldNotFilter)
 * so dashboard polling doesn't consume anyone's rate budget.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AbuseEventRepository abuseEvents;
    private final MetricsService metrics;

    public DashboardController(DashboardService dashboardService,
                              AbuseEventRepository abuseEvents,
                              MetricsService metrics) {
        this.dashboardService = dashboardService;
        this.abuseEvents = abuseEvents;
        this.metrics = metrics;
    }

    @GetMapping("/rates")
    public List<DashboardService.KeyRate> rates() {
        return dashboardService.currentRates();
    }

    @GetMapping("/abuse-events")
    public List<AbuseEventView> abuseEvents(@RequestParam(defaultValue = "50") int limit) {
        return abuseEvents.findRecent(limit);
    }

    @GetMapping("/bans")
    public List<DashboardService.ActiveBan> bans() {
        return dashboardService.activeBans();
    }

    @GetMapping("/volume")
    public List<VolumePoint> volume(@RequestParam(defaultValue = "30") int buckets) {
        return metrics.recentVolume(buckets);
    }
}