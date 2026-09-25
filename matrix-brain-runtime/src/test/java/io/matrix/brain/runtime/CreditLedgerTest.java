package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W7 — Credit ledger tests.
 *
 * <p>Verifies per-operation cost, top-up, refund, and recent-entries
 * rendering for /v1/billing/usage.</p>
 */
class CreditLedgerTest {

    @Test
    void default_cost_matches_documented_values() {
        assertThat(CreditLedger.defaultCost(CreditLedger.Operation.ANALYZE)).isEqualTo(1);
        assertThat(CreditLedger.defaultCost(CreditLedger.Operation.THINK_WITH_MCTS)).isEqualTo(5);
        assertThat(CreditLedger.defaultCost(CreditLedger.Operation.TEACH)).isEqualTo(2);
        assertThat(CreditLedger.defaultCost(CreditLedger.Operation.LEARN)).isEqualTo(10);
        assertThat(CreditLedger.defaultCost(CreditLedger.Operation.SLEEP)).isEqualTo(3);
        assertThat(CreditLedger.defaultCost(CreditLedger.Operation.DISTILL)).isEqualTo(100);
    }

    @Test
    void initial_balance_is_zero() {
        CreditLedger ledger = new CreditLedger();
        assertThat(ledger.balance("alice")).isZero();
    }

    @Test
    void top_up_credits_balance() {
        CreditLedger ledger = new CreditLedger();
        assertThat(ledger.topUp("alice", 100)).isEqualTo(100);
        assertThat(ledger.balance("alice")).isEqualTo(100);
        assertThat(ledger.topUp("alice", 50)).isEqualTo(150);
    }

    @Test
    void deduct_decrements_balance() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        assertThat(ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0)).isEqualTo(99);
        assertThat(ledger.deduct("alice", CreditLedger.Operation.THINK_WITH_MCTS, 0))
            .isEqualTo(94);
    }

    @Test
    void deduct_uses_override_when_positive() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        assertThat(ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 50))
            .isEqualTo(50);
    }

    @Test
    void deduct_allows_negative_balance() {
        // Per-Operation is decoupled from balance enforcement; tier check
        // (LicenseManager, deferred to W7.5) enforces the actual limit.
        CreditLedger ledger = new CreditLedger();
        assertThat(ledger.deduct("alice", CreditLedger.Operation.DISTILL, 0))
            .isEqualTo(-100);
    }

    @Test
    void refund_increments_balance() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0);
        ledger.refund("alice", CreditLedger.Operation.ANALYZE, 1);
        assertThat(ledger.balance("alice")).isEqualTo(100);
    }

    @Test
    void recent_entries_returns_most_recent_first() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 10);
        ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0);
        ledger.deduct("alice", CreditLedger.Operation.SLEEP, 0);

        var entries = ledger.recentEntries("alice", 5);
        // 3 events total (topup + 2 deducts); recent-first ordering
        assertThat(entries).hasSize(3);
        // Most recent first (SLEEP, ANALYZE, topup)
        assertThat(entries.get(0).op()).isEqualTo(CreditLedger.Operation.SLEEP);
        assertThat(entries.get(1).op()).isEqualTo(CreditLedger.Operation.ANALYZE);
        // Third entry has null op (topup event)
        assertThat(entries.get(2).op()).isNull();
    }

    @Test
    void recent_entries_filters_by_user() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 10);
        ledger.topUp("bob", 10);
        ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0);

        var aliceEntries = ledger.recentEntries("alice", 5);
        var bobEntries = ledger.recentEntries("bob", 5);

        assertThat(aliceEntries).hasSize(2);
        assertThat(bobEntries).hasSize(1);
        assertThat(bobEntries.get(0).userId()).isEqualTo("bob");
    }

    @Test
    void multi_user_balances_independent() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        ledger.topUp("bob", 50);
        ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0);
        ledger.deduct("bob", CreditLedger.Operation.ANALYZE, 0);
        assertThat(ledger.balance("alice")).isEqualTo(99);
        assertThat(ledger.balance("bob")).isEqualTo(49);
    }

    @Test
    void snapshot_reports_all_users() {
        CreditLedger ledger = new CreditLedger();
        ledger.topUp("alice", 100);
        ledger.topUp("bob", 50);
        ledger.topUp("charlie", 200);

        var snap = ledger.snapshot();
        @SuppressWarnings("unchecked")
        var users = (java.util.Map<String, Integer>) snap.get("users");
        assertThat(users).hasSize(3);
        assertThat(users.get("alice")).isEqualTo(100);
        assertThat(users.get("bob")).isEqualTo(50);
        assertThat(users.get("charlie")).isEqualTo(200);
        assertThat(snap.get("total_events")).isEqualTo(3);
    }

    @Test
    void events_are_recorded_with_timestamps() {
        CreditLedger ledger = new CreditLedger();
        long before = System.currentTimeMillis();
        ledger.deduct("alice", CreditLedger.Operation.ANALYZE, 0);
        long after = System.currentTimeMillis();
        var entry = ledger.recentEntries("alice", 1).get(0);
        assertThat(entry.timestampMillis()).isBetween(before, after);
    }
}
