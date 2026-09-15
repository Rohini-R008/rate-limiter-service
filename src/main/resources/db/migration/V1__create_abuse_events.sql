CREATE TABLE abuse_events (
    id           BIGSERIAL PRIMARY KEY,
    event_time   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    api_key      VARCHAR(255) NOT NULL,
    rule_name    VARCHAR(100) NOT NULL,
    action_taken VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_abuse_events_event_time ON abuse_events (event_time DESC);
CREATE INDEX idx_abuse_events_api_key    ON abuse_events (api_key);