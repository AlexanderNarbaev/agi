package io.matrix.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 162 — ConsolidationCycle unit tests. */
class ConsolidationCycleTest {

    @Test
    void emptyCycleAllTiersZero() {
        ConsolidationCycle c = new ConsolidationCycle();
        assertThat(c.m0Size()).isZero();
        assertThat(c.m1Size()).isZero();
        assertThat(c.m2Size()).isZero();
    }

    @Test
    void storeAddsToM2() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.store("k", new byte[]{1, 2, 3});
        assertThat(c.m2Size()).isEqualTo(1);
        assertThat(c.get("k")).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void trMovesAllM2ToM1() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.store("a", new byte[]{1});
        c.store("b", new byte[]{2});
        int trs = c.tr();
        assertThat(trs).isEqualTo(2);
        assertThat(c.m2Size()).isZero();
        assertThat(c.m1Size()).isEqualTo(2);
    }

    @Test
    void remPromotesAccessedToM0() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.store("k", new byte[]{1});
        c.access("k"); // set accessCount=1
        // Move to M1 first
        c.tr();
        // Move to M0 (any accessCount >= 1)
        int rem = c.rem();
        assertThat(rem).isGreaterThan(0);
        assertThat(c.m0Size()).isGreaterThan(0);
    }

    @Test
    void storeReplacesExisting() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.store("k", new byte[]{1});
        c.store("k", new byte[]{2});
        // Same key replaces
        assertThat(c.m2Size()).isEqualTo(1);
        assertThat(c.get("k")).isEqualTo(new byte[]{2});
    }

    @Test
    void fullCycleTicTac() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.store("k", new byte[]{1});
        c.access("k");
        c.tick(); // TR + REM
        // TR moved to M1, REM moved to M0 (since accessCount >= 1)
        // So after a full cycle, M0 has the entry, M1 and M2 are empty
        assertThat(c.m0Size()).isEqualTo(1);
        assertThat(c.m1Size()).isZero();
        assertThat(c.m2Size()).isZero();
    }

    @Test
    void accessOnUnknownKeyNoOp() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.access("nonexistent"); // should not throw
        assertThat(c.m0Size()).isZero();
    }

    @Test
    void getReturnsNullForUnknown() {
        ConsolidationCycle c = new ConsolidationCycle();
        assertThat(c.get("missing")).isNull();
    }

    @Test
    void stepCountersIncrement() {
        ConsolidationCycle c = new ConsolidationCycle();
        c.store("k", new byte[]{1});
        c.access("k");
        c.tick();
        c.store("k2", new byte[]{2});
        c.tick();
        assertThat(c.trSteps()).isEqualTo(2);
        assertThat(c.remSteps()).isGreaterThan(0);
    }
}
