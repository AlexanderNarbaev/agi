package io.matrix.brain.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MIND-W7 — Credit ledger for billing.
 *
 * <p>Tracks credit consumption per cognitive cycle. The double-entry style
 * keeps a per-tier balance alongside an event log so that /v1/billing/usage
 * can render a transparent report.</p>
 *
 * <p>CONSTITUTION Article VI — every credit movement is recorded with
 * timestamp + reason; no "just trust me" balance changes.</p>
 */
public final class CreditLedger {

    public enum Operation { ANALYZE, THINK_WITH_MCTS, TEACH, LEARN, SLEEP, DISTILL }

    public record Entry(
        String id,
        String userId,
        Operation op,
        int credits,
        long timestampMillis,
        String reason
    ) {}

    private final Map<String, Integer> balances = new LinkedHashMap<>();
    private final java.util.List<Entry> log = new java.util.ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Default cost per operation. */
    public static int defaultCost(Operation op) {
        return switch (op) {
            case ANALYZE           -> 1;
            case THINK_WITH_MCTS   -> 5;
            case TEACH             -> 2;
            case LEARN             -> 10;
            case SLEEP             -> 3;
            case DISTILL           -> 100;
        };
    }

    /** Deduct credits for {@code userId}; returns the new balance. */
    public int deduct(String userId, Operation op, int costOverride) {
        int cost = costOverride > 0 ? costOverride : defaultCost(op);
        lock.writeLock().lock();
        try {
            int before = balances.getOrDefault(userId, 0);
            int after = before - cost;
            balances.put(userId, after);
            log.add(new Entry(UUID.randomUUID().toString(),
                userId, op, cost, System.currentTimeMillis(),
                op.name() + " x" + cost));
            return after;
        } finally {
            lock.writeLock().unlock(); }
    }

    /** Refund (e.g. when a downstream error refunds the call). */
    public int refund(String userId, Operation op, int amount) {
        lock.writeLock().lock();
        try {
            int before = balances.getOrDefault(userId, 0);
            int after = before + amount;
            balances.put(userId, after);
            log.add(new Entry(UUID.randomUUID().toString(),
                userId, op, -amount, System.currentTimeMillis(),
                "refund " + op.name()));
            return after;
        } finally {
            lock.writeLock().unlock(); }
    }

    /** Top up a user's balance (admin / billing webhook). */
    public int topUp(String userId, int credits) {
        lock.writeLock().lock();
        try {
            int before = balances.getOrDefault(userId, 0);
            int after = before + credits;
            balances.put(userId, after);
            log.add(new Entry(UUID.randomUUID().toString(),
                userId, null, credits, System.currentTimeMillis(),
                "topup"));
            return after;
        } finally {
            lock.writeLock().unlock(); }
    }

    public int balance(String userId) {
        lock.readLock().lock();
        try { return balances.getOrDefault(userId, 0); }
        finally { lock.readLock().unlock(); }
    }

    /** Last N entries for a user (most-recent first). */
    public java.util.List<Entry> recentEntries(String userId, int n) {
        lock.readLock().lock();
        try {
            java.util.List<Entry> all = new java.util.ArrayList<>();
            for (int i = log.size() - 1; i >= 0 && all.size() < n; i--) {
                Entry e = log.get(i);
                if (userId == null || userId.equals(e.userId())) all.add(e);
            }
            return all;
        } finally {
            lock.readLock().unlock(); }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", new LinkedHashMap<>(balances));
        m.put("total_events", log.size());
        return m;
    }
}
