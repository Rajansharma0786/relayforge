CREATE TABLE delivery_jobs (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    destination VARCHAR(2048) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_delivery_created ON delivery_jobs(created_at);
