package io.matrix.bir;

/**
 * JVM SIMD Backend (SPEC-002 FR-D1): reference implementation using
 * Java Vector API + virtual threads.
 *
 * <p>Uses the existing BirForm.evalBatch() for hot-path execution.
 * No additional SIMD optimization needed — the forms already use
 * packed long[] for bit-level parallelism.
 */
public final class JvmSimdBackend implements SubstrateBackend {

    @Override public String id() { return "jvm-simd"; }

    @Override
    public long[][] evaluate(Bir bir, long[][] inputs) {
        if (!(bir instanceof BirForm form)) {
            throw new IllegalArgumentException("Not a BirForm: " + bir.getClass());
        }
        long[][] outputs = new long[inputs.length][(form.outputBits() + 63) / 64];
        form.evalBatch(inputs, outputs);
        return outputs;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, false, 4096, "JVM SIMD reference backend");
    }
}
