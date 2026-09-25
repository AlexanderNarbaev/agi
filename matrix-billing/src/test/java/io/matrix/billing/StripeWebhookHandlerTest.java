package io.matrix.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class StripeWebhookHandlerTest {

    private CreditLedger ledger;
    private SubscriptionService subscriptions;
    private StripeWebhookHandler handler;
    private static final String WEBHOOK_SECRET = "whsec_test";

    @BeforeEach
    void setUp() {
        ledger = new CreditLedger();
        LicenseValidator validator = new LicenseValidator("test-secret");
        subscriptions = new SubscriptionService(ledger, validator, "test-secret");
        handler = new StripeWebhookHandler(WEBHOOK_SECRET, subscriptions, ledger);
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
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"id\":\"evt_1\",\"type\":\"invoice.paid\"}";
        String signedPayload = timestamp + "." + payload;
        String sig = StripeWebhookHandler.hmacSha256Hex(WEBHOOK_SECRET, signedPayload);
        String header = "t=" + timestamp + ",v1=" + sig;

        assertTrue(handler.verifySignature(header, payload));
    }

    @Test
    void testVerifyInvalidSignature() {
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{}";
        String header = "t=" + timestamp + ",v1=deadbeef" + "0".repeat(56);
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
}
