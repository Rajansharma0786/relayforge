# Java webhook signature contract: v1

The implementation is WebhookSignatures in delivery-service. These primitives are tested; outbound worker integration is future work.

## Format and exact bytes

```text
message = ascii(unixSeconds) + "." + exactRequestBodyBytes
RelayForge-Signature: t=<timestamp>,v1=<64 lowercase HMAC-SHA256 hex characters>
```

Use sign(secretBytes, bodyBytes, timestampSeconds) to produce a header. Use verify(secretBytes, bodyBytes, header, currentInstant, allowedDuration) to validate it. Verification compares digest bytes using MessageDigest.isEqual.

The parser accepts one timestamp and one v1 digest in that order. It rejects duplicate fields, additional fields, leading-zero timestamps, unsupported versions, and malformed digests.

## Receiver behavior

Verify the exact request bytes before parsing JSON. Whitespace or reserialization changes the signature. Provision a random endpoint secret securely and do not place it in payloads, Git, or logs.

The allowed duration is a symmetric freshness window, including the boundary. It must be nonnegative. Callers supply the current Instant and Duration, making clock-dependent behavior deterministic in tests.

Freshness alone does not prevent processing the same event twice inside the window. Receivers must persist event IDs and deduplicate them. Secret rotation, TLS, and endpoint-network policy require additional integration.

## Validation

JUnit covers a fixed independently generated OpenSSL reference vector, changed body/secret/timestamp, past/future freshness boundaries, ambiguous headers, invalid configuration, and empty bodies.

The reference is HMAC-SHA256 with test-secret over the exact bytes:

```text
1700000000.{"event":"invoice.paid"}
```

Expected header:

```text
t=1700000000,v1=f6f4b00b62e5d72c8059b70a555be877bdae3c7cb9fd766d3334e791802bb995
```
