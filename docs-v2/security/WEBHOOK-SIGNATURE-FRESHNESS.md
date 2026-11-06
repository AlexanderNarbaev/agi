# Webhook Signature Freshness Policy

**Status:** Active — `StripeWebhookHandlerTest` enforces
**Applies to:** `matrix-billing` / `io.matrix.billing.StripeWebhookHandler`
**Introduced:** RECON-W38.4, 2026-10-26

## Scope

Outbound billing webhooks are signed by Stripe with HMAC-SHA256 and the
signature is delivered in the `Stripe-Signature` header as
`t=<unix seconds>,v1=<hex signature>`, over the payload
`t + "." + <raw body>`.

This policy governs **when a captured signature may still be accepted**.

## Threat

| Category | Threat | Control |
|---|---|---|
| **R**epudiation | A captured `(header, raw body)` pair is replayed later to re-trigger a billing side effect | Timestamp freshness window |
| **S**poofing | An attacker reuses an old signature to pass verification | Timestamp freshness window + HMAC over the raw body |
| **T**ampering | The body is altered after signing | HMAC covers the exact raw bytes; a modified body fails the HMAC check |
| **D**enial of Service | Header flooding with many `v1` candidates | Every candidate is compared; the count is bounded by the header size |

## Control

1. **Freshness window.** The signed timestamp must satisfy
   `|now - t| <= toleranceSeconds`. The default is
   `StripeWebhookHandler.DEFAULT_TOLERANCE_SECONDS = 300`, matching
   `stripe-java`'s `Webhook.DEFAULT_TOLERANCE`.
2. **The window cannot be disabled.** The constructor rejects a non-positive
   tolerance with `IllegalArgumentException`, and there is no configuration
   path that sets it to unbounded. A test asserts both rejected values.
3. **Malformed timestamps are rejected outright**, not treated as merely old:
   non-numeric, zero and negative timestamps all fail. A non-positive
   timestamp would otherwise make the tolerance window meaningless.
4. **Future timestamps are rejected** beyond the window. `stripe-java` tolerates
   them; this policy is deliberately stricter.
5. **Constant-time comparison is preserved.** Signature bytes are compared with
   a constant-time equality helper.
6. **Key rotation is supported.** Stripe sends one `v1` entry per active
   secret during rotation. The request is accepted when *any* candidate
   matches, and **every** candidate is compared without early exit, so the work
   performed does not depend on which candidate matched.
7. **Deterministic time.** The handler takes an injectable `java.time.Clock`.
   Tests use a fixed clock, so freshness assertions never depend on the wall
   clock at run time.
8. **No secret material is logged.** The class performs no logging at all.
   `verifySignatureDetailed` returns a non-secret failure *category*
   (`SignatureFailure`) so an operator can diagnose a rejection without
   handling authentication material.

## Freshness and idempotency are independent

Both are required, and neither substitutes for the other:

- **Freshness** stops an *old capture* from being accepted.
- **Idempotency** (`CreditLedger.creditFromStripe`, keyed on the Stripe event
  ID) stops a *fresh duplicate delivery* from applying a credit twice.

A test covers the combination: a signature that is still fresh is accepted
twice, yet the balance changes only once.

## Raw body requirement

The signature covers the exact bytes Stripe sent. A caller must pass the
unmodified request body. Re-serialising a parsed payload before verification
will fail the HMAC check — this is a property of the Stripe scheme, not a
MATRIX choice, and it is why the raw body must be retained until
verification completes.

## Verification

```bash
./gradlew :matrix-billing:test --no-daemon --console=plain
```

`StripeWebhookHandlerTest` — 26 tests, 0 failures — covers: valid recent
signature, expired signature, exactly-at-boundary, one second outside the
boundary, future timestamp, malformed timestamp, non-positive timestamp,
missing timestamp, missing signature, altered payload, missing header, missing
payload, rotation with multiple `v1` entries, all-invalid rotation candidates,
tolerance-cannot-be-disabled, default-tolerance value, idempotency, and
fresh-duplicate-does-not-double-credit.

## Known limitations

- **There is no production caller of `verifySignature` yet.** The handler is
  reachable through `BillingModule.webhook()`, but no REST endpoint in
  `matrix-api-gateway` or `matrix-core` currently receives a Stripe webhook.
  The control is therefore verified at component level, not yet on a live
  request path. Exposing the endpoint is tracked as a separate item; when it
  is added, it must pass the **raw** body to this handler and must not
  re-serialise it.
- **The Stripe Java SDK is not used.** The project has no Stripe SDK
  dependency (deliberately: `matrix-billing` depends on `jackson` and the JDK
  only). The custom implementation therefore carries the parity burden, which
  the rotation and boundary tests discharge. Migrating to the official SDK is
  an open option, not a current requirement.
- **Only one signing secret is configured.** Rotation on the Stripe side is
  handled by multiple `v1` entries; rotating the secret *value* on the MATRIX
  side would need a second accepted secret, which is not implemented.
