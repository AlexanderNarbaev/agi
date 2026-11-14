package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — an observation is not a usable piece of evidence.
 *
 * <p><b>What this test holds.</b> The reliability of a method is read in three states, and
 * the difference between the first two is the difference between "we saw nothing" and "we
 * saw something that cannot be acted on". A stratum in which the method was called and
 * declined to answer — or answered in a way nothing confirmed — has been observed: the
 * record says so, and the state says {@link MethodReliability.State#NOT_YET_USABLE}. Only
 * a stratum in which nothing at all was observed is
 * {@link MethodReliability.State#NO_EVIDENCE}.</p>
 *
 * <p><b>Why the distinction matters.</b> {@code NO_EVIDENCE} claims that nothing was seen.
 * A stratum that holds a call which abstained was seen — the method was there, and it
 * declined. Reporting that stratum as {@code NO_EVIDENCE} therefore overwrites an
 * observation with an absence, and a reader of the ledger can no longer tell a method that
 * was never tried from one that was tried and refused to answer. The score is not moved by
 * either state: both of them contribute a zero term, so the difference is visible in the
 * record and in the state, never in the ranking.</p>
 *
 * <p><b>What it proves.</b> Three cases of the same rule: nothing observed is
 * {@code NO_EVIDENCE}; something observed that decided nothing is {@code NOT_YET_USABLE}
 * and not {@code NO_EVIDENCE}; and a decided stratum is {@code ESTIMATED} only when its
 * interval is narrow enough to act on, which is the width gate and nothing else.</p>
 */
class MethodReliabilityStateTest {

    /** The method the estimates are about. */
    private static final String METHOD = "the-method-under-test";

    /** The family of the request the estimates are about. */
    private static final String FAMILY = "the-family";

    /** The version of the capability that ran. */
    private static final String VERSION = "1.0.0";

    /** The trials of the narrow interval: enough of them for the interval to close. */
    private static final int TRIALS = 100;

    /** The successes of the narrow interval. */
    private static final int SUCCESSES = 95;

    /** The trials of the wide interval: too few of them for the interval to close. */
    private static final int FEW_TRIALS = 2;

    /** The successes of the wide interval. */
    private static final int FEW_SUCCESSES = 1;

    /**
     * A stratum in which the method was never called says that nothing was observed.
     */
    @Test
    @DisplayName("a stratum in which nothing was observed is NO_EVIDENCE")
    void nothingObservedIsNoEvidence() {
        MethodReliability estimate = MethodReliability.of(
                new MethodVerdict(METHOD, FAMILY, VERSION, 0, 0, 0, 0, 0, 0, 0, 0));

        assertThat(estimate.state()).isEqualTo(MethodReliability.State.NO_EVIDENCE);
        assertThat(estimate.calls()).isZero();
    }

    /**
     * A method that was never measured at all is the same absence.
     */
    @Test
    @DisplayName("a method that was never measured is NO_EVIDENCE")
    void neverMeasuredIsNoEvidence() {
        MethodReliability estimate = MethodReliability.neverMeasured(METHOD);

        assertThat(estimate.state()).isEqualTo(MethodReliability.State.NO_EVIDENCE);
        assertThat(estimate.decisions()).isZero();
    }

    /**
     * A stratum in which the method was called and abstained was observed.
     */
    @Test
    @DisplayName("an observed stratum that decided nothing is NOT_YET_USABLE, not NO_EVIDENCE")
    void observedButUndecidedIsNotYetUsable() {
        MethodVerdict verdict = new MethodVerdict(METHOD, FAMILY, VERSION,
                1, 0, 0, 0, 0, 0, 1, 0);

        MethodReliability estimate = MethodReliability.of(verdict);

        assertThat(estimate.state()).isEqualTo(MethodReliability.State.NOT_YET_USABLE);
        assertThat(estimate.state()).isNotEqualTo(MethodReliability.State.NO_EVIDENCE);
        assertThat(estimate.calls()).isEqualTo(1);
        assertThat(estimate.decisions()).isZero();
    }

    /**
     * A call that returned without anything confirming its result is an observation too.
     */
    @Test
    @DisplayName("a call that confirmed nothing is an observation as well")
    void notEvidenceIsAnObservation() {
        MethodVerdict verdict = new MethodVerdict(METHOD, FAMILY, VERSION,
                1, 0, 0, 0, 0, 0, 0, 1);

        MethodReliability estimate = MethodReliability.of(verdict);

        assertThat(estimate.state()).isEqualTo(MethodReliability.State.NOT_YET_USABLE);
        assertThat(estimate.notEvidence()).isEqualTo(1);
    }

    /**
     * The two states are not decoration: they must not be confused with each other by the
     * code that reads them.
     */
    @Test
    @DisplayName("the state that says nothing was observed is reserved for the empty stratum")
    void noEvidenceIsReservedForTheEmptyStratum() {
        MethodReliability empty = MethodReliability.of(
                new MethodVerdict(METHOD, FAMILY, VERSION, 0, 0, 0, 0, 0, 0, 0, 0));
        MethodReliability observed = MethodReliability.of(
                new MethodVerdict(METHOD, FAMILY, VERSION, 1, 0, 0, 0, 0, 0, 1, 0));

        assertThat(empty.state()).isNotEqualTo(observed.state());
    }

    /**
     * The interval decides between the two usable states, and nothing else does.
     */
    @Test
    @DisplayName("a decided stratum is ESTIMATED only when its interval is narrow enough")
    void aNarrowIntervalIsEstimated() {
        MethodVerdict verdict = new MethodVerdict(METHOD, FAMILY, VERSION,
                TRIALS, SUCCESSES, 0, 0, 0, 0, 0, 0);

        MethodReliability estimate = MethodReliability.of(verdict);

        assertThat(estimate.width())
                .isLessThan(MethodReliability.RELIABILITY_MAX_WIDTH);
        assertThat(estimate.state()).isEqualTo(MethodReliability.State.ESTIMATED);
    }

    /**
     * And a decided stratum whose interval is too wide to act on is not estimated.
     */
    @Test
    @DisplayName("a decided stratum with a wide interval is NOT_YET_USABLE")
    void aWideIntervalIsNotYetUsable() {
        MethodVerdict verdict = new MethodVerdict(METHOD, FAMILY, VERSION,
                FEW_TRIALS, FEW_SUCCESSES, 0, 0, 0, 0, 0, 0);

        MethodReliability estimate = MethodReliability.of(verdict);

        assertThat(estimate.width())
                .isGreaterThanOrEqualTo(MethodReliability.RELIABILITY_MAX_WIDTH);
        assertThat(estimate.state()).isEqualTo(MethodReliability.State.NOT_YET_USABLE);
    }
}
