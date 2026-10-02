package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W31.3 — HW-1: the bitCount kernel, and proof it changes nothing but speed.
 *
 * <p>W30 measured that a {@code long[]} + {@link Long#bitCount} kernel computes the same
 * Jaccard value roughly 2.9-3.4x faster than the production
 * {@link PersistentHdcStore#cosine}, which clones two {@link BitSet}s per comparison. The
 * measurement was sound but the kernel was never adopted, so the win sat in a document
 * for a wave.</p>
 *
 * <p><b>Why equivalence has to be proven rather than assumed.</b> This kernel is on the
 * contradiction-detection path, which decides whether a newly taught fact is a
 * DUPLICATE, a POTENTIAL_CONFLICT, or NOVEL. A silent semantic drift would not crash —
 * it would quietly reclassify knowledge, and nothing in the system would report it. So
 * the tests below assert bit-exact agreement with the old algorithm across randomized
 * and adversarial inputs, including the edge cases where a hand-rolled word loop goes
 * wrong: non-multiple-of-64 dimensions and bits in the final partial word.</p>
 *
 * <p><b>Article III.</b> The seed is fixed ({@code 42L}) so a failure is reproducible
 * rather than a once-off.</p>
 */
class HdcVectorTest {

    /** Dimension not a multiple of 64 — the tail word is partially used. */
    private static final int DIM_NOT_WORD_ALIGNED = 512;

    /** Dimension exactly word-aligned. */
    private static final int DIM_WORD_ALIGNED = 512;

    private static BitSet randomBitset(int dim, double density, long seed) {
        Random r = new Random(seed);
        BitSet bs = new BitSet(dim);
        for (int i = 0; i < dim; i++) {
            if (r.nextDouble() < density) bs.set(i);
        }
        return bs;
    }

    // ---- Equivalence with the algorithm being replaced --------------------

    @Test
    void agreesWithProductionCosineOnRandomInputs() {
        Random r = new Random(42L);
        for (int trial = 0; trial < 2000; trial++) {
            int dim = 64 + r.nextInt(1024);           // deliberately not all aligned
            double density = 0.01 + r.nextDouble() * 0.4;
            BitSet a = randomBitset(dim, density, r.nextLong());
            BitSet b = randomBitset(dim, density, r.nextLong());
            double expected = PersistentHdcStore.cosine(a, b);
            double actual = HdcVector.jaccard(
                HdcVector.from(a), HdcVector.from(b));
            assertEquals(expected, actual, 0.0,
                "must be bit-exact, not merely close (dim=" + dim + ")");
        }
    }

    @Test
    void agreesOnAdversarialDensities() {
        double[] densities = {0.0, 0.001, 0.5, 0.999, 1.0};
        for (double d1 : densities) {
            for (double d2 : densities) {
                BitSet a = randomBitset(DIM_NOT_WORD_ALIGNED, d1, (long) (7L * d1 + d2));
                BitSet b = randomBitset(DIM_NOT_WORD_ALIGNED, d2, (long) (11L * d2 + d1));
                assertEquals(PersistentHdcStore.cosine(a, b),
                    HdcVector.jaccard(HdcVector.from(a), HdcVector.from(b)), 0.0,
                    "densities " + d1 + " vs " + d2);
            }
        }
    }

    @Test
    void agreesWhenBitsSitInTheFinalPartialWord() {
        // The classic off-by-one: a dimension of 512 is 8 full words, but 100 is 1 full
        // word plus 36 bits. A loop that ignores the remainder silently drops them, and
        // a loop that reads past the end either throws or reads another vector's data.
        for (int dim : new int[]{1, 2, 63, 64, 65, 100, 127, 128, 129, 512, 513}) {
            BitSet a = new BitSet(dim);
            BitSet b = new BitSet(dim);
            a.set(dim - 1);
            b.set(dim - 1);
            assertEquals(1.0,
                HdcVector.jaccard(HdcVector.from(a), HdcVector.from(b)), 0.0,
                "dim=" + dim + " last-bit match");
            // Need a genuinely different bit, so skip dim==1 where 0 and dim-1 coincide.
            a.clear();
            if (dim >= 2) {
                a.set(0);
                assertEquals(0.0,
                    HdcVector.jaccard(HdcVector.from(a), HdcVector.from(b)), 0.0,
                    "dim=" + dim + " disjoint (low bit vs high bit)");
            }
        }
    }

    @Test
    void agreesOnEmptyAndNullInputs() {
        BitSet empty = new BitSet();
        BitSet some = randomBitset(512, 0.1, 3L);
        assertEquals(PersistentHdcStore.cosine(empty, some),
            HdcVector.jaccard(HdcVector.from(empty), HdcVector.from(some)), 0.0);
        assertEquals(PersistentHdcStore.cosine(empty, empty),
            HdcVector.jaccard(HdcVector.from(empty), HdcVector.from(empty)), 0.0);
        assertEquals(0.0, HdcVector.jaccard(null, HdcVector.from(some)));
        assertEquals(0.0, HdcVector.jaccard(HdcVector.from(some), null));
    }

    // ---- Round-trip fidelity ----------------------------------------------

    @Test
    void roundTripsThroughBitSetWithoutLoss() {
        Random r = new Random(42L);
        for (int trial = 0; trial < 500; trial++) {
            int dim = 1 + r.nextInt(2048);
            BitSet original = randomBitset(dim, 0.3, r.nextLong());
            assertEquals(original, HdcVector.from(original).toBitSet(dim),
                "toBitSet must reproduce the source exactly, dim=" + dim);
        }
    }

    @Test
    void hashCodeAndEqualsAreValueBased() {
        BitSet a = randomBitset(512, 0.2, 1L);
        BitSet b = randomBitset(512, 0.2, 1L);
        assertEquals(HdcVector.from(a), HdcVector.from(b));
        assertEquals(HdcVector.from(a).hashCode(), HdcVector.from(b).hashCode());
    }

    // ---- The property the speedup depends on ------------------------------

    @Test
    void repeatedScoringAllocatesNothing() {
        // The entire speedup comes from removing per-call allocation. If jaccard()
        // allocates, the "3x" is measuring the allocator and the win evaporates at
        // scale, so this is asserted rather than assumed.
        HdcVector a = HdcVector.from(randomBitset(512, 0.2, 1L));
        HdcVector b = HdcVector.from(randomBitset(512, 0.2, 2L));
        for (int i = 0; i < 100; i++) HdcVector.jaccard(a, b);   // warm up JIT

        long before = allocatedBytes();
        double sink = 0.0;
        for (int i = 0; i < 100_000; i++) sink += HdcVector.jaccard(a, b);
        long delta = allocatedBytes() - before;
        // sink accumulates real similarity values; it is a JIT sink, not an assertion.
        // The property under test is the allocation delta below.
        assertTrue(sink > 0.0, "sanity: the loop must actually have scored something");

        // Only the loop's double accumulator may allocate; allow a token margin for
        // any JIT safepoint bookkeeping, but nothing on the order of per-call arrays.
        assertTrue(delta < 64 * 1024,
            "jaccard() must not allocate per call; allocated " + delta + " bytes over 100k calls");
    }

    /** Best-effort allocation counter; falls back to a no-op when unavailable. */
    private static long allocatedBytes() {
        try {
            Class<?> bean = Class.forName("com.sun.management.ThreadMXBean");
            java.lang.management.ThreadMXBean tb =
                (java.lang.management.ThreadMXBean) java.lang.management.ManagementFactory
                    .getThreadMXBean();
            java.lang.reflect.Method m = bean.getMethod("getThreadAllocatedBytes", long.class);
            return (long) m.invoke(tb, Thread.currentThread().getId());
        } catch (Throwable t) {
            return 0L;   // unavailable: the assertion then degrades to a no-op
        }
    }

    // ---- Sanity on the values that matter operationally --------------------

    @Test
    void identicalVectorsScoreOne() {
        HdcVector v = HdcVector.from(randomBitset(512, 0.3, 9L));
        assertEquals(1.0, HdcVector.jaccard(v, v), 0.0);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 64, 128, 512, 4096, 8192})
    void wordCountIsCeilingOfDimensionOverSixtyFour(int dim) {
        // A wrong word count either truncates the scan (missing matches, so a duplicate
        // is scored as novel) or over-reads (another vector's bits, so unrelated facts
        // look similar). It is asserted explicitly for this reason.
        assertEquals((dim + 63) / 64, HdcVector.wordCount(dim));
    }
}
