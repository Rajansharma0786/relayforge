# Development progress

## 2026-11-05 — Java Spring Boot microservices and React migration

AI-assisted implementation with Codex, following the owner's requested stack.

### Backend

Replaced the prototype runtime with Java 21 / Spring Boot 4.1.2. Added three independently executable applications: event-service, delivery-service, and Spring Cloud Gateway. Implemented event validation and idempotency, atomic event/outbox persistence, HTTP forwarding with backoff, internal service-token checks, and an independently stored delivery queue.

Each stateful service owns its Flyway schema. Local development uses separate H2 files; PostgreSQL is configured through environment-based profiles. The Java signature module retains the v1 signing contract.

### Frontend

Added a React 19 / TypeScript 7 dashboard with TanStack Query, actual event and job listings, a validated event-creation form, idempotency replay feedback, automatic refresh, and service-error states. Added a committed lockfile and production build.

### Validation completed

- Maven verify: 21 Java tests passed across all three modules.
- Vitest: 7 frontend tests passed.
- TypeScript checking and Vite production build passed.
- Live smoke test against three packaged Java applications passed with H2.
- Live smoke test against three packaged Java applications passed with PostgreSQL 16.13; temporary test databases were removed.
- Docker Compose configuration validation passed.
- GitHub Actions defines Java/frontend checks plus H2 and PostgreSQL 17 smoke tests.

The local Docker daemon could not be accessed in this sandbox, so container images and the Compose runtime were not started locally. The supplied image definitions remain to be exercised in a clean Docker environment.

### Current limits and next work

PENDING delivery jobs have not been sent to their external destinations. Outbound workers, fenced leases, external retry/dead-letter behavior, OAuth2/OIDC workspace security, and distributed tracing remain planned.

Next: implement a tested delivery-state migration and worker leases, then controlled external delivery.

## Earlier prototype history

Two initial prototype commits implemented event persistence and signature primitives before the owner requested Java/Spring Boot and React/TypeScript. Those commits remain in Git history. Their source files, runtime metadata, test suite, and run instructions have been replaced; their historical test counts do not describe the current application.
