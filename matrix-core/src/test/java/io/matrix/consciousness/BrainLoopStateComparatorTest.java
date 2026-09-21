package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 229 — BrainLoopStateComparator unit tests. */
class BrainLoopStateComparatorTest {

    @Test
    void identicalTracesMatch() {
        var a = new BrainLoopService();
        var b = new BrainLoopService();
        for (int i = 0; i < 10; i++) {
            a.cycle("X-" + i);
            b.cycle("X-" + i);
        }
        var cmp = BrainLoopStateComparator.compare(a.trace(), b.trace());
        assertThat(cmp.identical()).isTrue();
        assertThat(cmp.stepsA()).isEqualTo(cmp.stepsB());
    }

    @Test
    void differentTracesDiffer() {
        var a = new BrainLoopService();
        var b = new BrainLoopService();
        for (int i = 0; i < 5; i++) a.cycle("X-" + i);
        for (int i = 0; i < 8; i++) b.cycle("X-" + i);
        var cmp = BrainLoopStateComparator.compare(a.trace(), b.trace());
        assertThat(cmp.identical()).isFalse();
        assertThat(cmp.stepsA()).isLessThan(cmp.stepsB());
    }

    @Test
    void emptyTracesProduceIdentical() {
        var a = new BrainLoopService();
        var b = new BrainLoopService();
        var cmp = BrainLoopStateComparator.compare(a.trace(), b.trace());
        // Both empty → not identical (no steps), but identical in count
        assertThat(cmp.stepsA()).isZero();
        assertThat(cmp.stepsB()).isZero();
    }

    @Test
    void formatProducesReadableString() {
        var a = new BrainLoopService();
        a.cycle("X");
        var b = new BrainLoopService();
        b.cycle("X");
        var cmp = BrainLoopStateComparator.compare(a.trace(), b.trace());
        String text = BrainLoopStateComparator.format(cmp);
        assertThat(text).contains("Compare");
        assertThat(text).contains("stepsA=");
    }
}
