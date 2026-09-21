package io.matrix.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreditLedgerTest {

    private CreditLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new CreditLedger();
    }

    @Test
    void testInitialBalanceIsZero() {
        assertEquals(0, ledger.balance("user-1"));
    }

    @Test
    void testCredit() {
        long balance = ledger.credit("user-1", 100, "signup");
        assertEquals(100, balance);
        assertEquals(100, ledger.balance("user-1"));
    }

    @Test
    void testDebit() {
        ledger.credit("user-1", 100, "signup");
        boolean success = ledger.debit("user-1", 30, "API call");
        assertTrue(success);
        assertEquals(70, ledger.balance("user-1"));
    }

    @Test
    void testDebitInsufficient() {
        ledger.credit("user-1", 100, "signup");
        boolean success = ledger.debit("user-1", 150, "API call");
        assertFalse(success, "Should fail when balance insufficient");
        assertEquals(100, ledger.balance("user-1"), "Balance unchanged");
    }

    @Test
    void testDebitExactBalance() {
        ledger.credit("user-1", 100, "signup");
        assertTrue(ledger.debit("user-1", 100, "exact"));
        assertEquals(0, ledger.balance("user-1"));
    }

    @Test
    void testNegativeAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> ledger.credit("user-1", -10, "bad"));
        assertThrows(IllegalArgumentException.class, () -> ledger.debit("user-1", -10, "bad"));
    }

    @Test
    void testZeroAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> ledger.credit("user-1", 0, "zero"));
    }

    @Test
    void testStripeIdempotency() {
        ledger.creditFromStripe("user-1", 100, "payment-1", "evt_123");
        ledger.creditFromStripe("user-1", 100, "payment-1", "evt_123");  // duplicate
        assertEquals(100, ledger.balance("user-1"), "Duplicate event should not double-credit");
    }

    @Test
    void testDifferentStripeEventsBothApply() {
        ledger.creditFromStripe("user-1", 100, "payment-1", "evt_123");
        ledger.creditFromStripe("user-1", 200, "payment-2", "evt_456");
        assertEquals(300, ledger.balance("user-1"));
    }

    @Test
    void testHistory() {
        ledger.credit("user-1", 100, "signup");
        ledger.debit("user-1", 30, "API");
        ledger.debit("user-1", 20, "API");
        var history = ledger.history("user-1");
        assertEquals(3, history.size());
        assertEquals(CreditTransaction.Type.DEBIT, history.get(0).type());
        assertEquals(CreditTransaction.Type.DEBIT, history.get(1).type());
        assertEquals(CreditTransaction.Type.CREDIT, history.get(2).type());
    }

    @Test
    void testHistoryIsolatedPerUser() {
        ledger.credit("user-1", 100, "a");
        ledger.credit("user-2", 200, "b");
        assertEquals(1, ledger.history("user-1").size());
        assertEquals(1, ledger.history("user-2").size());
    }

    @Test
    void testTotalConsumed() {
        ledger.credit("user-1", 100, "signup");
        ledger.debit("user-1", 30, "API");
        ledger.debit("user-1", 20, "API");
        assertEquals(50, ledger.totalConsumed());
    }

    @Test
    void testReset() {
        ledger.credit("user-1", 100, "a");
        ledger.reset();
        assertEquals(0, ledger.balance("user-1"));
        assertEquals(0, ledger.totalConsumed());
    }

    @Test
    void testBalanceAfterInTransaction() {
        ledger.credit("user-1", 100, "a");
        var history = ledger.history("user-1");
        assertEquals(100, history.get(0).balanceAfter());
    }
}
