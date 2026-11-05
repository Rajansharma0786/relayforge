# Progress log

## 2026-11-05 — New project foundation

AI-assisted development with Codex.

### Durable event acceptance

Implemented the SQLite event ledger, transaction-backed idempotency, input validation, schema version checks, and a CLI for accepting and inspecting events.

Validation: 16 automated tests passed, covering persistent reopen, duplicate and conflicting keys, 24 concurrent submissions through eight connections, invalid inputs, and CLI subprocess flows.

Current scope: events are stored as pending. No outbound HTTP requests, worker leases, retry scheduling, authentication, or dashboard are implemented.

Next: add timestamped signing and tamper/replay-window verification, then CI and leased workers.
