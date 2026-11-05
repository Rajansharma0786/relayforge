from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sqlite3
import tempfile
import unittest

from relayforge import EventStore, IdempotencyConflict


class EventStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / "events.db"
        self.store = EventStore(self.path, clock=lambda: 1000.0)
        self.addCleanup(self.store.close)

    def enqueue(self, **kwargs):
        args = dict(event_type="invoice.paid", destination="https://example.com/hooks",
                    payload={"invoice_id": "inv_123"})
        args.update(kwargs)
        return self.store.enqueue(**args)

    def test_event_survives_reopening_database(self):
        event = self.enqueue()
        with EventStore(self.path) as reopened:
            self.assertEqual(reopened.get(event.id), event)
        self.assertEqual(event.created_at, 1000.0)
        self.assertEqual(event.status, "pending")

    def test_missing_event_returns_none(self):
        self.assertIsNone(self.store.get("does-not-exist"))

    def test_same_key_and_body_replays_the_original_event(self):
        event = self.enqueue(idempotency_key="payment-123", payload={"a": 1, "b": 2})
        replay = self.enqueue(idempotency_key="payment-123", payload={"b": 2, "a": 1})
        self.assertEqual(replay, event)
        self.assertEqual(len(self.store.list_events()), 1)

    def test_key_reuse_for_changed_body_type_or_destination_is_rejected(self):
        event = self.enqueue(idempotency_key="same")
        for change in [dict(payload={"invoice_id": "different"}),
                       dict(event_type="invoice.created"),
                       dict(destination="https://example.com/other")]:
            with self.subTest(change=change), self.assertRaises(IdempotencyConflict):
                self.enqueue(idempotency_key="same", **change)
        self.assertEqual(self.store.get(event.id), event)
        self.assertEqual(len(self.store.list_events()), 1)

    def test_omitting_key_accepts_distinct_events(self):
        self.assertNotEqual(self.enqueue().id, self.enqueue().id)
        self.assertEqual(len(self.store.list_events()), 2)

    def test_concurrent_connections_share_one_idempotent_event(self):
        def submit(_):
            with EventStore(self.path) as store:
                return store.enqueue("invoice.paid", "https://example.com/hooks",
                                     {"invoice_id": "inv_123"}, idempotency_key="shared").id
        with ThreadPoolExecutor(max_workers=8) as pool:
            ids = list(pool.map(submit, range(24)))
        self.assertEqual(len(set(ids)), 1)
        self.assertEqual(len(self.store.list_events()), 1)

    def test_list_returns_newest_first_and_obeys_limit(self):
        self.enqueue(payload={"number": 1})
        latest = self.enqueue(payload={"number": 2})
        self.assertEqual(self.store.list_events(limit=1), [latest])

    def test_invalid_limit_is_rejected(self):
        for value in [0, -1, 101, True, "10", 2.5]:
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.store.list_events(limit=value)

    def test_invalid_destinations_are_rejected(self):
        for url in ["", "/relative", "ftp://example.com/a", "https://", "https://user:pass@example.com",
                    "https://example.com/#fragment", "https://example.com:99999", "https://[broken",
                    "https://example.com:0", "https://example.com/with space", None]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                self.enqueue(destination=url)
        self.assertEqual(self.store.list_events(), [])

    def test_invalid_event_types_are_rejected(self):
        for kind in ["", "has spaces", "a" * 101, None]:
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                self.enqueue(event_type=kind)

    def test_invalid_payloads_are_rejected(self):
        for payload in [[], "text", None, {"value": float("nan")}, {"value": object()}]:
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                self.enqueue(payload=payload)
        self.assertEqual(self.store.list_events(), [])

    def test_invalid_keys_are_rejected(self):
        for key in ["", " ", "a" * 201, 42]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.enqueue(idempotency_key=key)

    def test_newer_database_schema_is_not_silently_opened(self):
        other = Path(self.temp.name) / "newer.db"
        with sqlite3.connect(other) as connection:
            connection.execute("PRAGMA user_version = 2")
        with self.assertRaisesRegex(RuntimeError, "Unsupported database schema"):
            EventStore(other)


if __name__ == "__main__":
    unittest.main()
