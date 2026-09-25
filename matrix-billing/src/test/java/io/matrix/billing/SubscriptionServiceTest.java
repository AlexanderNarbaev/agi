package io.matrix.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubscriptionServiceTest {

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        CreditLedger ledger = new CreditLedger();
        LicenseValidator validator = new LicenseValidator("test-secret");
        service = new SubscriptionService(ledger, validator, "test-secret");
    }

    @Test
    void testSignup() {
        var result = service.signup("alice@example.com", Plan.PRO);
        assertNotNull(result.customer());
        assertEquals("alice@example.com", result.customer().email());
        assertEquals(Plan.PRO, result.customer().plan());
        assertNotNull(result.licenseKey());
        assertTrue(result.balance() > 0);
    }

    @Test
    void testSignupDuplicateRejected() {
        service.signup("alice@example.com", Plan.FREE);
        assertThrows(IllegalStateException.class,
            () -> service.signup("alice@example.com", Plan.PRO));
    }

    @Test
    void testChangePlan() {
        var result = service.signup("alice@example.com", Plan.FREE);
        long balanceBefore = result.balance();
        service.changePlan(result.customer().customerId(), Plan.PRO);
        // PRO has 10x the credits/hour of FREE; balance should grow
        Customer updated = service.findById(result.customer().customerId()).orElseThrow();
        assertEquals(Plan.PRO, updated.plan());
        assertTrue(service.ledger().balance(result.customer().customerId()) >= balanceBefore);
    }

    @Test
    void testCancel() {
        var result = service.signup("alice@example.com", Plan.PRO);
        service.cancel(result.customer().customerId());
        Customer cancelled = service.findById(result.customer().customerId()).orElseThrow();
        assertFalse(cancelled.active());
        assertEquals(Plan.FREE, cancelled.plan());
    }

    @Test
    void testFindByEmail() {
        service.signup("alice@example.com", Plan.FREE);
        var found = service.findByEmail("alice@example.com");
        assertTrue(found.isPresent());
        assertEquals("alice@example.com", found.get().email());
    }

    @Test
    void testFindByEmailCaseInsensitive() {
        service.signup("alice@example.com", Plan.FREE);
        var found = service.findByEmail("ALICE@EXAMPLE.COM");
        assertTrue(found.isPresent());
    }

    @Test
    void testCustomerCount() {
        assertEquals(0, service.customerCount());
        service.signup("a@a.com", Plan.FREE);
        service.signup("b@b.com", Plan.FREE);
        assertEquals(2, service.customerCount());
    }

    @Test
    void testSubscriptionCreated() {
        var result = service.signup("a@a.com", Plan.PRO);
        var sub = service.subscriptionFor(result.customer().customerId());
        assertTrue(sub.isPresent());
        assertEquals(Plan.PRO, sub.get().plan());
        assertTrue(sub.get().isActive());
    }

    @Test
    void testLicenseEncodingRoundTrip() {
        var result = service.signup("a@a.com", Plan.PRO);
        LicenseKey decoded = LicenseKey.decode(result.licenseKey());
        assertEquals(result.customer().customerId(), decoded.customerId());
    }

    @Test
    void testFindByIdUnknown() {
        assertTrue(service.findById("unknown").isEmpty());
    }
}
