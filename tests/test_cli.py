import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class CliTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.db = str(Path(self.temp.name) / "cli.db")

    def run_cli(self, *args):
        return subprocess.run([sys.executable, "-m", "relayforge", "--db", self.db, *args],
                              capture_output=True, text=True, check=False)

    def test_enqueue_show_and_list(self):
        accepted = self.run_cli("enqueue", "--type", "invoice.paid", "--url", "https://example.com/hooks",
                                "--payload", '{"invoice_id":"inv_123"}', "--key", "invoice-123")
        self.assertEqual(accepted.returncode, 0, accepted.stderr)
        event = json.loads(accepted.stdout)
        shown = self.run_cli("show", event["id"])
        self.assertEqual(shown.returncode, 0, shown.stderr)
        self.assertEqual(json.loads(shown.stdout), event)
        listed = self.run_cli("list")
        self.assertEqual(listed.returncode, 0, listed.stderr)
        self.assertEqual(json.loads(listed.stdout), [event])

    def test_malformed_json_is_an_actionable_error(self):
        result = self.run_cli("enqueue", "--type", "invoice.paid", "--url", "https://example.com",
                              "--payload", "{")
        self.assertEqual(result.returncode, 2)
        self.assertIn("error:", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_missing_event_has_nonzero_exit(self):
        result = self.run_cli("show", "missing")
        self.assertEqual(result.returncode, 1)
        self.assertIn("event not found", result.stderr)


if __name__ == "__main__":
    unittest.main()
