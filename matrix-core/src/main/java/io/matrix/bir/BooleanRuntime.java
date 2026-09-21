package io.matrix.bir;

/**
 * Boolean Runtime: single point of execution for BIR forms.
 *
 * Per SPEC-002 §2: the runtime contract is {@code evaluate(Bir, long[]) → long[]}.
 * Any form can be executed without knowing which form it is.
 * Substrate substitution (JVM→FPGA→quantum) = new backend, BIR unchanged.
 */
public final class BooleanRuntime {

    private BooleanRuntime() {}

    /** Evaluate a single input. */
    public static long[] evaluate(Bir bir, long[] input) {
        if (!(bir instanceof BirForm form)) {
            throw new IllegalArgumentException("Not a BirForm: " + bir.getClass());
        }
        long[] output = new long[(form.outputBits() + 63) / 64];
        form.eval(input, output);
        return output;
    }

    /** Evaluate a batch of inputs (hot path). */
    public static long[][] evaluateBatch(Bir bir, long[][] inputs) {
        if (!(bir instanceof BirForm form)) {
            throw new IllegalArgumentException("Not a BirForm: " + bir.getClass());
        }
        long[][] outputs = new long[inputs.length][(form.outputBits() + 63) / 64];
        form.evalBatch(inputs, outputs);
        return outputs;
    }

    /** Check exact equivalence of two BIR artifacts via BDD canonicality. */
    public static boolean equivalent(Bir a, Bir b) {
        BddForm bddA = BirCompiler.toBdd(a);
        BddForm bddB = BirCompiler.toBdd(b);
        return bddA.equivalentTo(bddB);
    }
}
