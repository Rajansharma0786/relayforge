"""Durable event acceptance. Delivery workers are a separate roadmap milestone."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
import sqlite3
import time
from typing import Callable
from urllib.parse import urlsplit
from uuid import uuid4


class IdempotencyConflict(ValueError):
    """An idempotency key was reused for a different event."""


@dataclass(frozen=True)
class Event:
    id: str
    event_type: str
    destination: str
    payload: dict
    status: str
    created_at: float
    idempotency_key: str | None


class EventStore:
    """SQLite-backed event store; use one instance per thread or process.

    A unique constraint arbitrates concurrent submissions with the same key.
    Returning from enqueue means the insert transaction has committed.
    """

    def __init__(self, path: str | Path, *, clock: Callable[[], float] = time.time):
        self._clock = clock
        self._db = sqlite3.connect(str(path), timeout=10, isolation_level="IMMEDIATE")
        self._db.row_factory = sqlite3.Row
        try:
            version = self._db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1):
                raise RuntimeError(f"Unsupported database schema version: {version}")
            with self._db:
                self._db.execute("""
                    CREATE TABLE IF NOT EXISTS events (
                        id TEXT PRIMARY KEY,
                        event_type TEXT NOT NULL,
                        destination TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'pending' CHECK(status = 'pending'),
                        created_at REAL NOT NULL,
                        idempotency_key TEXT UNIQUE
                    )
                """)
                self._db.execute("PRAGMA user_version = 1")
        except BaseException:
            self._db.close()
            raise

    def __enter__(self) -> EventStore:
        return self

    def __exit__(self, *_exc: object) -> None:
        self.close()

    def close(self) -> None:
        self._db.close()

    def enqueue(self, event_type: str, destination: str, payload: dict,
                *, idempotency_key: str | None = None) -> Event:
        if not isinstance(event_type, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,100}", event_type):
            raise ValueError("event_type must contain 1-100 letters, digits, dots, dashes or underscores")
        self._validate_destination(destination)
        if not isinstance(payload, dict):
            raise ValueError("payload must be a JSON object")
        try:
            body = json.dumps(payload, sort_keys=True, separators=(",", ":"), allow_nan=False)
        except (TypeError, ValueError) as exc:
            raise ValueError("payload must contain valid JSON values") from exc
        if idempotency_key is not None and (
            not isinstance(idempotency_key, str) or not idempotency_key.strip() or len(idempotency_key) > 200
        ):
            raise ValueError("idempotency_key must be a nonblank string of at most 200 characters")

        event_id = str(uuid4())
        # Insert before lookup: SQLite serializes writers, avoiding a check-then-insert race.
        with self._db:
            self._db.execute("""
                INSERT INTO events (id, event_type, destination, payload, created_at, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(idempotency_key) DO NOTHING
            """, (event_id, event_type, destination, body, self._clock(), idempotency_key))
            if idempotency_key is None:
                row = self._db.execute("SELECT * FROM events WHERE id = ?", (event_id,)).fetchone()
            else:
                row = self._db.execute("SELECT * FROM events WHERE idempotency_key = ?",
                                       (idempotency_key,)).fetchone()
                if (row["event_type"], row["destination"], row["payload"]) != (event_type, destination, body):
                    raise IdempotencyConflict("idempotency_key already belongs to a different event")
            result = self._event(row)
        return result

    def get(self, event_id: str) -> Event | None:
        row = self._db.execute("SELECT * FROM events WHERE id = ?", (event_id,)).fetchone()
        return self._event(row) if row is not None else None

    def list_events(self, *, limit: int = 20) -> list[Event]:
        if type(limit) is not int or not 1 <= limit <= 100:
            raise ValueError("limit must be an integer between 1 and 100")
        rows = self._db.execute("SELECT * FROM events ORDER BY created_at DESC, rowid DESC LIMIT ?",
                                (limit,)).fetchall()
        return [self._event(row) for row in rows]

    @staticmethod
    def _event(row: sqlite3.Row) -> Event:
        values = dict(row)
        values["payload"] = json.loads(values["payload"])
        return Event(**values)

    @staticmethod
    def _validate_destination(destination: str) -> None:
        if not isinstance(destination, str) or any(c.isspace() for c in destination):
            raise ValueError("destination must be an absolute HTTP(S) URL without whitespace")
        try:
            parts = urlsplit(destination)
            port = parts.port  # validate malformed or out-of-range ports
            valid = parts.scheme in {"http", "https"} and parts.hostname and not (
                parts.username is not None or parts.password is not None or parts.fragment
            ) and port != 0
        except ValueError:
            valid = False
        if not valid:
            raise ValueError("destination must be an absolute HTTP(S) URL without credentials or a fragment")
