package io.matrix.bir;

/**
 * TT-form BIR: dense truth table for k ≤ 20 inputs.
 * Storage: long[] little-endian, 2^k bits. Eval: gather + shift.
 *
 * Per SPEC-002 §1: canonical semantics, nanosecond SIMD-eval, direct FPGA LUT mapping.
 */
public final class TtForm extends BirForm {

    private final int k;
    private final long[] table; // 2^k bits packed little-endian

    public TtForm(int k, long[] table, String provenance, double fidelity) {
        super(k, 1, provenance, fidelity);
        if (k < 1 || k > 20) throw new IllegalArgumentException("k in 1..20");
        int expected = ((1 << k) + 63) / 64;
        if (table == null || table.length < expected) {
            throw new IllegalArgumentException("table too small for k=" + k + ": need " + expected + " longs");
        }
        this.k = k;
        this.table = table.clone();
    }

    public int k() { return k; }
    public long[] table() { return table.clone(); }

    @Override public String form() { return "tt"; }

    @Override
    public void eval(long[] input, long[] output) {
        int idx = (int) (input[0] & ((1L << k) - 1));
        output[0] = (table[idx >>> 6] >>> (idx & 63)) & 1L;
    }

    @Override
    public void evalBatch(long[][] inputs, long[][] outputs) {
        long[] t = table;
        long mask = (1L << k) - 1;
        for (int i = 0; i < inputs.length; i++) {
            int idx = (int) (inputs[i][0] & mask);
            outputs[i][0] = (t[idx >>> 6] >>> (idx & 63)) & 1L;
        }
    }

    @Override
    protected byte[] toBytes() {
        var buf = java.nio.ByteBuffer.allocate(8 + table.length * 8);
        buf.putInt(k);
        buf.putInt(table.length);
        for (long l : table) buf.putLong(l);
        return buf.array();
    }

    /** Create from legacy TruthTable. */
    public static TtForm fromTruthTable(io.matrix.neuron.TruthTable tt) {
        int k = tt.k();
        int size = 1 << k;
        long[] table = new long[(size + 63) / 64];
        for (int i = 0; i < size; i++) {
            if (tt.evaluate(i)) table[i >>> 6] |= (1L << (i & 63));
        }
        return new TtForm(k, table, "legacy-truthtable", 1.0);
    }

    /** Memory footprint in bytes. */
    public long memoryBytes() { return 8L * table.length; }
}
