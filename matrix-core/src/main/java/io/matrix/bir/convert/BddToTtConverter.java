package io.matrix.bir.convert;

import io.matrix.bir.BddForm;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.TtForm;

/**
 * BDD → TT converter: traverses the BDD to reconstruct a full truth table.
 *
 * <p>Delegates to {@link BirCompiler#bddToTt(BddForm)} for the conversion.
 * This wrapper provides:
 * <ul>
 *   <li>Explicit BIR-form contract (BDD input, TT output)</li>
 *   <li>k≤20 validation (TT storage limit)</li>
 *   <li>Provenance tagging: "compiled-from-bdd"</li>
 * </ul>
 *
 * <p>Per SPEC-002 §1: BDD→TT is exact (fidelity = 1.0). The algorithm
 * evaluates the BDD for all 2^k input combinations and populates the
 * truth table. Complexity: O(2^k · d) where d = BDD depth (≤ k).
 *
 * <p>Round-trip property: {@code convert(TtToBddConverter.convert(tt))}
 * must produce a TT equivalent to the original for the same k.
 */
public final class BddToTtConverter {

    private BddToTtConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a BDD to a full truth table.
     *
     * @param bdd binary decision diagram with k ≤ 20
     * @return full truth table (2^k entries), fidelity = 1.0
     * @throws IllegalArgumentException if k > 20
     */
    public static TtForm convert(BddForm bdd) {
        if (bdd.inputBits() > 20) {
            throw new IllegalArgumentException(
                    "BDD→TT requires k ≤ 20, got k=" + bdd.inputBits());
        }
        TtForm tt = BirCompiler.bddToTt(bdd);
        // Re-wrap with explicit converter provenance
        return new TtForm(tt.k(), tt.table(), "bdd-to-tt-converter", tt.fidelity());
    }

    /**
     * Converts a BDD to a legacy {@link io.matrix.neuron.TruthTable}.
     *
     * @param bdd binary decision diagram with k ≤ 20
     * @return legacy truth table equivalent to this BDD
     */
    public static io.matrix.neuron.TruthTable toLegacy(BddForm bdd) {
        TtForm tt = convert(bdd);
        int k = tt.k();
        int size = 1 << k;
        java.util.BitSet bits = new java.util.BitSet(size);
        long[] table = tt.table();
        for (int i = 0; i < size; i++) {
            if (((table[i >>> 6] >>> (i & 63)) & 1L) == 1L) {
                bits.set(i);
            }
        }
        return io.matrix.neuron.TruthTable.of(k, bits);
    }
}
