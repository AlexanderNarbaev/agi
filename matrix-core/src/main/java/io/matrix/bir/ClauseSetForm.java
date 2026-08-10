package io.matrix.bir;

import java.util.ArrayList;
import java.util.List;

/**
 * CLAUSESET-form BIR: Tsetlin clauses (DNF) with pos/neg masks in long[].
 * Unbounded arity, learnable via Tsetlin automata feedback.
 *
 * Per SPEC-002 §1: primary form for learning and knowledge storage.
 * Compresses sparse functions; built-in learning (Tsetlin automata);
 * interpretable rules; industrially proven FPGA on-chip training.
 */
public final class ClauseSetForm extends BirForm {

    public static final class Clause {
        public final long[] pos; // required ones
        public final long[] neg; // required zeros

        public Clause(long[] pos, long[] neg) {
            this.pos = pos.clone();
            this.neg = neg.clone();
        }

        /** Fires on input x (little-endian words). */
        public boolean fires(long[] x) {
            for (int w = 0; w < pos.length; w++) {
                if ((x[w] & pos[w]) != pos[w]) return false;
                if ((x[w] & neg[w]) != 0L) return false;
            }
            return true;
        }

        /** Witness: bits that mechanically determined the output (pos|neg). */
        public long[] witnessMask() {
            long[] m = new long[pos.length];
            for (int w = 0; w < pos.length; w++) m[w] = pos[w] | neg[w];
            return m;
        }
    }

    private final int kWords;
    private final List<Clause> clauses;

    public ClauseSetForm(int inputBits, List<Clause> clauses, String provenance, double fidelity) {
        super(inputBits, 1, provenance, fidelity);
        this.kWords = (inputBits + 63) / 64;
        this.clauses = List.copyOf(clauses);
    }

    public List<Clause> clauses() { return clauses; }

    @Override public String form() { return "clauseset"; }

    @Override
    public void eval(long[] input, long[] output) {
        for (Clause c : clauses) {
            if (c.fires(input)) { output[0] = 1L; return; }
        }
        output[0] = 0L;
    }

    @Override
    public void evalBatch(long[][] inputs, long[][] outputs) {
        for (int i = 0; i < inputs.length; i++) {
            eval(inputs[i], outputs[i]);
        }
    }

    @Override
    protected byte[] toBytes() {
        var buf = new java.io.ByteArrayOutputStream();
        try {
            var dos = new java.io.DataOutputStream(buf);
            dos.writeInt(kWords);
            dos.writeInt(clauses.size());
            for (Clause c : clauses) {
                for (long l : c.pos) dos.writeLong(l);
                for (long l : c.neg) dos.writeLong(l);
            }
            dos.flush();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return buf.toByteArray();
    }

    /** Human-readable rule: "IF x1 AND NOT x4 AND ... THEN 1". */
    public String toHumanReadable(Clause c) {
        StringBuilder sb = new StringBuilder("IF ");
        List<String> lits = new ArrayList<>();
        for (int w = 0; w < kWords; w++) {
            for (int b = 0; b < 64; b++) {
                int i = w * 64 + b;
                if (((c.pos[w] >>> b) & 1L) == 1L) lits.add("x" + i);
                if (((c.neg[w] >>> b) & 1L) == 1L) lits.add("NOT x" + i);
            }
        }
        sb.append(String.join(" AND ", lits)).append(" THEN 1");
        return sb.toString();
    }
}
