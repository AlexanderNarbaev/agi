package io.matrix.ethics.frozen;

import io.matrix.ethics.EthicalFilter;
import io.matrix.neuron.TruthTable;

import java.util.BitSet;
import java.util.Locale;
import java.util.Objects;

/**
 * A single FROZEN MPDT-neuron dedicated to detecting violations of one
 * {@link EthicalFilter.Axiom}.
 *
 * <p>Architectural pillar of L7 §3.1 / L5 §5.1: "FROZEN flagged neurons must be
 * immutable in code (final classes, unmodifiable collections)". Each neuron
 * here is implemented as:
 * <ul>
 *   <li>a {@code final} class (no subclassing);</li>
 *   <li>a {@code final} {@link TruthTable} field (already immutable);</li>
 *   <li>a {@code final} axiom reference.</li>
 * </ul>
 *
 * <p>Activation is deterministic: the same {@code BitSet} of feature bits always
 * produces the same {@code activate()} result. The neuron cannot be retrained,
 * mutated, or replaced at runtime — any change to the table requires replacing
 * the entire neuron, which would be visible to callers (and to the L7 audit
 * log) as a code-level construction change.
 *
 * <p>Ref: L7 §3.1, L5 §5.1.
 */
public final class FrozenAxiomNeuron {

    private final EthicalFilter.Axiom axiom;
    private final TruthTable table;
    private final String tag;       // human-readable label for logs

    /**
     * @param axiom  the axiom this neuron guards
     * @param table  the frozen truth table; size must equal {@code 2^k}
     * @param tag    short tag for logs (e.g. {@code "kill-detector"})
     */
    public FrozenAxiomNeuron(EthicalFilter.Axiom axiom, TruthTable table, String tag) {
        this.axiom = Objects.requireNonNull(axiom, "axiom");
        this.table = Objects.requireNonNull(table, "table");
        this.tag = Objects.requireNonNull(tag, "tag");
    }

    public EthicalFilter.Axiom axiom() { return axiom; }
    public TruthTable table() { return table; }
    public String tag() { return tag; }
    public int k() { return table.k(); }

    /**
     * Activates the neuron against the given feature bits.
     * Returns {@code true} when the input pattern matches a violation
     * (output bit {@code 1}).
     */
    public boolean activate(BitSet features) {
        // Map BitSet (LSB-first, up to k bits) → integer index 0..2^k-1.
        int idx = 0;
        int limit = Math.min(features.length(), table.k());
        for (int i = 0; i < limit; i++) {
            if (features.get(i)) idx |= (1 << i);
        }
        return table.evaluate(idx);
    }

    /**
     * Convenience: activate from a {@code long} bit field (LSB-first,
     * low {@code k} bits used).
     */
    public boolean activate(long features) {
        int idx = (int) (features & ((1L << table.k()) - 1L));
        return table.evaluate(idx);
    }

    /**
     * Stable per-instance identifier — useful for audit logs and
     * {@code equals}/{@code hashCode} in tests.
     */
    public String id() {
        return axiom.name() + ":" + tag;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FrozenAxiomNeuron other)) return false;
        return axiom == other.axiom && tag.equals(other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(axiom, tag);
    }

    @Override
    public String toString() {
        return "FrozenAxiomNeuron[" + id() + " k=" + k() + "]";
    }

    /** Lowercase helper used by the default text feature extractor. */
    static String safeLowercase(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
