package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the cost ledger: what it records, and the fact that it is the only place in the
 * package that reads a clock.
 */
class CostLedgerTest {

    @Test
    void anUnmeasuredMethodReportsTheUnmeasuredSentinel() {
        CostLedger ledger = new CostLedger();

        assertThat(ledger.measuredNanos("never.run")).isEqualTo(CostLedger.UNMEASURED);
        assertThat(ledger.measurement("never.run")).isNull();
        assertThat(ledger.callCount("never.run")).isZero();
    }

    @Test
    void theMeasuredValueIsTheMeanOverTheRecordedCalls() {
        CostLedger ledger = new CostLedger();

        ledger.record("m", 100L, true);
        ledger.record("m", 300L, true);

        assertThat(ledger.measuredNanos("m")).isEqualTo(200L);
        assertThat(ledger.callCount("m")).isEqualTo(2);
        assertThat(ledger.measurement("m").successes()).isEqualTo(2);
        assertThat(ledger.measurement("m").failures()).isZero();
    }

    @Test
    void aFailedCallIsCountedAsAFailureAndStillCostsWhatItCost() {
        CostLedger ledger = new CostLedger();

        ledger.record("m", 500L, false);

        assertThat(ledger.measurement("m").failures()).isOne();
        assertThat(ledger.measurement("m").successes()).isZero();
        assertThat(ledger.measuredNanos("m")).isEqualTo(500L);
    }

    @Test
    void measureReturnsWhatTheBodyProducedAndRecordsTheCost() {
        CostLedger ledger = new CostLedger();

        String answer = ledger.measure("m", () -> "the answer");

        assertThat(answer).isEqualTo("the answer");
        assertThat(ledger.callCount("m")).isOne();
        assertThat(ledger.measuredNanos("m")).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void measureRecordsAFailureAndRethrows() {
        CostLedger ledger = new CostLedger();

        assertThatThrownBy(() -> ledger.measure("m", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ledger.measurement("m").failures()).isOne();
        assertThat(ledger.measurement("m").successes()).isZero();
    }

    @Test
    void aBlankMethodIdIsRefused() {
        CostLedger ledger = new CostLedger();

        assertThatThrownBy(() -> ledger.record(" ", 10L, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.record("m", -1L, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMeanIsAlsoReportedInMilliseconds() {
        CostLedger ledger = new CostLedger();
        ledger.record("m", 2_000_000L, true);

        assertThat(ledger.measurement("m").meanMillis()).isEqualTo(2.0);
        assertThat(ledger.measurement("m").meanNanos()).isEqualTo(2_000_000.0);
    }

    @Test
    void theVoidOverloadOfMeasureRecordsTheCostToo() {
        CostLedger ledger = new CostLedger();
        StringBuilder seen = new StringBuilder();

        // A block lambda, so that the void overload is the one that is called.
        ledger.measure("m", () -> {
            seen.append("done");
        });

        assertThat(seen.toString()).isEqualTo("done");
        assertThat(ledger.callCount("m")).isOne();
        assertThat(ledger.measurement("m").successes()).isOne();
    }

    @Test
    void clearingForgetsEveryMeasurement() {
        CostLedger ledger = new CostLedger();
        ledger.record("m", 100L, true);

        ledger.clear();

        assertThat(ledger.measuredNanos("m")).isEqualTo(CostLedger.UNMEASURED);
        assertThat(ledger.measuredMethodIds()).isEmpty();
        assertThat(ledger.snapshot()).isEmpty();
    }
}
