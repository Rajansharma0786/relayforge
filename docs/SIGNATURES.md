# Webhook signature contract: v1

This module provides signing and verification primitives. Network delivery and receiver integration are still planned.

## Signed bytes and header

The message is ASCII Unix seconds, a literal period byte, then the exact HTTP request body bytes. Compute HMAC-SHA256 with the endpoint secret and encode the result as lowercase hexadecimal.

```text
message = ascii(timestamp) + b"." + body
RelayForge-Signature: t=<timestamp>,v1=<64 lowercase hex characters>
```

The verifier accepts exactly this single-signature format. Duplicate fields, additional fields, leading-zero timestamps, unsupported versions, whitespace, and malformed digests are rejected. Secret rotation and multi-signature headers need a future versioned extension.

## Python example

Run with PYTHONPATH=src from the repository root:

```python
import secrets
from relayforge.signing import sign_payload, verify_signature

secret = secrets.token_bytes(32)  # provision once and store securely in a real integration
body = b'{"event_id":"evt_123","type":"invoice.paid"}'
header = sign_payload(secret, body)
assert verify_signature(secret, body, header)
assert not verify_signature(secret, body + b" ", header)
```

Do not parse and reserialize a request before checking its signature. Even JSON whitespace changes the signed bytes. Verify first, then parse and process the payload. Never place secrets in Git or payload logs.

## Freshness and replay

The default freshness window is 300 seconds in either direction, including the boundary. A clock outside that window rejects the request. Receivers need synchronized clocks. Tolerance must be finite and nonnegative; test code can supply a fixed current timestamp.

Freshness does not prevent duplicate processing inside the accepted window. Receivers must persist and deduplicate event IDs. Secret distribution, rotation, receiver integration, and TLS/network protections are outside this module's current scope.

## Validation

The suite checks a fixed OpenSSL-generated reference vector, exact-byte tampering, a different secret, altered timestamps, past/future expiry, window boundaries, malformed headers, invalid configuration, and default clock behavior. It deliberately verifies that repeated fresh requests remain cryptographically valid, documenting the need for application-level deduplication.

Digest comparison uses Python's [hmac.compare_digest](https://docs.python.org/3/library/hmac.html#hmac.compare_digest), which avoids content-based short-circuit comparison.
