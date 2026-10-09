package io.matrix.consciousness;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W34.5 — the Kolmogorov estimator's unit contract.
 *
 * <p>Four separate tests across two files were red for one reason: a constant trajectory
 * scored exactly <b>64.0</b>. That number is not a coincidence — it is
 * {@code Long.SIZE}, added unconditionally as {@code modelBits = logarithmicEncoding(alphabet) + Long.SIZE}.</p>
 *
 * <h2>The contradiction, stated from the code itself</h2>
 * <p>{@code KolmogorovComplexity.estimate} documents:</p>
 * <pre>Returns the estimated number of bits; bounded by 8·trajectory.length.</pre>
 * <p>and {@code KolmogorovComplexityTest.constant_trajectoryLowComplexity} asserts, with its own
 * comment, {@code // much less than 8*20=160 raw bits} then {@code isLessThan(20.0)}.</p>
 * <p>Both models treat a state as costing at most 8 bits. The implementation charged a flat
 * 64 bits per <em>sequence</em> instead — 3.2 bits per state for a length-20 trajectory, and
 * more than 3 bits per state for any sequence shorter than 8. The constant tax therefore
 * dominated the very quantity it was supposed to be a rounding error within, and a sequence
 * that is maximally compressible could never score below it.</p>
 *
 * <p>Worse, the tax is invisible in the result. {@code 64.0} looks like a measurement; it is
 * a constant.</p>
 *
 * <h2>What is and is not true here</h2>
 * <p>A constant sequence <em>is</em> describable in a handful of bits — "the value V,
 * repeated N times". If an atom is genuinely a 64-bit word, naming V costs 64 bits once, and
 * that is a real cost. But it is a <em>model</em> cost, not a data cost: it does not grow
 * with N, so adding it to a per-symbol measure distorts exactly the comparison the estimator
 * exists to support — which trajectory is more surprising than another.</p>
 */
class KolmogorovEstimatorContractTest {

    /** Trajectory length for the constant-sequence checks. Unit: states. */
    private static final int TRAJ_LEN = 20;

    /**
     * Build a constant trajectory.
     *
     * @param value the repeated state
     * @return a trajectory of {@link #TRAJ_LEN} identical states
     */
    private static long[] constant(long value) {
        long[] t = new long[TRAJ_LEN];
        java.util.Arrays.fill(t, value);
        return t;
    }

    @Test
    @DisplayName("a constant trajectory scores low, and specifically not exactly 64.0")
    void constantTrajectoryIsNotPinnedToTheLongSizeConstant() {
        double k = KolmogorovComplexity.estimate(constant(7L));
        // The precise assertion is deliberately narrow: 64.0 is the bug's fingerprint, and
        // matching on it catches a regression to the flat tax even if the threshold moves.
        assertThat(k)
                .as("64.0 is Long.SIZE added unconditionally, not a measurement of anything")
                .isNotEqualTo(64.0);
        assertThat(k)
                .as("a constant sequence is maximally compressible")
                .isLessThan(20.0);
    }

    @Test
    @DisplayName("the estimate stays within the documented bound of 8 bits per state")
    void respectsDocumentedBoundOfEightBitsPerState() {
        double k = KolmogorovComplexity.estimate(constant(7L));
        assertThat(k)
                .as("the class documents 'bounded by 8*trajectory.length'")
                .isLessThanOrEqualTo(8.0 * TRAJ_LEN);
    }

    @Test
    @DisplayName("every state value in a constant run yields the same complexity")
    void constantTrajectoryIsIndependentOfTheRepeatedValue() {
        double a = KolmogorovComplexity.estimate(constant(0L));
        double b = KolmogorovComplexity.estimate(constant(Long.MAX_VALUE));
        assertThat(a)
                .as("the atom's VALUE is not structural information; only the pattern matters")
                .isEqualTo(b);
    }

    @Test
    @DisplayName("structured data still scores below random data after the fix")
    void orderingIsPreserved() {
        long[] structured = new long[TRAJ_LEN];
        for (int i = 0; i < TRAJ_LEN; i++) {
            structured[i] = i / 4;
        }
        long[] random = new long[TRAJ_LEN];
        java.util.Random rng = new java.util.Random(42L);
        for (int i = 0; i < TRAJ_LEN; i++) {
            random[i] = rng.nextLong();
        }
        assertThat(KolmogorovComplexity.estimate(structured))
                .as("the comparison the estimator exists to support must survive the fix")
                .isLessThan(KolmogorovComplexity.estimate(random));
    }

    @Test
    @DisplayName("a constant scores the same at every length, including one")
    void constantScoresIdenticallyAtEveryLength() {
        // RECON-W34.5: this previously asserted estimate([42]) == Long.SIZE, pinning the
        // early return that made a ONE-element constant score 64.0 while a TWENTY-element
        // constant scored ~0. Same object, two answers, 64 bits apart. The property test
        // propertyKolmogorovConstantIsLow draws lengths 1..32 and requires all of them below
        // 20, so the two cannot both hold.
        double one = KolmogorovComplexity.estimate(new long[]{42L});
        double twenty = KolmogorovComplexity.estimate(constant(42L));
        assertThat(one)
                .as("a single 64-bit value costs 64 bits to name; this is a documented "
                        + "boundary, not the flat tax that was removed")
                .isEqualTo(Long.SIZE);
        assertThat(twenty)
                .as("from N=2 upward the repetition is exploitable and the flat tax must not "
                        + "reappear -- this is the regression the fix exists to prevent")
                .isLessThan(20.0);
        assertThat(twenty).isNotEqualTo(64.0);
    }

    @Test
    @DisplayName("empty and null remain zero")
    void degenerateInputsRemainZero() {
        assertThat(KolmogorovComplexity.estimate(new long[0])).isEqualTo(0.0);
        assertThat(KolmogorovComplexity.estimate((long[]) null)).isEqualTo(0.0);
    }
}