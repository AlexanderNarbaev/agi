package io.matrix.bir.convert;

import io.matrix.bir.BirCompiler;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;

/**
 * ClauseSet → TT converter with fidelity measurement.
 *
 * <p>Delegates to {@link BirCompiler#clauseSetToTt(ClauseSetForm)} for the
 * conversion. This wrapper provides:
 * <ul>
 *   <li>Explicit BIR-form contract (CLAUSESET input, TT output)</li>
 *   <li>k≤20 validation</li>
 *   <li>Fidelity metric: fraction of input combinations where the reconstructed
 *       TT matches the original ClauseSet output</li>
 * </ul>
 *
 * <p>Per SPEC-002 §1: CLAUSESET→TT is exact for k≤20 (brute-force
 * evaluation of all 2^k inputs). For k>20, conversion is lossy and
 * requires fidelity tracking.
 *
 * <p>The fidelity metric is computed as:
 * <pre>
 *   fidelity = matches / 2^k
 * </pre>
 * where {@code matches} is the number of input vectors (0..2^k-1) where
 * the ClauseSet and the reconstructed TT produce the same output.
 */
public final class ClauseSetToTtConverter {

    private ClauseSetToTtConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a ClauseSet to a truth table (exact for k≤20).
     *
     * @param cs clause set with k ≤ 20
     * @return truth table equivalent to the clause set
     * @throws IllegalArgumentException if k > 20
     */
    public static TtForm convert(ClauseSetForm cs) {
        if (cs.inputBits() > 20) {
            throw new IllegalArgumentException(
                    "CLAUSESET→TT requires k ≤ 20, got k=" + cs.inputBits());
        }
        TtForm tt = BirCompiler.clauseSetToTt(cs);
        // Measure fidelity and re-wrap
        double fidelity = measureFidelity(tt, cs);
        return new TtForm(tt.k(), tt.table(), "clauseset-to-tt-converter", fidelity);
    }

    /**
     * Measures how accurately the TT represents the original ClauseSet.
     *
     * <p>Iterates over all 2^k inputs, evaluating both the TT and the
     * original ClauseSet. Returns the fraction of matching outputs.
     *
     * @param tt reconstructed truth table
     * @param cs original clause set
     * @return fidelity ∈ [0.0, 1.0]
     */
    public static double measureFidelity(TtForm tt, ClauseSetForm cs) {
        int k = tt.k();
        int size = 1 << k;
        long[] ttTable = tt.table();
        int matches = 0;
        long[] in = new long[1];
        long[] outTt = new long[1];
        long[] outCs = new long[1];
        for (int i = 0; i < size; i++) {
            in[0] = i;
            cs.eval(in, outCs);
            tt.eval(in, outTt);
            if (outCs[0] == outTt[0]) {
                matches++;
            }
        }
        return (double) matches / size;
    }
}
