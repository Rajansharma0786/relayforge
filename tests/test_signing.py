import unittest
from unittest.mock import patch

from relayforge.signing import sign_payload, verify_signature


class SigningTests(unittest.TestCase):
    secret = b"test-secret"
    body = b'{"event":"invoice.paid"}'
    timestamp = 1700000000

    def header(self):
        return sign_payload(self.secret, self.body, timestamp=self.timestamp)

    def test_matches_independent_openssl_reference_vector(self):
        # openssl dgst -sha256 -hmac test-secret over the exact timestamp.body bytes.
        expected = "t=1700000000,v1=f6f4b00b62e5d72c8059b70a555be877bdae3c7cb9fd766d3334e791802bb995"
        self.assertEqual(self.header(), expected)
        self.assertTrue(verify_signature(self.secret, self.body, expected, now=self.timestamp))

    def test_roundtrip_supports_empty_and_non_ascii_bytes(self):
        for body in [b"", '{"message":"hello 🌍"}'.encode()]:
            with self.subTest(body=body):
                header = sign_payload(self.secret, body, timestamp=self.timestamp)
                self.assertTrue(verify_signature(self.secret, body, header, now=self.timestamp))

    def test_body_tampering_and_json_reserialization_are_rejected(self):
        for body in [b'{"event":"invoice.failed"}', b'{ "event": "invoice.paid" }']:
            with self.subTest(body=body):
                self.assertFalse(verify_signature(self.secret, body, self.header(), now=self.timestamp))

    def test_wrong_secret_is_rejected(self):
        self.assertFalse(verify_signature(b"different-secret", self.body, self.header(), now=self.timestamp))

    def test_changed_timestamp_invalidates_mac(self):
        changed = self.header().replace("1700000000", "1700000001")
        self.assertFalse(verify_signature(self.secret, self.body, changed, now=self.timestamp))

    def test_expired_and_far_future_requests_are_rejected(self):
        for now in [self.timestamp - 301, self.timestamp + 301]:
            with self.subTest(now=now):
                self.assertFalse(verify_signature(self.secret, self.body, self.header(), now=now))

    def test_freshness_boundary_is_inclusive(self):
        for now in [self.timestamp - 300, self.timestamp + 300]:
            with self.subTest(now=now):
                self.assertTrue(verify_signature(self.secret, self.body, self.header(), now=now))

    def test_malformed_or_ambiguous_headers_are_rejected(self):
        valid = self.header()
        headers = [None, "", valid + ",v1=" + "0" * 64, valid + ",t=1700000000",
                   valid + "\n", " " + valid, valid.replace("v1=", "v2="),
                   valid.replace("t=170", "t=0170"), "t=-1,v1=" + "0" * 64,
                   "t=9999999999999999999,v1=" + "0" * 64, "x" * 1000,
                   "t=1700000000,v1=" + "é" * 64]
        for header in headers:
            with self.subTest(header=header):
                self.assertFalse(verify_signature(self.secret, self.body, header, now=self.timestamp))

    def test_invalid_secret_or_body_is_a_configuration_error(self):
        for secret, body in [(b"", self.body), ("secret", self.body), (self.secret, "text")]:
            with self.subTest(secret=secret, body=body), self.assertRaises(ValueError):
                sign_payload(secret, body, timestamp=self.timestamp)
            with self.assertRaises(ValueError):
                verify_signature(secret, body, self.header(), now=self.timestamp)

    def test_invalid_freshness_configuration_is_rejected(self):
        for tolerance in [-1, True, float("inf"), float("nan"), "300"]:
            with self.subTest(tolerance=tolerance), self.assertRaises(ValueError):
                verify_signature(self.secret, self.body, self.header(), now=self.timestamp, tolerance=tolerance)
        for now in [True, "now", float("inf"), float("nan")]:
            with self.subTest(now=now), self.assertRaises(ValueError):
                verify_signature(self.secret, self.body, self.header(), now=now)

    def test_invalid_signing_timestamp_is_rejected(self):
        for timestamp in [-1, 2**63, 1.5, True, "1700000000"]:
            with self.subTest(timestamp=timestamp), self.assertRaises(ValueError):
                sign_payload(self.secret, self.body, timestamp=timestamp)

    def test_default_clock_and_zero_tolerance(self):
        with patch("relayforge.signing.time.time", return_value=self.timestamp):
            self.assertEqual(sign_payload(self.secret, self.body), self.header())
            self.assertTrue(verify_signature(self.secret, self.body, self.header(), tolerance=0))
        self.assertFalse(verify_signature(self.secret, self.body, self.header(),
                                          now=self.timestamp + 0.1, tolerance=0))

    def test_freshness_alone_does_not_deduplicate_requests(self):
        for _ in range(2):
            self.assertTrue(verify_signature(self.secret, self.body, self.header(), now=self.timestamp))


if __name__ == "__main__":
    unittest.main()
