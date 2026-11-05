# Development and regular automation

## Required stack

Application development uses Java 21, Spring Boot microservices, React, and TypeScript. In-repository automation uses JavaScript and shell-compatible commands. The superseded Python application and test suite have been removed.

The daily Codex task targets Rajansharma0786/relayforge roughly once every 24 hours. No specific clock time is required. Multiple useful commits may be published in one session; commits should represent coherent, tested changes.

Local scheduled work requires the computer awake, Codex running, available credentials, and permitted network access.

## Commands

```bash
mvn verify
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run format:check
npm --prefix frontend run build
node scripts/smoke-test.mjs
docker compose config --quiet
```

The Java build verifies all modules and creates executable jars. Unit/integration tests use isolated in-memory H2 databases and actual HTTP requests. The smoke test starts all three packaged services on available ports and stops them afterwards. Logs go to ignored .smoke-logs/.

## PostgreSQL smoke variant

Create two disposable databases owned by a test role. The variant expects empty databases and does not delete them:

```bash
SMOKE_EVENTS_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/relayforge_events_test \
SMOKE_DELIVERIES_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/relayforge_deliveries_test \
SMOKE_DATABASE_USER=relayforge \
SMOKE_DATABASE_PASSWORD=your-local-test-password \
node scripts/smoke-test.mjs
```

CI provisions separate PostgreSQL 17 containers for these databases. Local PostgreSQL validation used temporary databases on PostgreSQL 16.13, followed by cleanup.

## Each scheduled session

1. Read repository instructions, the roadmap, architecture, API contract, and progress log.
2. Inspect local changes and remote main. Recover unpublished commits before implementing more work.
3. Respect existing user edits and never force-push.
4. Select feasible unfinished roadmap work, prioritizing regressions and broken checks.
5. Implement complete backend/frontend improvements; update behavioral tests and affected docs.
6. Run Maven verification, relevant frontend checks, and service smoke tests when integration changes.
7. Record actual changes, checks, limits, and next action. Mark only completed roadmap items done.
8. Review the diff, commit coherent changes with descriptive messages, and publish tested commits.
9. Verify the remote commit or PR and report a short result with links.

Use one regular session per Asia/Kolkata calendar date, while allowing publication recovery and user-requested additional work. There is no arbitrary daily commit quota.

If the execution environment restricts writes to the normal Maven cache, set maven.repo.local to a cache inside the allowed workspace. Do not change global tool configuration or credentials.

## Attribution and scope

Development is AI-assisted with Codex, including scheduled sessions. Do not fabricate test results, backdate commits, create date-only filler, or imply manual authorship of automated work.

Paid services, production deployment, repository deletion, force pushes, and unrelated repositories require separate authorization. If blocked, preserve work and report the precise failure.
