package io.matrix.bir;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jqwik property tests for SPEC-002 FR-A2: BDD canonicality under a fixed
 * variable ordering, cross-form eval equivalence, and round-trip identity
 * of the BirCompiler conversions.
 *
 * <p>All generators are deterministic: jqwik runs are seeded (fixed seeds
 * below), and no unseeded {@code Random} is used anywhere.
 */
class BirPropertiesTest {

    // ─── generators ───

    /** Dense random truth tables, k in 1..10 (QM minimization path, fast). */
    @Provide
    Arbitrary<TtForm> denseTtK1To10() {
        return Arbitraries.integers().between(1, 10).flatMap(k -> ttOf(k, false));
    }

    /** Dense random truth tables, k in 1..12 (exhaustive eval still cheap). */
    @Provide
    Arbitrary<TtForm> denseTtK1To12() {
        return Arbitraries.integers().between(1, 12).flatMap(k -> ttOf(k, false));
    }

    /**
     * Sparse random truth tables, k in 13..16 (Espresso minimization path).
     * Sparse (≈1/8 density) keeps the heuristic minimizer fast; dense tables
     * at k≥13 cost several seconds per sample.
     */
    @Provide
    Arbitrary<TtForm> sparseTtK13To16() {
        return Arbitraries.integers().between(13, 16).flatMap(k -> ttOf(k, true));
    }

    /** 512 deterministic sample inputs over the full 16-bit input space. */
    @Provide
    Arbitrary<List<Integer>> sampledInputs16() {
        return Arbitraries.integers().between(0, (1 << 16) - 1).list().ofSize(512);
    }

    static Arbitrary<TtForm> ttOf(int k, boolean sparse) {
        int size = 1 << k;
        int words = (size + 63) / 64;
        return Arbitraries.longs().array(long[].class).ofSize(words)
                .map(raw -> {
                    long[] wordsArr = sparse ? sparsify(raw) : raw;
                    return new TtForm(k, maskLastWord(wordsArr, k), "jqwik-gen", 1.0);
                });
    }

    /** Deterministic density reduction (~1/8 ones) via self-masking. */
    private static long[] sparsify(long[] words) {
        long[] out = new long[words.length];
        for (int i = 0; i < words.length; i++) {
            long w = words[i];
            out[i] = w & Long.rotateLeft(w, 21) & Long.rotateLeft(w, 42);
        }
        return out;
    }

    /** Clears bits above 2^k in the last word so tables compare cleanly. */
    private static long[] maskLastWord(long[] words, int k) {
        long[] copy = words.clone();
        int bits = (1 << k) & 63; // 0 for k >= 6 (2^k is a multiple of 64)
        if (bits != 0) {
            copy[copy.length - 1] &= (1L << bits) - 1;
        }
        return copy;
    }

    private static TtForm constantTt(int k, boolean value) {
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        if (value) java.util.Arrays.fill(table, -1L);
        return new TtForm(k, maskLastWord(table, k), "jqwik-const", 1.0);
    }

    // ─── BDD canonicality (FR-A2) ───

    /**
     * The same boolean function produced via two independent construction
     * paths (direct TT, and TT rebuilt through the CLAUSESET round-trip)
     * must compile to canonically equal BDDs under the fixed variable order.
     */
    @Property(tries = 20, seed = "20260822")
    void bddCanonicalAcrossConstructionPaths(@ForAll("denseTtK1To10") TtForm tt) {
        BddForm direct = BirCompiler.ttToBdd(tt);
        BddForm viaClauseSet =
                BirCompiler.ttToBdd(BirCompiler.clauseSetToTt(BirCompiler.ttToClauseSet(tt)));
        assertThat(direct.equivalentTo(viaClauseSet))
                .as("BDDs of the same function must be canonically equal")
                .isTrue();
        assertThat(direct.contentHash()).isEqualTo(viaClauseSet.contentHash());
    }

    /** Manual BddForm.Builder construction is deterministic and canonical. */
    @Property(tries = 20, seed = "20260823")
    void bddBuilderCanonicalForSameFunction(@ForAll("denseTtK1To10") TtForm tt) {
        // Two independent builder runs of the identical Shannon expansion.
        BddForm first = BirCompiler.ttToBdd(tt);
        BddForm second = BirCompiler.ttToBdd(new TtForm(tt.k(), tt.table(), "other-provenance", 1.0));
        assertThat(first.equivalentTo(second)).isTrue();
    }

