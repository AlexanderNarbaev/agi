package io.matrix.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stripe webhook verification.
 *
 * <p>All time-dependent assertions use an injected fixed {@link Clock}, so the
 * suite is deterministic and does not depend on the wall clock at run time.</p>
 */
class StripeWebhookHandlerTest {

    private CreditLedger ledger;
    private SubscriptionService subscriptions;
    private StripeWebhookHandler handler;
    private static final String WEBHOOK_SECRET = "whsec_test";

    /** Fixed "now" for every test: 2023-11-14T22:13:20Z. */
    private static final long NOW_SECONDS = 1_700_000_000L;
    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.ofEpochSecond(NOW_SECONDS), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        ledger = new CreditLedger();
        LicenseValidator validator = new LicenseValidator("test-secret");
        subscriptions = new SubscriptionService(ledger, validator, "test-secret");
        handler = new StripeWebhookHandler(WEBHOOK_SECRET, subscriptions, ledger,
            FIXED_CLOCK, StripeWebhookHandler.DEFAULT_TOLERANCE_SECONDS);
    }

    /** Build a header signed at {@code timestampSeconds} for {@code payload}. */
    private static String headerFor(long timestampSeconds, String payload) {
        String signedPayload = timestampSeconds + "." + payload;
        return "t=" + timestampSeconds + ",v1="
            + StripeWebhookHandler.hmacSha256Hex(WEBHOOK_SECRET, signedPayload);
    }

    @Test
    void testParseEventTypes() {
        assertEquals(StripeWebhookHandler.EventType.CHECKOUT_SESSION_COMPLETED,
            StripeWebhookHandler.parseEventType("checkout.session.completed"));
        assertEquals(StripeWebhookHandler.EventType.INVOICE_PAID,
            StripeWebhookHandler.parseEventType("invoice.paid"));
        assertEquals(StripeWebhookHandler.EventType.CUSTOMER_SUBSCRIPTION_UPDATED,
            StripeWebhookHandler.parseEventType("customer.subscription.updated"));
        assertEquals(StripeWebhookHandler.EventType.CUSTOMER_SUBSCRIPTION_DELETED,
            StripeWebhookHandler.parseEventType("customer.subscription.deleted"));
        assertEquals(StripeWebhookHandler.EventType.PAYMENT_FAILED,
            StripeWebhookHandler.parseEventType("invoice.payment_failed"));
        assertEquals(StripeWebhookHandler.EventType.UNKNOWN,
            StripeWebhookHandler.parseEventType("unknown.event"));
        assertEquals(StripeWebhookHandler.EventType.UNKNOWN,
            StripeWebhookHandler.parseEventType(null));
    }

    @Test
    void testHmacSha256() {
        String sig = StripeWebhookHandler.hmacSha256Hex("secret", "payload");
        assertEquals(64, sig.length());
        // Deterministic
        assertEquals(sig, StripeWebhookHandler.hmacSha256Hex("secret", "payload"));
        // Different payload => different sig
        assertNotEquals(sig, StripeWebhookHandler.hmacSha256Hex("secret", "different"));
    }

    @Test
    void testVerifyValidSignature() {
        String payload = "{\"id\":\"evt_1\",\"type\":\"invoice.paid\"}";
        String header = headerFor(NOW_SECONDS, payload);

        assertTrue(handler.verifySignature(header, payload));
    }

    @Test
    void testVerifyInvalidSignature() {
        String payload = "{}";
        String header = "t=" + NOW_SECONDS + ",v1=deadbeef" + "0".repeat(56);
        assertFalse(handler.verifySignature(header, payload));
    }

    @Test
    void testVerifyMissingHeader() {
        assertFalse(handler.verifySignature(null, "payload"));
        assertFalse(handler.verifySignature("", "payload"));
    }

    @Test
    void testVerifyMissingPayload() {
        assertFalse(handler.verifySignature("t=1234,v1=abc", null));
    }

    // ── replay protection ────────────────────────────────────────────────

    @Test
    void testExpiredSignatureIsRejected() {
        String payload = "{\"id\":\"evt_old\"}";
        // Signed 10 minutes ago: outside the 300 s window.
        long stale = NOW_SECONDS - 600L;
        String header = headerFor(stale, payload);

        assertFalse(handler.verifySignature(header, payload),
            "a signature older than the tolerance window must be rejected");
        assertEquals(StripeWebhookHandler.SignatureFailure.TIMESTAMP_OUT_OF_TOLERANCE,
            handler.verifySignatureDetailed(header, payload, Instant.ofEpochSecond(NOW_SECONDS)));
    }

    @Test
    void testVeryOldSignatureIsRejected() {
        String payload = "{}";
        String header = headerFor(NOW_SECONDS - 86_400L, payload);
        assertFalse(handler.verifySignature(header, payload));
    }

    @Test
    void testSignatureExactlyAtToleranceBoundaryIsAccepted() {
        String payload = "{}";
        long atBoundary = NOW_SECONDS - StripeWebhookHandler.DEFAULT_TOLERANCE_SECONDS;
        String header = headerFor(atBoundary, payload);
        assertTrue(handler.verifySignature(header, payload),
            "a signature exactly at the tolerance boundary is still fresh");
    }

    @Test
    void testSignatureOneSecondOutsideToleranceIsRejected() {
        String payload = "{}";
        long justOutside = NOW_SECONDS - StripeWebhookHandler.DEFAULT_TOLERANCE_SECONDS - 1L;
        String header = headerFor(justOutside, payload);
        assertFalse(handler.verifySignature(header, payload));
    }

    @Test
    void testFutureTimestampBeyondToleranceIsRejected() {
        String payload = "{}";
        String header = headerFor(NOW_SECONDS + 3600L, payload);
        assertFalse(handler.verifySignature(header, payload),
            "a timestamp far in the future must be rejected");
    }

    @Test
    void testMalformedTimestampIsRejected() {
        String payload = "{}";
        String header = "t=not-a-number,v1="
            + StripeWebhookHandler.hmacSha256Hex(WEBHOOK_SECRET, "not-a-number." + payload);
        assertFalse(handler.verifySignature(header, payload));
        assertEquals(StripeWebhookHandler.SignatureFailure.MALFORMED_TIMESTAMP,
            handler.verifySignatureDetailed(header, payload, Instant.ofEpochSecond(NOW_SECONDS)));
    }

    @Test
    void testNonPositiveTimestampIsRejected() {
        String payload = "{}";
        for (long ts : new long[] {0L, -1L, -1_700_000_000L}) {
            String header = "t=" + ts + ",v1="
                + StripeWebhookHandler.hmacSha256Hex(WEBHOOK_SECRET, ts + "." + payload);
            assertFalse(handler.verifySignature(header, payload),
                "timestamp " + ts + " must be rejected");
        }
    }

    @Test
    void testMissingTimestampIsRejected() {
        String payload = "{}";
        String header = "v1="
            + StripeWebhookHandler.hmacSha256Hex(WEBHOOK_SECRET, "." + payload);
        assertFalse(handler.verifySignature(header, payload));
        assertEquals(StripeWebhookHandler.SignatureFailure.MISSING_TIMESTAMP,
            handler.verifySignatureDetailed(header, payload, Instant.ofEpochSecond(NOW_SECONDS)));
    }

    @Test
    void testMissingSignatureIsRejected() {
        assertFalse(handler.verifySignature("t=" + NOW_SECONDS, "{}"));
        assertFalse(handler.verifySignature("t=" + NOW_SECONDS + ",v1=", "{}"));
    }

    @Test
    void testAlteredPayloadIsRejected() {
        String original = "{\"amount\":100}";
        String header = headerFor(NOW_SECONDS, original);
        assertFalse(handler.verifySignature(header, "{\"amount\":999}"),
            "a payload altered after signing must be rejected");
    }

    // ── key rotation ─────────────────────────────────────────────────────

    @Test
    void testMultipleV1CandidatesOneValidIsAccepted() {
        String payload = "{\"id\":\"evt_rot\"}";
        String good = StripeWebhookHandler.hmacSha256Hex(WEBHOOK_SECRET, NOW_SECONDS + "." + payload);
        String bad1 = "0".repeat(64);
        String bad2 = "1".repeat(64);
        String header = "t=" + NOW_SECONDS + ",v1=" + bad1 + ",v1=" + good + ",v1=" + bad2;

        assertTrue(handler.verifySignature(header, payload),
            "rotation: one valid v1 among several must be accepted");
    }

    @Test
    void testMultipleV1CandidatesNoneValidIsRejected() {
        String payload = "{}";
        String header = "t=" + NOW_SECONDS + ",v1=" + "0".repeat(64) + ",v1=" + "1".repeat(64);
        assertFalse(handler.verifySignature(header, payload));
    }

    // ── configuration ────────────────────────────────────────────────────

    @Test
    void testFreshnessCheckCannotBeDisabled() {
        assertThrows(IllegalArgumentException.class, () ->
            new StripeWebhookHandler(WEBHOOK_SECRET, subscriptions, ledger, FIXED_CLOCK, 0L));
        assertThrows(IllegalArgumentException.class, () ->
            new StripeWebhookHandler(WEBHOOK_SECRET, subscriptions, ledger, FIXED_CLOCK, -1L));
    }

    @Test
    void testDefaultToleranceMatchesStripe() {
        assertEquals(300L, StripeWebhookHandler.DEFAULT_TOLERANCE_SECONDS);
        StripeWebhookHandler defaulted =
            new StripeWebhookHandler(WEBHOOK_SECRET, subscriptions, ledger);
        assertEquals(StripeWebhookHandler.DEFAULT_TOLERANCE_SECONDS, defaulted.toleranceSeconds());
    }

    @Test
    void testSystemClockConstructorIsUsable() {
        StripeWebhookHandler defaulted =
            new StripeWebhookHandler(WEBHOOK_SECRET, subscriptions, ledger);
        String payload = "{}";
        String header = headerFor(Instant.now().getEpochSecond(), payload);
        assertTrue(defaulted.verifySignature(header, payload));
    }

    // ── event processing ─────────────────────────────────────────────────

    @Test
    void testProcessInvoicePaidCreditsCustomer() {
        var signup = subscriptions.signup("a@a.com", Plan.FREE);
        long balanceBefore = ledger.balance(signup.customer().customerId());

        boolean ok = handler.processEvent(
            "evt_123",
            StripeWebhookHandler.EventType.INVOICE_PAID,
            signup.customer().customerId(),
            "cus_stripe_1",
            5000,  // $50
            "{}"
        );
        assertTrue(ok);
        long balanceAfter = ledger.balance(signup.customer().customerId());
        assertEquals(balanceBefore + 5000, balanceAfter);
    }

    @Test
    void testProcessSubscriptionUpdatedChangesPlan() {
        var signup = subscriptions.signup("a@a.com", Plan.FREE);
        assertEquals(Plan.FREE, signup.customer().plan());

        handler.processEvent(
            "evt_sub_1",
            StripeWebhookHandler.EventType.CUSTOMER_SUBSCRIPTION_UPDATED,
            signup.customer().customerId(),
            "cus_1",
            4900,  // $49 PRO
            "{}"
        );

        Customer updated = subscriptions.findById(signup.customer().customerId()).orElseThrow();
        assertEquals(Plan.PRO, updated.plan());
    }

    @Test
    void testProcessSubscriptionDeletedCancels() {
        var signup = subscriptions.signup("a@a.com", Plan.PRO);

        handler.processEvent(
            "evt_del_1",
            StripeWebhookHandler.EventType.CUSTOMER_SUBSCRIPTION_DELETED,
            signup.customer().customerId(),
            "cus_1",
            0,
            "{}"
        );

        Customer cancelled = subscriptions.findById(signup.customer().customerId()).orElseThrow();
        assertFalse(cancelled.active());
    }

    @Test
    void testIdempotency() {
        var signup = subscriptions.signup("a@a.com", Plan.FREE);
        long balanceBefore = ledger.balance(signup.customer().customerId());

        // Same event_id twice — should only apply once
        handler.processEvent("evt_dup", StripeWebhookHandler.EventType.INVOICE_PAID,
            signup.customer().customerId(), "cus_1", 1000, "{}");
        handler.processEvent("evt_dup", StripeWebhookHandler.EventType.INVOICE_PAID,
            signup.customer().customerId(), "cus_1", 1000, "{}");

        long balanceAfter = ledger.balance(signup.customer().customerId());
        assertEquals(balanceBefore + 1000, balanceAfter);
    }

    @Test
    void testFreshDuplicateDeliveryDoesNotDoubleCredit() {
        // Freshness and idempotency are independent: a FRESH duplicate delivery
        // passes the signature check and must still not duplicate the credit.
        var signup = subscriptions.signup("a@a.com", Plan.FREE);
        long balanceBefore = ledger.balance(signup.customer().customerId());
        String payload = "{\"id\":\"evt_dup2\"}";
        String header = headerFor(NOW_SECONDS, payload);

        assertTrue(handler.verifySignature(header, payload));
        handler.processEvent("evt_dup2", StripeWebhookHandler.EventType.INVOICE_PAID,
            signup.customer().customerId(), "cus_1", 1000, payload);
        // Same (fresh) signature replayed immediately: signature still valid,
        // but the side effect must not repeat.
        assertTrue(handler.verifySignature(header, payload));
        handler.processEvent("evt_dup2", StripeWebhookHandler.EventType.INVOICE_PAID,
            signup.customer().customerId(), "cus_1", 1000, payload);

        assertEquals(balanceBefore + 1000, ledger.balance(signup.customer().customerId()));
    }
}
