package io.matrix.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * WAVE T-07 — Credit Transaction.
 *
 * Double-entry bookkeeping: every transaction is either a CREDIT (add)
 * or DEBIT (subtract). Sum of all transactions for a customer equals
 * their current balance. Negative balances are not allowed — usage
 * is denied before a debit would push balance below zero.
 */
public record CreditTransaction(
    @JsonProperty("tx_id") String txId,
    @JsonProperty("customer_id") String customerId,
    @JsonProperty("type") Type type,
    @JsonProperty("amount") long amount,
    @JsonProperty("balance_after") long balanceAfter,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("reason") String reason,
    @JsonProperty("stripe_event_id") String stripeEventId
) {
    public enum Type {
        CREDIT,  // add credits (purchase, plan renewal, bonus)
        DEBIT    // consume credits (API call, federation join)
    }

    public CreditTransaction {
        Objects.requireNonNull(txId, "txId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(type, "type");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
