# Architecture and reliability guarantees

## Executable boundaries

| Component | Port | Responsibility | Storage |
| --- | --- | --- | --- |
| API gateway | 8080 | Route /api/events and /api/deliveries | None |
| Event service | 8081 | Accept, validate, deduplicate, and forward events | Own events/outbox database |
| Delivery service | 8082 | Accept forwarded events and persist jobs | Own delivery database |
| Dashboard | 5173 | Event creation and live operations views | Server state through TanStack Query |

The gateway uses Spring Cloud Gateway on Reactor Netty. Stateful services use Spring MVC, JDBC transactions, and Java 21 virtual threads. These are separate processes and Maven modules with independent application configuration.

Service URLs come from environment variables or Compose DNS. Local services bind to 127.0.0.1. Compose makes only the gateway and dashboard available on loopback host ports.

## Event acceptance and forwarding

1. Validate the create-event request and normalize nested JSON object-key ordering.
2. Insert the event and one outbox row in the same database transaction.
3. Commit before returning HTTP 201.
4. A scheduled dispatcher finds due outbox rows and calls delivery-service internal intake.
5. The receiver inserts a delivery job identified by the event UUID.
6. Only after a successful response does the event service mark the outbox row forwarded and the event FORWARDED.

A uniqueness constraint on the optional idempotency key arbitrates concurrent submissions. The losing transaction is rolled back before reading the existing event. Identical event type, destination, and normalized payload return the original event. Conflicting reuse returns HTTP 409.

An unavailable delivery service leaves the accepted event intact. Forwarding uses a three-second connect timeout, five-second read timeout, and increasing retry delays capped at 60 seconds. Backoff does not currently include jitter.

A crash after the delivery service commits but before the event service records acknowledgement can cause another forward. The receiver deduplicates by event UUID and rejects conflicting reuse. This is at-least-once forwarding with an idempotent consumer, not exactly-once processing.

Multiple dispatcher replicas may forward the same due row. Receiver deduplication preserves one job; row leases/fencing and more efficient parallel polling are future work.

## Data ownership

Each stateful service has its own Flyway migration and database. The local profile uses separate file-backed H2 databases. The postgres profile uses PostgreSQL connection settings from the environment. Stateful services do not read each other's tables.

The initial schemas store payloads as text and expose a bounded newest-first listing. Request-size limits, retention policies, and workspace boundaries remain future API work.

## Internal authentication and external access

The delivery intake route requires X-RelayForge-Token and compares its bytes using MessageDigest.isEqual. The gateway does not publish /internal routes. Tokens are configuration, never event data.

Public event/read APIs currently target a loopback development environment. OIDC/OAuth2, workspace authorization, rate limiting, and managed secret rotation are required milestones for an externally exposed deployment. Current URL checks validate shape; outbound network-policy enforcement will be added with the actual delivery worker.

## Current queue semantics

A delivery job in PENDING has been accepted by the delivery service. The V2 migration backfills existing jobs as immediately eligible and adds attempt numbers, lease expiry, and delivery attempt history. It indexes pending jobs by due time and in-progress jobs by lease expiry. Each attempt number is unique within its job.

Worker claims and external HTTP delivery are the next milestones. The current service queues jobs in PENDING.

## Frontend boundary

The dashboard uses typed API functions and TanStack Query for server state. Queries support cancellation and refresh every five seconds. Mutations retain the user's idempotency key for safe retries. Malformed JSON and non-object payloads are rejected before submission; server validation remains authoritative.

## Current technology decisions

- Java 21 records for HTTP contracts and virtual threads for blocking request work.
- Spring Boot 4 with its default Jackson 3 integration.
- Spring Cloud release-train BOM to keep gateway dependencies compatible.
- Explicit JDBC transactions for event/outbox atomicity and database-level deduplication.
- Flyway migrations for schema ownership and repeatable deployment.
- A React SPA behind a gateway proxy, with a committed npm lockfile.
- Multi-stage container builds and CI covering Java, frontend, and real service communication.

References: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Cloud version compatibility](https://spring.io/projects/spring-cloud), and [Jackson integration](https://docs.spring.io/spring-boot/4.0/reference/features/json.html).
