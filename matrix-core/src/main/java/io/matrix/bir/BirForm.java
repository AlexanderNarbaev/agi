package io.matrix.bir;

/**
 * Base class for BIR forms. All forms are immutable.
 *
 * <p>Invariants (SPEC-002):
 * <ul>
 *   <li>INV-2: input/output bits bounded by {@link BirLimits#maxLiterals()}
 *       ({@code matrix.bir.max-literals}, default 4096)</li>
 *   <li>INV-3: fidelity must be in [0, 1]; a lossy form (fidelity &lt; 1.0)
 *       is only constructible via a {@code lossy(...)} factory that takes a
 *       measured fidelity value — never silently via the plain constructor</li>
 * </ul>
 */
public abstract sealed class BirForm implements Bir
        permits TtForm, ClauseSetForm, BddForm {

    private final int inputBits;
    private final int outputBits;
    private final String provenance;
    private final double fidelity;

    protected BirForm(int inputBits, int outputBits, String provenance, double fidelity) {
        this(inputBits, outputBits, provenance, fidelity, false);
    }

    protected BirForm(int inputBits, int outputBits, String provenance, double fidelity,
                      boolean measuredFidelity) {
        int maxLiterals = BirLimits.maxLiterals();
        if (inputBits < 1 || inputBits > maxLiterals) {
            throw new IllegalArgumentException("inputBits in 1.." + maxLiterals);
        }
        if (outputBits < 1 || outputBits > maxLiterals) {
            throw new IllegalArgumentException("outputBits in 1.." + maxLiterals);
        }
        if (Double.isNaN(fidelity) || fidelity < 0.0 || fidelity > 1.0) {
            throw new IllegalArgumentException("fidelity must be in [0, 1], got: " + fidelity);
        }
        if (fidelity < 1.0 && !measuredFidelity) {
            throw new IllegalArgumentException(
                    "lossy BIR (fidelity < 1.0) requires a measured fidelity value; "
                            + "use the lossy(...) factory instead of the plain constructor");
        }
        this.inputBits = inputBits;
        this.outputBits = outputBits;
        this.provenance = provenance == null ? "unknown" : provenance;
        this.fidelity = fidelity;
    }

    @Override public int inputBits() { return inputBits; }
    @Override public int outputBits() { return outputBits; }
    @Override public String provenance() { return provenance; }
    @Override public double fidelity() { return fidelity; }

    @Override
    public byte[] contentHash() {
        try {
            return java.security.MessageDigest.getInstance("SHA3-256").digest(toBytes());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA3-256 unavailable", e);
        }
    }

    /** Serialize to canonical bytes for hashing. */
    protected abstract byte[] toBytes();

    /** Evaluate a single input vector. */
    public abstract void eval(long[] input, long[] output);

    /** Evaluate a batch of inputs (hot path). */
    public abstract void evalBatch(long[][] inputs, long[][] outputs);
}
