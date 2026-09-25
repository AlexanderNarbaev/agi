package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TRUE-W0 — DiskBudget tests.
 */
class DiskBudgetTest {

    @Test
    void free_bytes_is_positive(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        assertThat(b.freeBytes()).isGreaterThan(0L);
    }

    @Test
    void free_gigabytes_matches_bytes(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        // Read bytes + GB close together (since FS usage can drift between calls).
        long bytes = b.freeBytes();
        double gb = b.freeGigabytes();
        // Allow up to 5 GB drift between calls (theoretical worst case under load)
        assertThat(gb).isBetween(bytes / 1e9 - 5, bytes / 1e9 + 5);
    }

    @Test
    void healthy_tier_is_default(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        // tmp on dev sandbox has plenty of space; expect HEALTHY
        assertThat(b.tier()).isEqualTo(DiskBudget.Tier.HEALTHY);
    }

    @Test
    void tier_thresholds_match_spec(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        // Verify constants
        assertThat(DiskBudget.WARN_THRESHOLD_BYTES).isEqualTo(25L * 1024 * 1024 * 1024);
        assertThat(DiskBudget.REFUSE_THRESHOLD_BYTES).isEqualTo(10L * 1024 * 1024 * 1024);
        assertThat(DiskBudget.WARN_THRESHOLD_BYTES)
            .isGreaterThan(DiskBudget.REFUSE_THRESHOLD_BYTES);
    }

    @Test
    void check_succeeds_when_enough_space(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        // 1 MB needed — well within HEALTHY
        b.check(1024L * 1024L, "test_op");
    }

    @Test
    void record_write_increments_ledger(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        assertThat(b.ledgerBytes()).isZero();
        b.recordWrite("artifact1", 1000L);
        b.recordWrite("artifact2", 2000L);
        assertThat(b.ledgerBytes()).isEqualTo(3000L);
    }

    @Test
    void snapshot_contains_required_keys(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        var snap = b.snapshot();
        assertThat(snap).containsKeys(
            "free_bytes", "free_gb", "tier",
            "warn_threshold_gb", "refuse_threshold_gb",
            "cumulative_writes_bytes"
        );
    }

    @Test
    void refuse_throws_disk_budget_exceeded_for_huge_requirement(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        // Ask for absurdly more bytes than available — must throw.
        long huge = b.freeBytes() + 100L * 1024 * 1024 * 1024;  // 100 GB more than free
        assertThatThrownBy(() -> b.check(huge, "absurd"))
            .isInstanceOf(DiskBudget.DiskBudgetExceeded.class)
            .hasMessageContaining("absurd");
    }

    @Test
    void refuse_threshold_constant_is_consistent(@TempDir Path tmp) {
        DiskBudget b = DiskBudget.forRoot(tmp);
        // Projected = free - needed; throws if projected < refuse threshold.
        long need = b.freeBytes() - (DiskBudget.REFUSE_THRESHOLD_BYTES / 2);
        assertThatThrownBy(() -> b.check(need, "op"))
            .isInstanceOf(DiskBudget.DiskBudgetExceeded.class);
    }
}
