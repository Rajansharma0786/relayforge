# Java microservices and React development roadmap

This ordered backlog replaces the original prototype plan. Daily sessions may deliver several coherent commits; large items may span sessions. Checked items are implemented, not aspirations.

## 1. Java and frontend foundation

- [x] Maven multi-module Java 21 / Spring Boot 4 project.
- [x] Separately executable event-service, delivery-service, and reactive API gateway.
- [x] Durable event acceptance and nested-payload idempotency.
- [x] Transactional outbox with retryable authenticated service forwarding.
- [x] Separate service data ownership and Flyway migrations.
- [x] PostgreSQL profile and local H2 profile.
- [x] Java HMAC signing and freshness verification.
- [x] React/TypeScript dashboard using real event/job APIs.
- [x] Java integration tests, frontend tests, and live cross-service smoke tests.
- [x] Multi-stage Dockerfiles, Compose definition, and GitHub Actions workflow.

## 2. Worker ownership and external delivery

- [ ] Add versioned delivery-state and attempt-history migrations.
- [ ] Implement atomic claims, lease expiry, and fencing tokens.
- [ ] Introduce a transport interface and deterministic receiver fixtures.
- [ ] Enforce resolved-address, redirect, payload-size, and timeout policies before outbound dispatch.
- [ ] Attach event IDs and HMAC signatures to exact outgoing bytes.
- [ ] Test competing workers, crashed workers, and duplicate external delivery.

Acceptance: one active claim owns each attempt; old claims cannot acknowledge new ownership; a controlled receiver gets a verifiable payload.

## 3. Retries and replay

- [ ] Classify transient and permanent failures.
- [ ] Add bounded exponential backoff, jitter, and retry budgets.
- [ ] Persist attempt outcomes and dead letters.
- [ ] Implement authorized replay with an audit trail.
- [ ] Add dashboard attempt details and retry states.

Acceptance: retries follow recorded schedules, terminal failures stop, and replay does not hide earlier attempts.

## 4. Identity and workspace boundaries

- [ ] Add Spring Security OAuth2/OIDC authentication at public boundaries.
- [ ] Resolve workspace membership and permissions server-side.
- [ ] Isolate event, job, endpoint, and replay access by workspace.
- [ ] Add endpoint registration and signing-secret provisioning/rotation.
- [ ] Test cross-workspace denial and expired sessions.

Acceptance: unauthorized callers cannot create or inspect protected resources.

## 5. API quality and frontend workflows

- [ ] Publish a versioned OpenAPI specification and contract checks.
- [ ] Generate or validate the TypeScript client against the contract.
- [ ] Add cursor pagination, filters, and endpoint/job detail views.
- [ ] Add accessible keyboard flows and end-to-end browser tests.
- [ ] Improve loading, empty, and recoverable error states using observed failures.

Acceptance: documented contracts and actual frontend requests stay aligned.

## 6. Event-driven scale

- [ ] Measure forwarding throughput and queue lag.
- [ ] Add claimed outbox batches for multi-replica efficiency.
- [ ] Evaluate Kafka or a broker-backed relay against measured requirements.
- [ ] Preserve consumer idempotency and schema compatibility if a broker is introduced.
- [ ] Add outage/recovery and contract-evolution scenarios.

Acceptance: any infrastructure change has measured benefits and tested recovery semantics.

## 7. Observability and resilience

- [ ] Add OpenTelemetry tracing and Micrometer metrics for queue lag and attempts.
- [ ] Propagate correlation IDs across gateway and services.
- [ ] Add rate limiting and circuit breaking where measured failures justify it.
- [ ] Redact payloads and secrets from logs.
- [ ] Add an operations guide and actionable dashboards.

Acceptance: a failed event can be traced across service boundaries without exposing sensitive data.

## 8. Deployment and portfolio release

- [ ] Exercise container builds and PostgreSQL migrations in a clean environment.
- [ ] Add backup, restore, retention, and migration-upgrade checks.
- [ ] Add Kubernetes manifests when deployment requirements justify them.
- [ ] Capture actual dashboard screenshots and a working failure/recovery demo.
- [ ] Publish a case study with design tradeoffs and reproducible benchmarks.
- [ ] Tag a release with verified quickstarts and known limitations.

Acceptance: another developer can start the stack, demonstrate its guarantees, and understand the remaining scope.
