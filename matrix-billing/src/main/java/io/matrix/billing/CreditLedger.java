package io.matrix.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WAVE T-07 — Credit Ledger.
 *
 * In-memory implementation. T-07.5 wires to PostgreSQL for persistence.
 *
 * <p>Concurrency: balances are stored in a ConcurrentHashMap; reads
 * are atomic; writes synchronize per-customer via the customer's
 * own lock object to prevent lost-update.</p>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure accounting logic.</p>
 */
public final class CreditLedger {

    private final Map<String, Long> balances = new ConcurrentHashMap<>();
    private final List<CreditTransaction> transactions = new ArrayList<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /**
     * Add credits to a customer. Used for plan signups, renewals,
     * purchases, and bonus grants.
     *
     * @return the resulting balance
     */
    public synchronized long credit(String customerId, long amount, String reason) {
        Objects.requireNonNull(customerId, "customerId");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        return mutate(customerId, amount, CreditTransaction.Type.CREDIT, reason, null);
    }

    /**
     * Record a Stripe event for idempotency (so retries don't double-credit).
     */
    public synchronized long creditFromStripe(String customerId, long amount,
                                              String reason, String stripeEventId) {
        // Check if event already processed (skip null stripe_event_id)
        for (CreditTransaction tx : transactions) {
            if (stripeEventId != null && stripeEventId.equals(tx.stripeEventId())) {
                return balances.getOrDefault(customerId, 0L);
            }
        }
        // Apply directly with stripeEventId recorded on the transaction
        return mutate(customerId, amount, CreditTransaction.Type.CREDIT, reason, stripeEventId);
    }

    /**
     * Spend credits. Returns true if successful, false if insufficient.
     */
    public synchronized boolean debit(String customerId, long amount, String reason) {
        Objects.requireNonNull(customerId, "customerId");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");

        long current = balances.getOrDefault(customerId, 0L);
        if (current < amount) {
            return false;  // insufficient
        }

        mutate(customerId, -amount, CreditTransaction.Type.DEBIT, reason, null);
        return true;
    }

    /** Get current balance for a customer (0 if no record). */
    public long balance(String customerId) {
        return balances.getOrDefault(customerId, 0L);
    }

    /** Full transaction history for a customer, newest first. */
    public List<CreditTransaction> history(String customerId) {
        List<CreditTransaction> result = new ArrayList<>();
        for (int i = transactions.size() - 1; i >= 0; i--) {
            CreditTransaction tx = transactions.get(i);
            if (tx.customerId().equals(customerId)) {
                result.add(tx);
            }
        }
        return List.copyOf(result);
    }

    /** Total credits consumed across all customers. */
    public long totalConsumed() {
        long total = 0;
        for (CreditTransaction tx : transactions) {
            if (tx.type() == CreditTransaction.Type.DEBIT) {
                total += tx.amount();
            }
        }
        return total;
    }

    private long mutate(String customerId, long delta, CreditTransaction.Type type,
                        String reason, String stripeEventId) {
        Object lock = locks.computeIfAbsent(customerId, k -> new Object());
        synchronized (lock) {
            long current = balances.getOrDefault(customerId, 0L);
            long newBalance = current + delta;
            if (newBalance < 0) {
                throw new IllegalStateException(
                    "Negative balance for " + customerId + ": " + newBalance);
            }
            balances.put(customerId, newBalance);

            String txId = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            transactions.add(new CreditTransaction(
                txId, customerId, type, Math.abs(delta), newBalance,
                java.time.Instant.now(), reason, stripeEventId
            ));
            return newBalance;
        }
    }

    /** Test helper: clear all state. */
    public void reset() {
        balances.clear();
        transactions.clear();
    }
}
