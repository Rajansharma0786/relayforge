# Progress log

## 2026-11-05 — New project foundation

AI-assisted development with Codex.

### Durable event acceptance

Implemented the SQLite event ledger, transaction-backed idempotency, input validation, schema version checks, and a CLI for accepting and inspecting events.

Validation: 16 automated tests passed, covering persistent reopen, duplicate and conflicting keys, 24 concurrent submissions through eight connections, invalid inputs, and CLI subprocess flows.

Current scope: events are stored as pending. No outbound HTTP requests, worker leases, retry scheduling, authentication, or dashboard are implemented.

Next: add timestamped signing and tamper/replay-window verification, then CI and leased workers.

### Timestamped payload signatures

Implemented exact-byte HMAC-SHA256 signing, a strict v1 signature header, constant-time digest comparison, and configurable freshness validation. Documented why receiver event-ID deduplication is still required.

Validation: 13 new signing tests passed; the complete suite reports 29 passed on Python 3.14.3. A fixed signature test vector was independently generated with OpenSSL. The suite covers tampering, wrong secrets, changed timestamps, freshness boundaries, malformed headers, and configuration failures. Other supported Python versions await the CI milestone.

Two coherent implementation commits were prepared in this initial session. No outbound delivery or hosted deployment was performed.

Next: add CI across supported Python versions, then a tested schema migration and worker leases.
