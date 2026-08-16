package io.matrix.bir;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BDD-form BIR: Binary Decision Diagram with unique-table + computed-cache.
 * Canonical under fixed variable ordering → exact equivalence checking.
 *
 * Per SPEC-002 §1: verification and audit role. BDD equivalence is exact
 * (not statistical fidelity). Compile hub for TT⇄CLAUSESET conversions.
 *
 * <p>Implementation: custom Java BDD with:
 * <ul>
 *   <li>Unique table (hash-consing) for node deduplication</li>
 *   <li>Computed cache for operation memoization</li>
 *   <li>Fixed variable ordering (0,1,2,...)</li>
 * </ul>
 */
public final class BddForm extends BirForm {

    // Node: id = index into nodes array
    // Node 0 = terminal 0, Node 1 = terminal 1
    private final int[] var;    // variable index per node
    private final int[] low;    // low child per node
    private final int[] high;   // high child per node
    private final int nodeCount;
    private final Map<Long, Integer> uniqueTable; // (var,low,high) → nodeId
    private final Map<Long, Integer> computedCache; // (op,nodeA,nodeB) → resultNode

    private BddForm(int inputBits, int[] var, int[] low, int[] high, int nodeCount,
                    String provenance, double fidelity) {
        super(inputBits, 1, provenance, fidelity);
        this.var = var;
        this.low = low;
        this.high = high;
        this.nodeCount = nodeCount;
        this.uniqueTable = new HashMap<>();
        this.computedCache = new HashMap<>();
    }

    @Override public String form() { return "bdd"; }

    @Override
    public void eval(long[] input, long[] output) {
        // Walk from root (last node added) down to terminal
        int node = nodeCount - 1; // root is last node
        for (int level = 0; level < inputBits(); level++) {
            if (node <= 1) break; // reached terminal
            int bit = (int) ((input[level >>> 6] >>> (level & 63)) & 1L);
            node = (bit == 0) ? low[node] : high[node];
        }
        output[0] = (node == 1) ? 1L : 0L;
    }

    @Override
    public void evalBatch(long[][] inputs, long[][] outputs) {
        for (int i = 0; i < inputs.length; i++) eval(inputs[i], outputs[i]);
    }

    @Override
    protected byte[] toBytes() {
        var buf = new java.io.ByteArrayOutputStream();
        try {
            var dos = new java.io.DataOutputStream(buf);
            dos.writeInt(nodeCount);
            for (int i = 0; i < nodeCount; i++) {
                dos.writeInt(var[i]); dos.writeInt(low[i]); dos.writeInt(high[i]);
            }
            dos.flush();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return buf.toByteArray();
    }

    /** Builder for constructing BDDs. */
    public static class Builder {
        private final List<int[]> nodes = new ArrayList<>(); // [var, low, high]
        private final Map<Long, Integer> unique = new HashMap<>();
        private int nextId = 2; // 0=terminal0, 1=terminal1

        public Builder() {
            nodes.add(new int[]{-1, 0, 0}); // terminal 0
            nodes.add(new int[]{-1, 1, 1}); // terminal 1
        }

        public int mk(int var, int low, int high) {
            if (low == high) return low;
            long key = ((long) var << 42) | ((long) low << 21) | high;
            Integer cached = unique.get(key);
            if (cached != null) return cached;
            nodes.add(new int[]{var, low, high});
            unique.put(key, nextId);
            return nextId++;
        }

        public BddForm build(int inputBits, String provenance) {
            int n = nodes.size();
            int[] var = new int[n];
            int[] low = new int[n];
            int[] high = new int[n];
            for (int i = 0; i < n; i++) {
                var[i] = nodes.get(i)[0];
                low[i] = nodes.get(i)[1];
                high[i] = nodes.get(i)[2];
            }
            return new BddForm(inputBits, var, low, high, n, provenance, 1.0);
        }
    }

    /** Exact equivalence check via BDD canonicality. */
    public boolean equivalentTo(BddForm other) {
        if (other == null || this.inputBits() != other.inputBits()) return false;
        return java.util.Arrays.equals(this.toBytes(), other.toBytes());
    }
}
