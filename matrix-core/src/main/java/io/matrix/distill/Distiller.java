package io.matrix.distill;

import io.matrix.bir.Bir;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;

import java.util.ArrayList;
import java.util.List;

/**
 * Weight Distiller (SPEC-001): converts neural network activations into
 * boolean BIR artifacts.
 *
 * <p>Per SPEC-001: LLM weights are not directly usable in the boolean core.
 * The distiller captures activations from a teacher model and synthesizes
 * boolean functions (TT or CLAUSESET) that approximate the teacher's behavior.
 *
 * <p>Process:
 * <ol>
 *   <li>Capture: run teacher on input corpus, record activations</li>
 *   <li>Binarize: threshold activations to boolean</li>
 *   <li>Synthesize: build TT or CLAUSESET from binarized activations</li>
 *   <li>Validate: measure fidelity of distilled BIR vs teacher</li>
 * </ol>
 */
public final class Distiller {

    private final int inputBits;
    private final double threshold;
    private final List<boolean[]> capturedActivations = new ArrayList<>();
    private final List<long[]> capturedInputs = new ArrayList<>();

    public Distiller(int inputBits, double threshold) {
        this.inputBits = inputBits;
        this.threshold = threshold;
    }

    /** Capture activation from teacher model. */
    public void capture(long[] input, float[] activations) {
        capturedInputs.add(input.clone());
        boolean[] binarized = new boolean[activations.length];
        for (int i = 0; i < activations.length; i++) {
            binarized[i] = activations[i] > threshold;
        }
        capturedActivations.add(binarized);
    }

    /** Synthesize BIR from captured activations. */
    public Bir synthesize(String provenance) {
        if (capturedInputs.isEmpty()) {
            throw new IllegalStateException("no activations captured");
        }

        int outputBits = capturedActivations.get(0).length;
        if (outputBits == 1) {
            return synthesizeTt(provenance);
        } else {
            return synthesizeClauseSet(provenance);
        }
    }

    private TtForm synthesizeTt(String provenance) {
        int size = 1 << inputBits;
        long[] table = new long[(size + 63) / 64];
        for (int i = 0; i < capturedInputs.size(); i++) {
            long[] input = capturedInputs.get(i);
            boolean[] act = capturedActivations.get(i);
            if (act[0]) {
                int idx = (int) (input[0] & ((1L << inputBits) - 1));
                table[idx >>> 6] |= (1L << (idx & 63));
            }
        }
        return new TtForm(inputBits, table, provenance, 1.0);
    }

    private ClauseSetForm synthesizeClauseSet(String provenance) {
        List<ClauseSetForm.Clause> clauses = new ArrayList<>();
        int kWords = (inputBits + 63) / 64;
        for (int i = 0; i < capturedInputs.size(); i++) {
            long[] input = capturedInputs.get(i);
            boolean[] act = capturedActivations.get(i);
            for (int b = 0; b < act.length; b++) {
                if (act[b]) {
                    long[] pos = new long[kWords];
                    long[] neg = new long[kWords];
                    for (int ib = 0; ib < inputBits; ib++) {
                        if (((input[ib >>> 6] >>> (ib & 63)) & 1L) == 1L) {
                            pos[ib >>> 6] |= (1L << (ib & 63));
                        } else {
                            neg[ib >>> 6] |= (1L << (ib & 63));
                        }
                    }
                    clauses.add(new ClauseSetForm.Clause(pos, neg));
                }
            }
        }
        return new ClauseSetForm(inputBits, clauses, provenance, 1.0);
    }

    /** Measure fidelity of distilled BIR vs teacher on test inputs. */
    public double fidelity(Bir distilled, long[][] testInputs, float[][] expectedActivations) {
        if (testInputs.length != expectedActivations.length) {
            throw new IllegalArgumentException("mismatched test sets");
        }
        int correct = 0;
        for (int i = 0; i < testInputs.length; i++) {
            long[] out = new long[1];
            ((io.matrix.bir.BirForm) distilled).eval(testInputs[i], out);
            boolean predicted = out[0] == 1;
            boolean expected = expectedActivations[i][0] > threshold;
            if (predicted == expected) correct++;
        }
        return (double) correct / testInputs.length;
    }

    public int capturedCount() { return capturedInputs.size(); }
}
