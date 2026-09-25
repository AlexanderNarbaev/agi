package io.matrix.billing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BillingModuleTest {

    @Test
    void testStatus() {
        String status = BillingModule.status();
        assertTrue(status.startsWith("matrix-billing:0.1.0-T07:credit=MCR"));
    }

    @Test
    void testCreditUnit() {
        assertEquals("MCR", BillingModule.CREDIT_UNIT);
    }

    @Test
    void testStripeKeyPrefix() {
        assertTrue(BillingModule.STRIPE_KEY_PREFIX.startsWith("whsec_"));
    }

    @Test
    void testLedgerAvailable() {
        assertNotNull(BillingModule.ledger());
    }

    @Test
    void testSubscriptionsAvailable() {
        assertNotNull(BillingModule.subscriptions());
    }

    @Test
    void testValidatorAvailable() {
        assertNotNull(BillingModule.validator());
    }

    @Test
    void testWebhookAvailable() {
        assertNotNull(BillingModule.webhook());
    }

    @Test
    void testFacadeSignup() {
        var result = BillingModule.subscriptions().signup("test@example.com", Plan.FREE);
        assertEquals("test@example.com", result.customer().email());
        assertTrue(result.balance() > 0);
    }
}
