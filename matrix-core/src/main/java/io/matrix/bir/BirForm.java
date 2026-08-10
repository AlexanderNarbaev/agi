package io.matrix.bir;

/**
 * Base class for BIR forms. All forms are immutable.
 */
public abstract sealed class BirForm implements Bir
        permits TtForm, ClauseSetForm, BddForm {

    private final int inputBits;
    private final int outputBits;
    private final String provenance;
    private final double fidelity;

    protected BirForm(int inputBits, int outputBits, String provenance, double fidelity) {
        if (inputBits < 1 || inputBits > 4096) {
            throw new IllegalArgumentException("inputBits in 1..4096");
        }
        if (outputBits < 1 || outputBits > 4096) {
            throw new IllegalArgumentException("outputBits in 1..4096");
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
