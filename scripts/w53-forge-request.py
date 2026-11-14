#!/usr/bin/env python3
"""W53 — the demonstration's forged request.

The mandate asks the acceptance scenario to show that a forged request is refused
(§13.25). This script sends one request to chain B with a signature that was made
with the WRONG secret, and it reads the peer's own answer.

Two things make the demonstration honest rather than decorative:

1. The signature is the only thing wrong with the message. The protocol, the version,
   the right and the canonical bytes are exactly what a genuine request would carry, so
   the peer's refusal cannot be about anything else.
2. The refusal's REASON is checked. The peer must refuse it because the signature does
   not verify — if it refuses for another reason, this script says so and exits with a
   failure, because a demonstration that proves the wrong thing is worse than no
   demonstration.

The canonical form is the one the bridge defines: the seven fields in a fixed order,
separated by a line feed, the signature excluded.

Usage:
    scripts/w53-forge-request.py <port> <secret>
"""

import hashlib
import hmac
import json
import sys
import urllib.error
import urllib.request

PROTOCOL = "matrix.bridge"
VERSION = 3
KIND = "INFERENCE_REQUEST"


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: w53-forge-request.py <port> <secret>", file=sys.stderr)
        return 2
    port = int(sys.argv[1])
    secret = sys.argv[2].encode("utf-8")

    request_id = "req-w53-forged"
    sent_at = "2026-11-09T12:00:00Z"
    sender_id = "chain-a-epistemic-core"
    # The payload is deliberately not a task: the bridge checks the signature BEFORE it
    # reads the payload, so a forged message is refused for the forgery alone. A payload
    # that were readable would only add a second thing to be wrong.
    payload = '{"kind":"INFERENCE_REQUEST","forged":true}'

    canonical = "\n".join(
        [PROTOCOL, str(VERSION), request_id, sent_at, sender_id, KIND, payload]
    )
    # The forgery: the right secret would produce a different signature.
    forged = hmac.new(b"a-secret-that-is-not-the-peers", canonical.encode("utf-8"),
                       hashlib.sha256).hexdigest()

    body = json.dumps(
        {
            "protocol": PROTOCOL,
            "version": VERSION,
            "requestId": request_id,
            "sentAt": sent_at,
            "senderId": sender_id,
            "kind": KIND,
            "payload": payload,
            "signature": forged,
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        f"http://127.0.0.1:{port}/v1/bridge/task",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            text = response.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8")
    except OSError as e:
        print(json.dumps({"error": f"the peer could not be reached: {e}"}))
        return 1

    try:
        answer = json.loads(text)
    except ValueError:
        print(json.dumps({"error": "the peer's answer could not be read", "raw": text}))
        return 1

    reason = answer.get("reason", "")
    if answer.get("status") != "REFUSED" or "signature" not in reason.lower():
        print(json.dumps({
            "error": "the peer did not refuse the forged request for its signature",
            "status": answer.get("status"),
            "reason": reason,
        }))
        return 1

    print(json.dumps({
        "status": answer.get("status"),
        "reason": reason,
        "auditId": answer.get("auditId"),
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
