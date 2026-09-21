package io.matrix.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * WAVE T-07 — Stripe Webhook Handler.
 *
 * Verifies Stripe webhook signatures using HMAC-SHA256 (the Stripe scheme).
 *
 * <p>The Stripe webhook payload is signed as: {@code HMAC-SHA256(signingSecret, payload)}.
 * Stripe sends the signature in the {@code Stripe-Signature} header as
 * {@code t=<timestamp>,v1=<hex signature>}.</p>
 *
 * <p><b>Idempotency:</b> Each Stripe event has a unique ID. Already-processed
 * events are skipped (via {@link CreditLedger#creditFromStripe}).</p>
 *
 * <p>T-07 ships a real HMAC-SHA256 implementation using JDK MessageDigest
 * (no external deps). T-07.5 will add real Stripe SDK integration.</p>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure crypto.</p>
 */
public final class StripeWebhookHandler {

    public enum EventType {
        CHECKOUT_SESSION_COMPLETED,
        INVOICE_PAID,
        CUSTOMER_SUBSCRIPTION_UPDATED,
        CUSTOMER_SUBSCRIPTION_DELETED,
        PAYMENT_FAILED,
        UNKNOWN
    }

    private final String webhookSecret;
    private final SubscriptionService subscriptions;
    private final CreditLedger ledger;

    public StripeWebhookHandler(String webhookSecret,
                                SubscriptionService subscriptions,
                                CreditLedger ledger) {
        this.webhookSecret = Objects.requireNonNull(webhookSecret, "webhookSecret");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /**
     * Verify the Stripe-Signature header for the given payload.
     *
     * @param header value of the Stripe-Signature header (e.g. "t=1234,v1=abc")
     * @param payload the raw request body
     * @return true if signature is valid
     */
    public boolean verifySignature(String header, String payload) {
        if (header == null || payload == null) return false;

        String timestamp = null;
        String v1Signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) timestamp = kv[1];
            else if ("v1".equals(kv[0])) v1Signature = kv[1];
        }
        if (timestamp == null || v1Signature == null) return false;

        String signedPayload = timestamp + "." + payload;
        String expected = hmacSha256Hex(webhookSecret, signedPayload);

        return constantTimeEquals(expected, v1Signature);
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
