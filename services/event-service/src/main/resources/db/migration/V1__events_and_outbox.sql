CREATE TABLE events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    destination VARCHAR(2048) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(200) UNIQUE
);
CREATE TABLE event_outbox (
    event_id UUID PRIMARY KEY REFERENCES events(id),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    forwarded_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_due ON event_outbox(forwarded_at, next_attempt_at);
