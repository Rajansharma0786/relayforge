# RelayForge

**A reliable webhook delivery platform, built incrementally in public.**

RelayForge will help applications accept events, deliver signed webhooks, recover from failures, and inspect delivery attempts. The first milestone is a dependency-free Python core with durable SQLite event acceptance and a working command-line demo.

## Working today

- Persist JSON events across process restarts.
- Deduplicate submissions using idempotency keys.
- Reject conflicting reuse of a key instead of silently changing the event.
- Accept concurrent submissions safely through a database uniqueness constraint.
- Inspect accepted events from the CLI.
- Validate event types, destinations, JSON payloads, and list limits.

Events currently remain `pending`. This version does not send network requests. Delivery workers, retry scheduling, an HTTP API, and a dashboard are planned.

## Quickstart

Requires Python 3.11 or newer with SQLite support. There are no third-party runtime or test dependencies.

```bash
git clone https://github.com/Rajansharma0786/relayforge.git
cd relayforge
export PYTHONPATH=src

python3 -m relayforge --help
python3 -m relayforge --db demo.db enqueue \
  --type invoice.paid \
  --url https://example.com/hooks \
  --payload '{"invoice_id":"inv_123","amount":4999}' \
  --key invoice-paid-inv_123

python3 -m relayforge --db demo.db list
python3 -m unittest discover -s tests -v
```

Repeating that enqueue command returns the same event ID. Reusing its key with a different event type, destination, or payload exits with an error. The URL is stored for future delivery; the demo sends no HTTP request.

Use `python3 -m relayforge --db demo.db show EVENT_ID` to inspect one event. Database files are ignored by Git.

## Development direction

The target platform adds an authenticated event API, leased workers, bounded retries, dead-letter replay, signed requests, delivery history, a React dashboard, and deployment documentation. Features are marked complete only after implementation and verification.

- [Architecture and design decisions](docs/ARCHITECTURE.md)
- [Prioritized roadmap](docs/ROADMAP.md)
- [Daily development process](docs/DEVELOPMENT.md)
- [Actual progress and validation](docs/PROGRESS.md)

## Project layout

```text
src/relayforge/
  __main__.py       Local demonstration CLI
  store.py          Durable event acceptance and idempotency
tests/              Behavioral and CLI integration tests
docs/               Architecture, roadmap, workflow, and progress
```

## Development and attribution

Created and maintained by [Rajan Sharma](https://github.com/Rajansharma0786) with AI assistance from Codex. Scheduled development may produce several meaningful commits in a day. Each commit describes actual work; the progress log records checks and limitations.

License: [MIT](LICENSE).