    // ─── TT⇄BDD round-trip and eval equivalence ───

    /** eval(BDD) = eval(TT) for every input, exhaustive for k ≤ 12. */
    @Property(tries = 25, seed = "20260824")
    void bddEvalMatchesTt(@ForAll("denseTtK1To12") TtForm tt) {
        BddForm bdd = BirCompiler.ttToBdd(tt);
        long[] in = new long[1];
        long[] outTt = new long[1];
        long[] outBdd = new long[1];
        for (int i = 0; i < (1 << tt.k()); i++) {
            in[0] = i;
            tt.eval(in, outTt);
            bdd.eval(in, outBdd);
            assertThat(outBdd[0]).as("mismatch at input %d (k=%d)", i, tt.k()).isEqualTo(outTt[0]);
        }
    }

    /** TT→BDD→TT is the identity on the packed table. */
    @Property(tries = 25, seed = "20260825")
    void ttToBddToTtRoundTrip(@ForAll("denseTtK1To12") TtForm tt) {
        TtForm back = BirCompiler.bddToTt(BirCompiler.ttToBdd(tt));
        assertThat(back.table()).isEqualTo(tt.table());
    }

    /**
     * Constant functions (all-zeros / all-ones) through every conversion:
     * TT→BDD eval parity, TT→BDD→TT and TT→CLAUSESET→TT round-trips.
     * Constants are the edge case where BDD reduction collapses the whole
     * diagram to a terminal node.
     */
    @Property(tries = 17, seed = "20260828")
    void constantFunctionsSurviveAllConversions(@ForAll @IntRange(min = 1, max = 16) int k,
                                                @ForAll boolean value) {
        TtForm tt = constantTt(k, value);
        long expected = value ? 1L : 0L;
        long[] in = new long[1];
        long[] out = new long[1];

        BddForm bdd = BirCompiler.ttToBdd(tt);
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);

        int size = 1 << k;
        int step = Math.max(1, size / 256); // up to 256 sampled inputs
        for (int i = 0; i < size; i += step) {
            in[0] = i;
            bdd.eval(in, out);
            assertThat(out[0]).as("bdd eval, input %d (k=%d, value=%b)", i, k, value)
                    .isEqualTo(expected);
            cs.eval(in, out);
            assertThat(out[0]).as("clauseset eval, input %d (k=%d, value=%b)", i, k, value)
                    .isEqualTo(expected);
        }

        assertThat(BirCompiler.bddToTt(bdd).table()).isEqualTo(tt.table());
        assertThat(BirCompiler.clauseSetToTt(cs).table()).isEqualTo(tt.table());
    }

    // ─── TT⇄CLAUSESET round-trip and eval equivalence ───

    /** TT→CLAUSESET→TT is the identity (exact minimization), k ≤ 10 dense. */
    @Property(tries = 20, seed = "20260826")
    void ttToClauseSetToTtRoundTrip(@ForAll("denseTtK1To10") TtForm tt) {
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        TtForm back = BirCompiler.clauseSetToTt(cs);
        assertThat(back.table()).as("round-trip table (k=%d)", tt.k()).isEqualTo(tt.table());
    }

    /**
     * eval(CLAUSESET) = eval(TT) on the Espresso path (k in 13..16) over 512
     * deterministically sampled inputs per function.
     */
    @Property(tries = 8, seed = "20260827")
    void clauseSetEvalMatchesTtEspressoPath(@ForAll("sparseTtK13To16") TtForm tt,
                                            @ForAll("sampledInputs16") List<Integer> inputs) {
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        long mask = (1L << tt.k()) - 1;
        long[] in = new long[1];
        long[] outTt = new long[1];
        long[] outCs = new long[1];
        for (int raw : inputs) {
            in[0] = raw & mask;
            tt.eval(in, outTt);
            cs.eval(in, outCs);
            assertThat(outCs[0]).as("mismatch at input %d (k=%d)", in[0], tt.k()).isEqualTo(outTt[0]);
        }
    }
}
