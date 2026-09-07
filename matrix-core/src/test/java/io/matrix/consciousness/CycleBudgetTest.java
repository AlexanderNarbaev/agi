package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 240 — CycleBudget unit tests. */
class CycleBudgetTest {

    @Test
    void freshBudgetFull() {
        var b = new CycleBudget(10);
        assertThat(b.remaining()).isEqualTo(10);
        assertThat(b.used()).isZero();
    }

    @Test
    void consumeReducesRemaining() {
        var b = new CycleBudget(5);
        assertThat(b.tryConsume()).isTrue();
        assertThat(b.remaining()).isEqualTo(4);
        assertThat(b.used()).isEqualTo(1);
    }

    @Test
    void budgetExhaustsAtMax() {
        var b = new CycleBudget(3);
        b.tryConsume();
        b.tryConsume();
        b.tryConsume();
        assertThat(b.tryConsume()).isFalse();  // no more
        assertThat(b.remaining()).isZero();
    }

    @Test
    void resetRestoresBudget() {
        var b = new CycleBudget(3);
        b.tryConsume();
        b.tryConsume();
        b.reset();
        assertThat(b.used()).isZero();
        assertThat(b.remaining()).isEqualTo(3);
    }

    @Test
    void zeroNormalized() {
        var b = new CycleBudget(0);
        assertThat(b.max()).isEqualTo(1);
    }

    @Test
    void snapshotReflectsState() {
        var b = new CycleBudget(10);
        b.tryConsume();
        b.tryConsume();
        var s = b.snapshot();
        assertThat(s.allowed()).isEqualTo(10);
        assertThat(s.used()).isEqualTo(2);
        assertThat(s.remaining()).isEqualTo(8);
    }
}
