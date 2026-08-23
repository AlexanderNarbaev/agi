package io.matrix.bir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * INV-5 mutation gate (SPEC-002 §4): exact conversions are verified by
 * BDD-equivalence, and an intentionally broken compiler MUST be caught.
 *
 * <p>Each test builds the correct conversion result and a mutated variant
 * modelling a concrete compiler bug class (dropped cube, flipped polarity,
 * complemented edge, bit-flipped table, constant confusion). The gate —
 * {@link BooleanRuntime#equivalent} plus exhaustive eval comparison — must
 * accept the correct artifact and reject every mutant. A gate that accepts a
 * mutant is a verification bug; a gate that rejects the correct artifact is a
 * false positive (equally fatal for CI).
 */
class BirCompilerMutationTest {

    private static final int K = 4;

    /** majority-3 of 4: fires when at least 3 of 4 inputs are 1. */
    private static TtForm majority3of4() {
        long[] table = new long[1];
        for (int i = 0; i < (1 << K); i++) {
            if (Integer.bitCount(i) >= 3) table[0] |= (1L << i);
        }
        return new TtForm(K, table, "mutation-test-source", 1.0);
    }

    /** Exhaustive eval comparison for small k — the ground-truth oracle. */
    private static boolean evalEquals(Bir a, Bir b, int k) {
        long[] outA = new long[1];
        long[] outB = new long[1];
        for (int i = 0; i < (1 << k); i++) {
            long[] in = {i};
            ((BirForm) a).eval(in, outA);
            ((BirForm) b).eval(in, outB);
            if (outA[0] != outB[0]) return false;
        }
        return true;
    }

    private static void assertGate(Bir source, Bir candidate, boolean expectedEquivalent) {
        assertThat(BooleanRuntime.equivalent(source, candidate))
                .as("BDD-equivalence gate")
                .isEqualTo(expectedEquivalent);
        assertThat(evalEquals(source, candidate, K))
                .as("exhaustive eval oracle")
                .isEqualTo(expectedEquivalent);
    }

    // ---- Positive controls: the correct compiler passes the gate ----

    @Test
    void correctTtToBddPassesGate() {
        TtForm tt = majority3of4();
        assertGate(tt, BirCompiler.ttToBdd(tt), true);
    }

    @Test
    void correctTtToClauseSetPassesGate() {
        TtForm tt = majority3of4();
        assertGate(tt, BirCompiler.ttToClauseSet(tt), true);
    }

    @Test
    void correctRoundTripsPassGate() {
        TtForm tt = majority3of4();
        assertGate(tt, BirCompiler.bddToTt(BirCompiler.ttToBdd(tt)), true);
        assertGate(tt, BirCompiler.clauseSetToTt(BirCompiler.ttToClauseSet(tt)), true);
    }

    // ---- Mutants: intentionally broken compiler outputs are caught ----

    /** Mutant 1: dropped cube — one covering clause lost in TT→CLAUSESET. */
    @Test
    void droppedClauseIsCaught() {
        TtForm tt = majority3of4();
        ClauseSetForm correct = BirCompiler.ttToClauseSet(tt);
        assertThat(correct.clauses()).hasSizeGreaterThan(1);
        List<ClauseSetForm.Clause> dropped = new ArrayList<>(correct.clauses());
        dropped.remove(0);
        ClauseSetForm mutant = new ClauseSetForm(K, dropped, "mutant:dropped-clause", 1.0);
        assertGate(tt, mutant, false);
    }

    /** Mutant 2: flipped polarity — pos/neg swapped on one literal. */
    @Test
    void flippedPolarityIsCaught() {
        TtForm tt = majority3of4();
        ClauseSetForm correct = BirCompiler.ttToClauseSet(tt);
        List<ClauseSetForm.Clause> flipped = new ArrayList<>(correct.clauses());
        ClauseSetForm.Clause c0 = flipped.get(0);
        long used = c0.pos[0] | c0.neg[0];
        assertThat(used).as("clause must use at least one literal").isNotZero();
        long lit = Long.lowestOneBit(used);
        long newPos;
        long newNeg;
        if ((c0.pos[0] & lit) != 0) {
            newPos = c0.pos[0] & ~lit;
            newNeg = c0.neg[0] | lit;
        } else {
            newPos = c0.pos[0] | lit;
            newNeg = c0.neg[0] & ~lit;
        }
        flipped.set(0, new ClauseSetForm.Clause(new long[]{newPos}, new long[]{newNeg}));
        ClauseSetForm mutant = new ClauseSetForm(K, flipped, "mutant:flipped-polarity", 1.0);
        assertGate(tt, mutant, false);
    }

    /** Mutant 3: complemented edge — low/high swapped at the root of the BDD. */
    @Test
    void complementedEdgeIsCaught() {
        // f(x0,x1) = x0 AND x1, built correctly and with swapped edges at root.
        BddForm.Builder correct = new BddForm.Builder();
        int hi = correct.mk(1, 0, 1);
        int rootOk = correct.mk(0, 0, hi);
        BddForm ok = correct.build(2, "correct", rootOk);

        BddForm.Builder broken = new BddForm.Builder();
        int hi2 = broken.mk(1, 0, 1);
        int rootBad = broken.mk(0, hi2, 0); // low/high swapped
        BddForm mutant = broken.build(2, "mutant:complemented-edge", rootBad);

        assertThat(BooleanRuntime.equivalent(ok, mutant)).isFalse();
        // sanity: mutant actually computes a different function (x0 NAND-ish path)
        long[] out = new long[1];
        ok.eval(new long[]{0b11}, out);
        assertThat(out[0]).isEqualTo(1L);
        mutant.eval(new long[]{0b11}, out);
        assertThat(out[0]).isEqualTo(0L);
    }

    /** Mutant 4: bit-flipped table — one minterm lost before TT→BDD. */
    @Test
    void bitFlippedTableIsCaught() {
        TtForm tt = majority3of4();
        long[] broken = tt.table();
        broken[0] ^= 1L << 14; // flip minterm 14 (1110b, weight 3 → was 1)
        TtForm mutant = new TtForm(K, broken, "mutant:bit-flip", 1.0);
        assertGate(tt, BirCompiler.ttToBdd(mutant), false);
    }

    /** Mutant 5: constant confusion — const-0 emitted instead of the function. */
    @Test
    void constantConfusionIsCaught() {
        TtForm tt = majority3of4();
        ClauseSetForm constZero = new ClauseSetForm(K, List.of(), "mutant:const-zero", 1.0);
        assertGate(tt, constZero, false);
        // and const-1: a clause with no literals fires on every input
        ClauseSetForm constOne = new ClauseSetForm(K,
                List.of(new ClauseSetForm.Clause(new long[1], new long[1])),
                "mutant:const-one", 1.0);
        assertGate(tt, constOne, false);
    }

    /** Vacuity guard: the gate must also distinguish two genuinely different functions. */
    @Test
    void gateIsNotVacuous() {
        TtForm majority = majority3of4();
        long[] parityTable = new long[1];
        for (int i = 0; i < (1 << K); i++) {
            if (Integer.bitCount(i) % 2 == 1) parityTable[0] |= (1L << i);
        }
        TtForm parity = new TtForm(K, parityTable, "parity", 1.0);
        assertGate(majority, parity, false);
    }
}
