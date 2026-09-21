package io.matrix.bir;

/**
 * Boolean Intermediate Representation (BIR) — unified interface for all
 * boolean function forms in MATRIX.
 *
 * <p>Per SPEC-002: any computational primitive is a boolean function
 * f: {0,1}^k → {0,1}^m with metadata. Three forms:
 * <ul>
 *   <li>{@link TtForm} — truth table, k≤20, canonical semantics, SIMD-eval</li>
 *   <li>{@link ClauseSetForm} — Tsetlin clauses, unbounded arity, learnable</li>
 *   <li>{@link BddForm} — binary decision diagram, canonical → exact equivalence</li>
 * </ul>
 *
 * <p>All forms are immutable (final fields, defensive copies) — safe
 * publication via JMM final-semantics, no synchronization needed.
 *
 * <p>The runtime contract is {@code evaluate(Bir, long[]) → long[]}: any
 * form can be executed by the BooleanRuntime without knowing which form
 * it is. Substitution of substrate (JVM→FPGA→quantum) = new backend,
 * BIR and verification unchanged.
 */
public sealed interface Bir permits BirForm {

    /** Number of input bits (arity). */
    int inputBits();

    /** Number of output bits. */
    int outputBits();

    /** Form identifier: "tt", "clauseset", "bdd". */
    String form();

    /** Provenance: how this artifact was created (teacher, corpus, mechanism). */
    String provenance();

    /** Measured fidelity (if conversion was lossy, else 1.0). */
    double fidelity();

    /** Content hash (SHA3-256) for lineage tracking. */
    byte[] contentHash();
}
