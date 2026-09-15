package com.portfolio.ratelimiter.abuse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes one row per triggered rule to the abuse_events audit table.
 * event_time defaults to now() in the database, so we don't set it here.
 * (Read queries for the dashboard come in Phase 4.)
 */
@Repository
public class AbuseEventRepository {

    private final JdbcTemplate jdbc;

    public AbuseEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String apiKey, String ruleName, String actionTaken) {
        jdbc.update(
            "INSERT INTO abuse_events (api_key, rule_name, action_taken) VALUES (?, ?, ?)",
            apiKey, ruleName, actionTaken);
    }
}