"""Dependency-free local demonstration: python -m relayforge --help."""

import argparse
from dataclasses import asdict
import json
import sqlite3

from .store import EventStore


def main() -> None:
    parser = argparse.ArgumentParser(description="Accept and inspect durable webhook events locally.")
    parser.add_argument("--db", default="relayforge.db", help="SQLite database file")
    commands = parser.add_subparsers(dest="command", required=True)
    enqueue = commands.add_parser("enqueue", help="Persist an event; no HTTP request is sent")
    enqueue.add_argument("--type", required=True, dest="event_type")
    enqueue.add_argument("--url", required=True)
    enqueue.add_argument("--payload", required=True, help="JSON object")
    enqueue.add_argument("--key", help="Optional idempotency key")
    show = commands.add_parser("show", help="Inspect an event")
    show.add_argument("id")
    listing = commands.add_parser("list", help="List newest accepted events")
    listing.add_argument("--limit", type=int, default=20)
    args = parser.parse_args()
    try:
        with EventStore(args.db) as store:
            if args.command == "enqueue":
                result = asdict(store.enqueue(args.event_type, args.url, json.loads(args.payload),
                                              idempotency_key=args.key))
            elif args.command == "show":
                event = store.get(args.id)
                if event is None:
                    parser.exit(1, "event not found\n")
                result = asdict(event)
            else:
                result = [asdict(event) for event in store.list_events(limit=args.limit)]
        print(json.dumps(result, indent=2, ensure_ascii=False))
    except (ValueError, sqlite3.Error, RuntimeError) as exc:
        parser.exit(2, f"error: {exc}\n")


if __name__ == "__main__":
    main()
