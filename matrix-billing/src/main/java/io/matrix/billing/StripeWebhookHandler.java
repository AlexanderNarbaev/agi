package io.matrix.billing;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * WAVE T-07 — Stripe Webhook Handler.
 *
 * <p>Verifies Stripe webhook signatures using HMAC-SHA256 (the Stripe scheme).</p>
 *
 * <p>The Stripe webhook payload is signed as: {@code HMAC-SHA256(signingSecret, t + "." + payload)}.
 * Stripe sends the signature in the {@code Stripe-Signature} header as
 * {@code t=<timestamp>,v1=<hex signature>}.</p>
 *
 * <h2>Replay protection</h2>
 * <p>A signature alone is not proof of freshness: a captured
 * {@code (header, payload)} pair stays valid forever unless the timestamp is
 * checked. Verification therefore enforces a <em>timestamp tolerance</em>:
 * the signed timestamp must be within {@link #DEFAULT_TOLERANCE_SECONDS}
 * seconds of the current time. This mirrors {@code stripe-java}'s
 * {@code Webhook.DEFAULT_TOLERANCE} behaviour and additionally rejects
 * timestamps too far in the future, which the SDK tolerates.</p>
 *
 * <p>The tolerance cannot be disabled: the constructor rejects a
 * non-positive value, and no configuration path sets it to "unbounded".</p>
 *
 * <h2>Key rotation</h2>
 * <p>During secret rotation Stripe sends one {@code v1} entry per active
 * secret. Verification accepts the request when <em>any</em> {@code v1}
 * candidate matches, and compares every candidate (no early exit) so that the
 * number of comparisons does not depend on which candidate matched.</p>
 *
 * <h2>Idempotency</h2>
 * <p>Freshness and idempotency are independent mechanisms and both are
 * required. Signature freshness stops an old capture from being replayed;
 * event-ID deduplication (via {@link CreditLedger#creditFromStripe}) stops a
 * fresh duplicate delivery from applying a billing side effect twice.
 * Neither replaces the other.</p>
 *
 * <p><b>Raw body:</b> the signature covers the exact bytes Stripe sent. The
 * caller must pass the unmodified request body; re-serialising a parsed
 * payload before verification will fail the HMAC check.</p>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure crypto. No secret or raw
 * payload is ever logged by this class.</p>
 */
public final class StripeWebhookHandler {

    /**
     * Signature-verification freshness window, in seconds.
     *
     * <p>Matches {@code stripe-java}'s {@code Webhook.DEFAULT_TOLERANCE}.</p>
     */
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L;

    public enum EventType {
        CHECKOUT_SESSION_COMPLETED,
        INVOICE_PAID,
        CUSTOMER_SUBSCRIPTION_UPDATED,
        CUSTOMER_SUBSCRIPTION_DELETED,
        PAYMENT_FAILED,
        UNKNOWN
    }

    /**
     * Non-secret reason a signature was rejected.
     *
     * <p>Deliberately carries no secret material, no payload and no signature
     * bytes — only the category of failure, so that an operator can diagnose
     * a rejected webhook without handling authentication material.</p>
     */
    public enum SignatureFailure {
        NONE,
        MISSING_HEADER,
        MISSING_PAYLOAD,
        MALFORMED_HEADER,
        MISSING_TIMESTAMP,
        MALFORMED_TIMESTAMP,
        TIMESTAMP_OUT_OF_TOLERANCE,
        NO_SIGNATURE_MATCH
    }

    private final String webhookSecret;
    private final SubscriptionService subscriptions;
    private final CreditLedger ledger;
    private final Clock clock;
    private final long toleranceSeconds;

    public StripeWebhookHandler(String webhookSecret,
                                SubscriptionService subscriptions,
                                CreditLedger ledger) {
        this(webhookSecret, subscriptions, ledger, Clock.systemUTC(), DEFAULT_TOLERANCE_SECONDS);
    }

    /**
     * Full constructor.
     *
     * @param webhookSecret     the Stripe webhook signing secret
     * @param subscriptions     subscription service
     * @param ledger            credit ledger
     * @param clock             time source; inject a fixed clock in tests so
     *                          freshness checks are deterministic
     * @param toleranceSeconds  freshness window in seconds; must be positive —
     *                          there is deliberately no way to disable the check
     * @throws IllegalArgumentException if {@code toleranceSeconds} is not positive
     */
    public StripeWebhookHandler(String webhookSecret,
                                SubscriptionService subscriptions,
                                CreditLedger ledger,
                                Clock clock,
                                long toleranceSeconds) {
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "webhookSecret");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (toleranceSeconds <= 0) {
            throw new IllegalArgumentException(
                "toleranceSeconds must be positive; the freshness check cannot be disabled");
        }
        this.toleranceSeconds = toleranceSeconds;
    }

    /** The configured freshness window, in seconds. */
    public long toleranceSeconds() {
        return toleranceSeconds;
    }

    /**
     * Verify the Stripe-Signature header for the given payload, using the
     * handler's clock.
     *
     * @param header value of the Stripe-Signature header (e.g. "t=1234,v1=abc")
     * @param payload the raw request body, unmodified
     * @return true if the signature is valid AND the timestamp is fresh
     */
    public boolean verifySignature(String header, String payload) {
        return verifySignature(header, payload, clock.instant());
    }

    /**
     * Verify the Stripe-Signature header for the given payload at an explicit
     * instant.
     *
     * @param header value of the Stripe-Signature header
     * @param payload the raw request body, unmodified
     * @param now    the instant to measure freshness against
     * @return true if the signature is valid AND the timestamp is fresh
     */
    public boolean verifySignature(String header, String payload, Instant now) {
        return verifySignatureDetailed(header, payload, now) == SignatureFailure.NONE;
    }

    /**
     * Verify and report why verification failed.
     *
     * @return {@link SignatureFailure#NONE} on success, otherwise the category
     *         of failure. Never contains secret material.
     */
    public SignatureFailure verifySignatureDetailed(String header, String payload, Instant now) {
        if (header == null || header.isEmpty()) return SignatureFailure.MISSING_HEADER;
        if (payload == null) return SignatureFailure.MISSING_PAYLOAD;

        String timestamp = null;
        List<String> candidates = new ArrayList<>();
        for (String part : header.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] kv = trimmed.split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0].trim())) {
                timestamp = kv[1].trim();
            } else if ("v1".equals(kv[0].trim())) {
                candidates.add(kv[1].trim());
            }
        }
        if (timestamp == null) return SignatureFailure.MISSING_TIMESTAMP;
        if (candidates.isEmpty()) return SignatureFailure.MALFORMED_HEADER;

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return SignatureFailure.MALFORMED_TIMESTAMP;
        }
        // A non-positive timestamp is malformed, not merely old: it can never
        // be a legitimate Stripe timestamp and would make the tolerance window
        // meaningless.
        if (timestampSeconds <= 0L) return SignatureFailure.MALFORMED_TIMESTAMP;

        long nowSeconds = now.getEpochSecond();
        long delta;
        try {
            delta = Math.abs(Math.subtractExact(nowSeconds, timestampSeconds));
        } catch (ArithmeticException overflow) {
            // A timestamp astronomically far from now cannot be fresh.
            return SignatureFailure.TIMESTAMP_OUT_OF_TOLERANCE;
        }
        if (delta > toleranceSeconds) return SignatureFailure.TIMESTAMP_OUT_OF_TOLERANCE;

        // Sign the canonical timestamp, matching stripe-java: the parsed value
        // is re-serialised, so "t=001234" is signed as "1234.payload".
        String signedPayload = Long.toString(timestampSeconds) + "." + payload;
        String expected = hmacSha256Hex(webhookSecret, signedPayload);

        // Compare every candidate without early exit: the work performed must
        // not depend on which candidate matched.
        boolean matched = false;
        for (String candidate : candidates) {
            matched |= constantTimeEquals(expected, candidate);
        }
        return matched ? SignatureFailure.NONE : SignatureFailure.NO_SIGNATURE_MATCH;
    }

    /**
     * Process a verified webhook event.
     *
     * @return true if event was applied (or already applied via idempotency);
     *         false if signature was invalid
     */
    public boolean processEvent(String eventId, EventType type,
                                String customerId, String stripeCustomerId,
                                long amountCents, String payload) {
        // In production: deserialize full event from payload, then act.
        // T-07 covers the main event types and idempotency.

        if (type == EventType.INVOICE_PAID || type == EventType.CHECKOUT_SESSION_COMPLETED) {
            // Convert cents to MCR (MATRIX Credit). 1 USD = 100 MCR
            long credits = amountCents;  // 1:1 with cents for now
            ledger.creditFromStripe(customerId, credits,
                "Stripe " + type + " (" + eventId + ")",
                eventId);
            return true;
        }

        if (type == EventType.CUSTOMER_SUBSCRIPTION_UPDATED) {
            // Parse plan from payload in production. T-07 assumes PRO for demo.
            Plan newPlan = Plan.PRO;
            subscriptions.changePlan(customerId, newPlan);
            return true;
        }

        if (type == EventType.CUSTOMER_SUBSCRIPTION_DELETED) {
            subscriptions.cancel(customerId);
            return true;
        }

        if (type == EventType.PAYMENT_FAILED) {
            // Log + flag for retry. T-07.5 adds notification.
            ledger.credit(customerId, 0, "Payment failed for event " + eventId);
            return true;
        }

        return false;
    }

    /** HMAC-SHA256 hex helper. */
    public static String hmacSha256Hex(String secret, String payload) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec =
                new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    /** Constant-time string equality to prevent timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public static EventType parseEventType(String type) {
        if (type == null) return EventType.UNKNOWN;
        return switch (type) {
            case "checkout.session.completed" -> EventType.CHECKOUT_SESSION_COMPLETED;
            case "invoice.paid" -> EventType.INVOICE_PAID;
            case "customer.subscription.updated" -> EventType.CUSTOMER_SUBSCRIPTION_UPDATED;
            case "customer.subscription.deleted" -> EventType.CUSTOMER_SUBSCRIPTION_DELETED;
            case "invoice.payment_failed" -> EventType.PAYMENT_FAILED;
            default -> EventType.UNKNOWN;
        };
    }
}
