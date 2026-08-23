package io.matrix.bir.convert;

import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TtToClauseSetConverter}: espresso-type minimized DNF
 * conversion with exact round-trip fidelity.
 *
 * <p>The underlying minimization behavior (majority-3 → 3 clauses,
 * constants, k &gt; 12 Espresso path) is covered end-to-end in
 * {@code BirInvariantsTest}; here we pin the converter-level contract:
 * provenance, fidelity, clause counts, and {@code roundTripFidelity}.
 */
class TtToClauseSetConverterTest {

    private static void assertEvalParity(TtForm tt, ClauseSetForm cs) {
        long[] in = new long[1];
        long[] outTt = new long[1];
        long[] outCs = new long[1];
        for (int i = 0; i < (1 << tt.k()); i++) {
            in[0] = i;
            tt.eval(in, outTt);
            cs.eval(in, outCs);
            assertThat(outCs[0]).as("input %d", i).isEqualTo(outTt[0]);
        }
    }

    @Test
    void convertMajority3MinimizedTo3Clauses() {
        // majority-3: minterms 3,5,6,7 → minimal DNF x0x1 + x0x2 + x1x2 (3 clauses)
        TtForm tt = new TtForm(3, new long[]{(1L << 3) | (1L << 5) | (1L << 6) | (1L << 7)},
                "test-maj3", 1.0);
        ClauseSetForm cs = TtToClauseSetConverter.convert(tt);
        assertThat(cs.clauses()).hasSize(3);
        assertThat(cs.provenance()).isEqualTo("tt-to-clauseset-converter");
        assertThat(cs.fidelity()).isEqualTo(1.0);
        assertEvalParity(tt, cs);
    }

    @Test
    void convertSingleMinterm() {
        // f = x0 AND NOT x1 AND x2 (minterm 5, k=3) → exactly one full clause
        TtForm tt = new TtForm(3, new long[]{1L << 5}, "test-single", 1.0);
        ClauseSetForm cs = TtToClauseSetConverter.convert(tt);
        assertThat(cs.clauses()).hasSize(1);
        ClauseSetForm.Clause clause = cs.clauses().get(0);
        assertThat(clause.pos).isEqualTo(new long[]{0b101L});
        assertThat(clause.neg).isEqualTo(new long[]{0b010L});
        assertEvalParity(tt, cs);
    }

    @Test
    void convertConstants() {
        TtForm zero = new TtForm(2, new long[]{0L}, "test-zero", 1.0);
        ClauseSetForm csZero = TtToClauseSetConverter.convert(zero);
        assertThat(csZero.clauses()).isEmpty();
        assertEvalParity(zero, csZero);

        TtForm one = new TtForm(2, new long[]{0b1111L}, "test-one", 1.0);
        ClauseSetForm csOne = TtToClauseSetConverter.convert(one);
        assertThat(csOne.clauses()).hasSize(1);
        assertEvalParity(one, csOne);
    }

    @Test
    void convertParity3NotMinimizable() {
        // parity-3 has no mergeable minterms → 4 full-minterm clauses
        TtForm tt = new TtForm(3, new long[]{0b10010110L}, "test-parity", 1.0);
        ClauseSetForm cs = TtToClauseSetConverter.convert(tt);
        assertThat(cs.clauses()).hasSize(4);
        assertEvalParity(tt, cs);
    }

    @Test
    void roundTripFidelityIsExact() {
        TtForm[] cases = {
                new TtForm(3, new long[]{(1L << 3) | (1L << 5) | (1L << 6) | (1L << 7)}, "maj3", 1.0),
                new TtForm(3, new long[]{0b10010110L}, "parity3", 1.0),
                new TtForm(2, new long[]{0b1000L}, "and2", 1.0),
                new TtForm(3, new long[]{0L}, "zero3", 1.0),
                new TtForm(3, new long[]{0xFFL}, "one3", 1.0),
                new TtForm(4, new long[]{0b0110100110010110L}, "parity4", 1.0),
        };
        for (TtForm tt : cases) {
            assertThat(TtToClauseSetConverter.roundTripFidelity(tt))
                    .as("round-trip fidelity of %s", tt.provenance())
                    .isEqualTo(1.0);
        }
    }
}
