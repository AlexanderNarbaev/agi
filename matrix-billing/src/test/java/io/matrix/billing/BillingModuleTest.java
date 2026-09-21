package io.matrix.billing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BillingModuleTest {

    @Test
    void testStatus() {
        String status = BillingModule.status();
        assertTrue(status.startsWith("matrix-billing:0.1.0-T01:credit="));
        assertTrue(status.endsWith("MCR"));
    }

    @Test
    void testCreditUnit() {
        assertEquals("MCR", BillingModule.CREDIT_UNIT);
    }

    @Test
    void testStripeKeyPrefix() {
        assertTrue(BillingModule.STRIPE_KEY_PREFIX.startsWith("pk_"));
    }
}
