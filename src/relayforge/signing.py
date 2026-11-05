"""RelayForge v1 webhook signatures over a timestamp and exact request bytes."""

import hashlib
import hmac
import math
import re
import time

_HEADER = re.compile(r"t=(0|[1-9][0-9]{0,18}),v1=([0-9a-f]{64})")


def _validate_inputs(secret: bytes, body: bytes) -> None:
    if not isinstance(secret, bytes) or not secret:
        raise ValueError("secret must be nonempty bytes")
    if not isinstance(body, bytes):
        raise ValueError("body must be the exact request bytes")


def sign_payload(secret: bytes, body: bytes, *, timestamp: int | None = None) -> str:
    """Return t=<Unix seconds>,v1=<lowercase SHA-256 HMAC hex>."""
    _validate_inputs(secret, body)
    if timestamp is None:
        timestamp = int(time.time())
    if type(timestamp) is not int or not 0 <= timestamp <= 2**63 - 1:
        raise ValueError("timestamp must be a nonnegative signed 64-bit integer")
    message = str(timestamp).encode("ascii") + b"." + body
    digest = hmac.new(secret, message, hashlib.sha256).hexdigest()
    return f"t={timestamp},v1={digest}"


def verify_signature(secret: bytes, body: bytes, header: str, *,
                     now: float | None = None, tolerance: float = 300) -> bool:
    """Check the MAC and a symmetric freshness window.

    Freshness limits old requests; receivers must also deduplicate event IDs to
    prevent replays inside the accepted window. This function stores no state.
    """
    _validate_inputs(secret, body)
    if isinstance(tolerance, bool) or not isinstance(tolerance, (int, float)) or (
        not math.isfinite(tolerance) or tolerance < 0
    ):
        raise ValueError("tolerance must be a finite nonnegative number of seconds")
    current = time.time() if now is None else now
    if isinstance(current, bool) or not isinstance(current, (int, float)) or not math.isfinite(current):
        raise ValueError("now must be a finite Unix timestamp")
    if not isinstance(header, str) or len(header) > 100:
        return False
    match = _HEADER.fullmatch(header)
    if match is None:
        return False
    timestamp = int(match[1])
    if timestamp > 2**63 - 1 or abs(current - timestamp) > tolerance:
        return False
    expected = sign_payload(secret, body, timestamp=timestamp).split("v1=", 1)[1]
    return hmac.compare_digest(expected, match[2])
