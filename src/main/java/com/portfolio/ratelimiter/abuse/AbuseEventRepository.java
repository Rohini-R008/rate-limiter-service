package com.portfolio.ratelimiter.abuse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;


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

    public List<AbuseEventView> findRecent(int limit) {
        return jdbc.query(
            "SELECT id, event_time, api_key, rule_name, action_taken " +
            "FROM abuse_events ORDER BY event_time DESC, id DESC LIMIT ?",
            (rs, rowNum) -> new AbuseEventView(
                rs.getLong("id"),
                rs.getTimestamp("event_time").toInstant(),
                rs.getString("api_key"),
                rs.getString("rule_name"),
                rs.getString("action_taken")),
            limit);
    }

    public record AbuseEventView(long id, java.time.Instant eventTime,
                                 String apiKey, String ruleName, String actionTaken) {}
}
