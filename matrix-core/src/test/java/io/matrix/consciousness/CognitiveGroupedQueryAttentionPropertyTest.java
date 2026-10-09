package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGroupedQueryAttentionPropertyTest {

    /**
     * The model dimension used by {@link #propertyCompressionRatio}.
     *
     * <p>Unit: embedding dimensions. Fixed so the valid head counts are its divisors.</p>
     */
    private static final int RATIO_DIM = 64;

    @Property(tries = 30)
    void propertyCompressionRatio(@ForAll("anySeed") int seed,
                                    @ForAll("validQueryHeads") int qh,
                                    @ForAll("headCountCandidates") int kvCandidate) {
        int kvh = largestDivisorAtMost(qh, kvCandidate);
        // RECON-W34.5: this property constructed CognitiveGroupedQueryAttention with a fixed
        // dim of 64 while drawing query heads from 1..16, and the guard checked only
        // `qh % kvh`. It never checked `64 % qh`, so any head count that does not divide 64 --
        // 5, 6, 7, 9, 11, 13, 14, 15 -- threw IllegalArgumentException from the constructor
        // and FAILED THE TEST. A property test that throws is not testing a property; it is
        // testing that its generator happens to respect a precondition, and when that
        // precondition is violated it reports a spurious failure rather than a real defect.
        //
        // The early `return` made it worse in the other direction: most of the 30 tries
        // returned before asserting anything, so the property was silently vacuous for the
        // majority of its budget. Both providers below generate VALID configurations only, so
        // every try now asserts.
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(RATIO_DIM, qh, kvh, seed);
        assertThat(gqa.compressionRatio()).isEqualTo((double) qh / kvh);
    }

    /**
     * Head counts that divide {@link #RATIO_DIM}.
     *
     * <p>Unit: attention heads. 64's divisors within 1..16 are 1, 2, 4, 8, 16.</p>
     *
     * @return an arbitrary over the valid query-head counts
     */
    @Provide
    Arbitrary<Integer> validQueryHeads() {
        return Arbitraries.of(1, 2, 4, 8, 16);
    }

    /**
     * Candidate KV-head counts, resolved against the drawn query-head count in the property.
     *
     * <p>Grouped-query attention requires query heads to be a whole multiple of KV heads, so
     * the two cannot be drawn independently -- {@code largestDivisorAtMost} intersects them.</p>
     *
     * @return an arbitrary over plausible head counts
     */
    @Provide
    Arbitrary<Integer> headCountCandidates() {
        return Arbitraries.of(1, 2, 4, 8, 16);
    }

    /**
     * The largest divisor of {@code qh} that does not exceed {@code candidate}.
     *
     * <p>Unit: attention heads. Used to turn an independently drawn KV-head candidate into a
     * value the constructor will accept, so the property exercises real configurations
     * instead of returning early and asserting nothing.</p>
     *
     * @param qh        query heads, already a divisor of {@link #RATIO_DIM}
     * @param candidate independently drawn KV-head count
     * @return a positive divisor of {@code qh}, never greater than {@code candidate}
     */
    private static int largestDivisorAtMost(int qh, int candidate) {
        for (int k = Math.min(candidate, qh); k >= 1; k--) {
            if (qh % k == 0) {
                return k;
            }
        }
        return 1;
    }

    @Property(tries = 30)
    void propertyAttendPreservesDimensions(@ForAll("anySeed") int seed,
                                              @ForAll("anyDim") int dim) {
        if (dim < 1 || dim % 8 != 0) return;
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(dim, 8, 2, seed);
        double[] v = new double[dim];
        for (int i = 0; i < dim; i++) v[i] = i / dim;
        double[] result = gqa.attend(v);
        assertThat(result.length).isEqualTo(dim);
    }

    @Property(tries = 20)
    void propertySameSeedDeterministic(@ForAll("anySeed") int seed,
                                          @ForAll("anyDim") int dim) {
        if (dim < 1 || dim % 8 != 0) return;
        CognitiveGroupedQueryAttention g1 =
            new CognitiveGroupedQueryAttention(dim, 8, 2, seed);
        CognitiveGroupedQueryAttention g2 =
            new CognitiveGroupedQueryAttention(dim, 8, 2, seed);
        double[] v = new double[dim];
        for (int i = 0; i < dim; i++) v[i] = i / dim;
        double[] r1 = g1.attend(v);
        double[] r2 = g2.attend(v);
        for (int i = 0; i < dim; i++) {
            assertThat(r1[i]).isCloseTo(r2[i], offset(1e-9));
        }
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anyQueryHeads() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> anyKVHeads() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Integer> anyDim() {
        return Arbitraries.integers().between(8, 256);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
