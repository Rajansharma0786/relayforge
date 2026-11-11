ALTER TABLE delivery_jobs ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE delivery_jobs ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE delivery_jobs ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE delivery_jobs SET next_attempt_at = created_at;
ALTER TABLE delivery_jobs ALTER COLUMN next_attempt_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE delivery_jobs ALTER COLUMN next_attempt_at SET NOT NULL;

ALTER TABLE delivery_jobs ADD CONSTRAINT ck_delivery_status
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DELIVERED', 'DEAD_LETTERED'));
ALTER TABLE delivery_jobs ADD CONSTRAINT ck_delivery_attempt_count
    CHECK (attempt_count >= 0);
ALTER TABLE delivery_jobs ADD CONSTRAINT ck_delivery_lease_state
    CHECK (
        (status = 'IN_PROGRESS' AND lease_expires_at IS NOT NULL AND attempt_count > 0)
        OR (status <> 'IN_PROGRESS' AND lease_expires_at IS NULL)
    );

CREATE INDEX idx_delivery_due ON delivery_jobs(status, next_attempt_at, event_id);
CREATE INDEX idx_delivery_lease ON delivery_jobs(status, lease_expires_at, event_id);

CREATE TABLE delivery_attempts (
    event_id UUID NOT NULL REFERENCES delivery_jobs(event_id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(24),
    http_status INTEGER CHECK (http_status BETWEEN 100 AND 599),
    PRIMARY KEY (event_id, attempt_number),
    CONSTRAINT ck_delivery_attempt_outcome CHECK (
        outcome IN ('SUCCEEDED', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE', 'LEASE_EXPIRED')
    ),
    CONSTRAINT ck_delivery_attempt_completion CHECK (
        (outcome IS NULL AND finished_at IS NULL AND http_status IS NULL)
        OR (outcome IS NOT NULL AND finished_at IS NOT NULL AND finished_at >= started_at)
    )
);
