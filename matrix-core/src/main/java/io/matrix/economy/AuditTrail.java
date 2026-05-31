package io.matrix.economy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable audit trail for all economic transactions.
 *
 * <p>Every credit operation is recorded as an immutable entry.
 * Full transparency: any participant can verify the ledger.
 * Compliant with regenerative economy principles.
 *
 * <p>Ref: L8_Roadmap.md §3.9
 */
public class AuditTrail {

    public enum TransactionType {
        PUBLICATION_REWARD,
        CERTIFICATION_BONUS,
        DOWNLOAD_CHARGE,
        RESOURCE_CONTRIBUTION,
        PENALTY,
        REBATE
    }

    public record Transaction(
            UUID id,
            TransactionType type,
            String instanceId,
            double amount,
            double balanceAfter,
            String description,
            Instant timestamp
    ) {}

    private final List<Transaction> ledger = new ArrayList<>();

    public Transaction record(TransactionType type, String instanceId,
                                double amount, double balanceAfter, String description) {
        Transaction tx = new Transaction(
                UUID.randomUUID(), type, instanceId,
                amount, balanceAfter, description, Instant.now());
        ledger.add(tx);
        return tx;
    }

    public List<Transaction> forInstance(String instanceId) {
        return ledger.stream()
                .filter(tx -> tx.instanceId().equals(instanceId))
                .toList();
    }

    public List<Transaction> byType(TransactionType type) {
        return ledger.stream()
                .filter(tx -> tx.type() == type)
                .toList();
    }

    public List<Transaction> all() { return List.copyOf(ledger); }

    public int size() { return ledger.size(); }

    public double totalRewards(String instanceId) {
        return ledger.stream()
                .filter(tx -> tx.instanceId().equals(instanceId))
                .filter(tx -> tx.amount() > 0)
                .mapToDouble(Transaction::amount)
                .sum();
    }
}
