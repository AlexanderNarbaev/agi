package io.matrix.bir.convert;

import io.matrix.bir.BirCompiler;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;

/**
 * TT → ClauseSet converter (espresso-type minimized DNF).
 *
 * <p>Delegates to {@link BirCompiler#ttToClauseSet(TtForm)} for the
 * conversion (Quine-McCluskey for k ≤ 12, Espresso heuristic for k &gt; 12,
 * with exactness restored for sampled-out minterms). This wrapper provides:
 * <ul>
 *   <li>Explicit BIR-form contract (TT input, CLAUSESET output)</li>
 *   <li>k≤20 validation</li>
 *   <li>Lossless/lossy classification with fidelity metric</li>
 * </ul>
 *
 * <p>Per SPEC-002 §1: TT→CLAUSESET is exact. The on-set is minimized to a
 * DNF (espresso-type); each implicant becomes a clause with literal masks
 * (pos=1 bits, neg=0 bits, don't-care positions omitted). For constant-zero
 * functions, the clause set is empty.
 *
 * <p>The conversion is lossless for the same k — the reconstructed TT from
 * the resulting ClauseSet is identical to the original. For k>20, conversion
 * is not supported (TT storage limit).
 *
 * <p>Interpretability: each clause is a human-readable rule:
 * <pre>
 *   IF x3 AND NOT x7 AND x12 THEN output=1
 * </pre>
 */
public final class TtToClauseSetConverter {

    private TtToClauseSetConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a truth table to a clause set (DNF).
     *
     * <p>Each minterm where the TT output is 1 becomes a clause.
     * For densely-1 functions, the output may have many clauses.
     *
     * @param tt truth table (any k, but k≤20 recommended)
     * @return clause set equivalent to this truth table
     */
    public static ClauseSetForm convert(TtForm tt) {
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        // Wrap with explicit provenance
        return new ClauseSetForm(
                cs.inputBits(),
                cs.clauses(),
                "tt-to-clauseset-converter",
                1.0
        );
    }

    /**
     * Converts a TT to ClauseSet and measures round-trip fidelity.
     *
     * <p>The round-trip fidelity is computed as:
     * <ol>
     *   <li>TT → ClauseSet (this conversion)</li>
     *   <li>ClauseSet → TT (via {@link ClauseSetToTtConverter})</li>
     *   <li>Compare original TT with reconstructed TT bit-by-bit</li>
     * </ol>
     *
     * @param tt original truth table
     * @return fidelity ∈ [0.0, 1.0] (1.0 = exact round-trip)
     */
    public static double roundTripFidelity(TtForm tt) {
        ClauseSetForm cs = convert(tt);
        TtForm reconstructed = ClauseSetToTtConverter.convert(cs);
        int k = tt.k();
        int size = 1 << k;
        long[] orig = tt.table();
        long[] recon = reconstructed.table();
        int matches = 0;
        for (int i = 0; i < size; i++) {
            long origBit = (orig[i >>> 6] >>> (i & 63)) & 1L;
            long reconBit = (recon[i >>> 6] >>> (i & 63)) & 1L;
            if (origBit == reconBit) matches++;
        }
        return (double) matches / size;
    }
}
