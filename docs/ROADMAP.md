# Prioritized development roadmap

This is an ordered backlog for roughly twelve weeks of regular development, not a promise that each milestone fits a calendar week. A daily session can include multiple complete changes and multiple commits. Larger items may take several sessions.

## 1. Event acceptance foundation

- [x] Durable SQLite event ledger with schema-version checks.
- [x] Idempotent acceptance and explicit conflict errors.
- [x] CLI enqueue, show, and list with regression tests.
- [ ] Timestamped HMAC signing and receiver verification.
- [ ] CI that runs the dependency-free suite on supported Python versions.

Acceptance: local quickstart works, accepted events survive a restart, concurrent retries produce one event, and signature verification rejects tampering.

## 2. Worker ownership and crash recovery

- [ ] Add a versioned migration for pending, in-flight, delivered, and dead-letter states.
- [ ] Implement atomic claim and lease expiry.
- [ ] Fence acknowledgements with claim tokens.
- [ ] Test competing workers and recovery from expired leases.

Acceptance: two workers do not own a valid lease simultaneously, and an old worker cannot acknowledge a reclaimed job.

## 3. HTTP delivery

- [ ] Add a transport interface and deterministic fake transport.
- [ ] Implement bounded HTTP timeouts and response classification.
- [ ] Enforce outbound network policy, resolved-IP checks, and redirect restrictions.
- [ ] Send the event ID and signature on each attempt.
- [ ] Exercise a local receiver in end-to-end tests.

Acceptance: one accepted event reaches a controlled receiver with verifiable bytes and headers; failures remain observable.

## 4. Retries and dead letters

- [ ] Implement bounded backoff with jitter and retry budgets.
- [ ] Separate permanent failures from transient failures.
- [ ] Add attempt history and safe dead-letter replay.
- [ ] Test crash windows and duplicate delivery behavior.

Acceptance: exhausted jobs stop retrying, retries follow the recorded schedule, and replay preserves an audit trail.

## 5. Authenticated API

- [ ] Define an API contract and request-size limits.
- [ ] Add authenticated event intake and idempotency headers.
- [ ] Add event status and paginated attempt-history endpoints.
- [ ] Add consistent validation and error responses.

Acceptance: unauthorized requests fail, valid events persist, and repeated requests honor the documented idempotency contract.

## 6. Endpoint and secret management

- [ ] Add endpoint registration and workspace ownership.
- [ ] Introduce signing-secret provisioning and rotation.
- [ ] Redact secrets and sensitive payloads from diagnostics.
- [ ] Test cross-workspace access denial.

Acceptance: one workspace cannot inspect or replay another workspace's events, and rotation supports a documented overlap window.

## 7. Dashboard foundation

- [ ] Build a React/TypeScript interface against the real API.
- [ ] Add an event list with pending, delivered, and failed states.
- [ ] Add event details and attempt history.
- [ ] Include loading, empty, and actionable error states.

Acceptance: the dashboard reflects actual persisted events and a failed delivery can be diagnosed.

## 8. Operations and observability

- [ ] Add health/readiness checks and correlation IDs.
- [ ] Record queue age, attempt latency, success rate, and dead-letter count.
- [ ] Add structured logs without payload or secret leakage.
- [ ] Document operational troubleshooting.

Acceptance: the project can explain why a known failed event did not arrive.

## 9. Storage and deployment

- [ ] Add a PostgreSQL persistence adapter with equivalent contract tests.
- [ ] Add reproducible application containers and development Compose.
- [ ] Add tested migrations, backup, and restore instructions.
- [ ] Verify local and container quickstarts.

Acceptance: a fresh checkout can start a working stack using documented commands.

## 10. Quality and performance

- [ ] Add reproducible load scenarios using synthetic payloads.
- [ ] Measure throughput, latency, and queue recovery.
- [ ] Fix measured bottlenecks and publish before/after results.
- [ ] Add pagination and retention policies.

Acceptance: performance claims have a workload, machine description, and reproducible command.

## 11. Developer experience

- [ ] Add a receiver example and language-neutral signing examples.
- [ ] Add a guided local demo with realistic failure/recovery scenarios.
- [ ] Improve keyboard navigation and accessibility.
- [ ] Capture screenshots from the working product.

Acceptance: another developer can complete the demo without undocumented manual steps.

## 12. Portfolio release

- [ ] Write a case study with tradeoffs, diagrams, and measured results.
- [ ] Verify all README claims against running features.
- [ ] Publish a tagged release and changelog.
- [ ] Prioritize the next backlog from real defects and usability gaps.

Acceptance: the project has a reproducible release, honest limitations, useful documentation, and an understandable commit history.
