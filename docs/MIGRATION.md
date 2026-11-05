# Migration to the requested Java and TypeScript stack

The repository's active application is now a Maven multi-module Spring Boot project with a React/TypeScript frontend.

The earlier Python package, pyproject metadata, and Python test suite have been removed. Java code owns event acceptance, outbox persistence, internal delivery intake, and webhook signature primitives. The frontend now uses real HTTP APIs instead of a local-only CLI.

## Contract changes

- Event requests use eventType, destination, and payload.
- The idempotency key is an HTTP Idempotency-Key header.
- Event IDs are UUIDs and timestamps are ISO instants.
- Event status progresses from ACCEPTED to FORWARDED.
- Delivery jobs have their own PENDING state and database.
- Java HMAC signing preserves the prior v1 header and exact-byte message format.

Old SQLite files are not automatically imported into the new service databases. This is a replacement of an early unreleased prototype; no user event data was included in the repository.

Git history is retained. No prior commits were rewritten or backdated.
