package io.matrix.bir.convert;

import io.matrix.bir.BddForm;
import io.matrix.bir.BirCompiler;
import io.matrix.bir.TtForm;

/**
 * Canonical TT → BDD converter via Shannon expansion.
 *
 * <p>Delegates to {@link BirCompiler#ttToBdd(TtForm)} for the conversion
 * algorithm. This wrapper provides:
 * <ul>
 *   <li>Explicit BIR-form contract (TT input, BDD output)</li>
 *   <li>k≤20 validation (TT max arity for round-trip safety)</li>
 *   <li>Clear converter semantics (vs raw BirCompiler call)</li>
 * </ul>
 *
 * <p>Per SPEC-002 §1: TT⇄BDD is exact (fidelity = 1.0). The resulting BDD
 * is canonical under fixed variable ordering (0,1,2,…), enabling exact
 * equivalence checking via {@link BddForm#equivalentTo(BddForm)}.
 *
 * <p>Algorithm: Shannon expansion — recursively decompose on each variable,
 * building a BDD with a unique-table (hash-consing) for node deduplication.
 * Complexity: O(2^k) worst case, but typical boolean functions compress
 * significantly via shared subgraphs.
 *
 * <p>Example:
 * <pre>{@code
 *   TtForm tt = new TtForm(2, new long[]{0b1000L}, "test", 1.0);
 *   BddForm bdd = TtToBddConverter.convert(tt);
 *   assert bdd.equivalentTo(BirCompiler.ttToBdd(tt));
 * }</pre>
 */
public final class TtToBddConverter {

    private TtToBddConverter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a truth table to a canonical BDD.
     *
     * @param tt truth table with k ≤ 20 inputs
     * @return canonical BDD (fidelity = 1.0, provenance = "tt-to-bdd")
     * @throws IllegalArgumentException if k > 20
     */
    public static BddForm convert(TtForm tt) {
        if (tt.k() > 20) {
            throw new IllegalArgumentException(
                    "TT→BDD requires k ≤ 20, got k=" + tt.k());
        }
        return BirCompiler.ttToBdd(tt);
    }

    /**
     * Converts a legacy {@link io.matrix.neuron.TruthTable} to a canonical BDD.
     *
     * <p>First converts the legacy TruthTable to {@link TtForm} via
     * {@link TtForm#fromTruthTable}, then delegates to {@link #convert(TtForm)}.
     *
     * @param tt legacy truth table (k ≤ 20)
     * @return canonical BDD
     * @throws IllegalArgumentException if k > 20
     */
    public static BddForm fromLegacy(io.matrix.neuron.TruthTable tt) {
        TtForm ttf = TtForm.fromTruthTable(tt);
        return convert(ttf);
    }
}
