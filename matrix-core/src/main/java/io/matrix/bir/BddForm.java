package io.matrix.bir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BDD-form BIR: reduced ordered Binary Decision Diagram (ROBDD).
 *
 * <p>Canonical under a fixed variable ordering (natural order {@code 0..k-1}),
 * which makes exact equivalence checking possible: two reduced ordered BDDs
 * with the same variable ordering represent the same Boolean function iff
 * their structures match — independent of construction sequence (see
 * {@link #equivalentTo}).
 *
 * <p>Implementation notes (SPEC-002 §1, FR-A2):
 * <ul>
 *   <li><b>Hash-consing</b> lives in {@link Builder#mk} (unique table per
 *       builder): nodes with {@code low == high} collapse to the child and
 *       structurally identical nodes share storage, so every built form is
 *       reduced.</li>
 *   <li><b>Computed caches</b> for Boolean operations ({@link #apply},
 *       {@link #not}) are call-local maps keyed by operand node pairs — they
 *       exist only for the duration of one operation invocation and keep the
 *       immutable form instances free of manager state.</li>
 *   <li>All operations are exact ({@code fidelity == 1.0}) and deterministic:
 *       integer arithmetic only, no randomness, no wall-clock.</li>
 * </ul>
 */
public final class BddForm extends BirForm {

    /**
     * Binary boolean operators supported by {@link #apply(BddForm, Op)}.
     *
     * <p>Semantics are on the boolean outputs of the two operand functions:
     * {@code result = op(a, b)} where {@code a}, {@code b} ∈ {false, true}.
     */
    public enum Op {
        AND     { @Override boolean eval(boolean a, boolean b) { return a && b; } },
        OR      { @Override boolean eval(boolean a, boolean b) { return a || b; } },
        XOR     { @Override boolean eval(boolean a, boolean b) { return a ^ b; } },
        NAND    { @Override boolean eval(boolean a, boolean b) { return !(a && b); } },
        NOR     { @Override boolean eval(boolean a, boolean b) { return !(a || b); } },
        XNOR    { @Override boolean eval(boolean a, boolean b) { return a == b; } },
        IMPLIES { @Override boolean eval(boolean a, boolean b) { return !a || b; } };

        abstract boolean eval(boolean a, boolean b);
    }

    // Node: id = index into arrays. Node 0 = terminal 0, Node 1 = terminal 1
    // (terminals carry var = -1 and self-children).
    private final int[] var;    // variable index per node (-1 for terminals)
    private final int[] low;    // low child per node
    private final int[] high;   // high child per node
    private final int nodeCount;
    private final int root;     // root node id (NOT derivable from nodeCount: constant functions add no nodes)

    private BddForm(int inputBits, int[] var, int[] low, int[] high, int nodeCount, int root,
                    String provenance, double fidelity) {
        super(inputBits, 1, provenance, fidelity);
        this.var = var;
        this.low = low;
        this.high = high;
        this.nodeCount = nodeCount;
        this.root = root;
    }

    @Override public String form() { return "bdd"; }

    @Override
    public void eval(long[] input, long[] output) {
        // Walk from the root down to a terminal, branching on the node's own
        // variable index. Reduced BDDs skip levels (unique-table reduction in
        // Builder.mk eliminates nodes with low == high), so the walk must not
        // assume the node's variable equals the loop depth.
        int node = root;
        while (node > 1) {
            int v = var[node];
            int bit = (int) ((input[v >>> 6] >>> (v & 63)) & 1L);
            node = (bit == 0) ? low[node] : high[node];
        }
        output[0] = (node == 1) ? 1L : 0L;
    }

    @Override
    public void evalBatch(long[][] inputs, long[][] outputs) {
        for (int i = 0; i < inputs.length; i++) eval(inputs[i], outputs[i]);
    }

    /**
     * Applies a binary boolean operator to {@code this} and {@code other},
     * returning a new immutable reduced ordered BDD representing
     * {@code op(this, other)}.
     *
     * <p>Classic ITE/apply recursion (Brace–Rudell–Bryant 1990) over the
     * merged node-id space of the two operands: terminals (ids 0, 1) are
     * shared; {@code other}'s non-terminal node {@code n ≥ 2} is re-mapped to
     * {@code n + this.nodeCount - 2}. The result is materialized into a fresh
     * {@link Builder} during recursion unwind, so {@link Builder#mk} keeps it
     * reduced and canonical under the fixed variable order.
     *
     * @param other the right-hand operand (must not be null, same arity)
     * @param op    the boolean operator to apply
     * @return a new BddForm, {@code op(this, other)}
     * @throws IllegalArgumentException if {@code other} is null or has a
     *         different {@link #inputBits()}
     */
    public BddForm apply(BddForm other, Op op) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        if (other.inputBits() != inputBits()) {
            throw new IllegalArgumentException(
                    "arity mismatch: this=" + inputBits() + " other=" + other.inputBits());
        }
        Builder out = new Builder();
        Map<Long, Integer> memo = new HashMap<>();
        // other's root must be remapped into the merged id space (terminals stay 0/1)
        int result = applyRec(this, other, nodeCount, out, memo, root, bMap(other.root, nodeCount), op);
        return out.build(inputBits(), "apply(" + op + ")", result);
    }

    /** Complement: {@code ¬this}, a new reduced ordered BDD. */
    public BddForm not() {
        Builder out = new Builder();
        Map<Long, Integer> memo = new HashMap<>();
        int result = complement(this, root, out, memo);
        return out.build(inputBits(), "not(" + provenance() + ")", result);
    }

    /**
     * A constant function (constant-0 or constant-1) of the given arity.
     *
     * <p>The resulting BDD has no decision nodes — its root is terminal 0
     * or terminal 1.
     *
     * @param inputBits  function arity (≥ 1)
     * @param value      {@code true} → constant-1, {@code false} → constant-0
     * @param provenance provenance tag (SPEC-002 INV-4)
     * @throws IllegalArgumentException if {@code inputBits < 1}
     */
    public static BddForm constant(int inputBits, boolean value, String provenance) {
        if (inputBits < 1) {
            throw new IllegalArgumentException("inputBits must be >= 1, got " + inputBits);
        }
        return new Builder().build(inputBits, provenance, value ? 1 : 0);
    }

    @Override
    protected byte[] toBytes() {
        var buf = new java.io.ByteArrayOutputStream();
        try {
            var dos = new java.io.DataOutputStream(buf);
            dos.writeInt(nodeCount);
            dos.writeInt(root);
            for (int i = 0; i < nodeCount; i++) {
                dos.writeInt(var[i]); dos.writeInt(low[i]); dos.writeInt(high[i]);
            }
            dos.flush();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return buf.toByteArray();
    }

    /**
     * Exact equivalence check via BDD canonicality.
     *
     * <p>Both operands are reduced ordered BDDs under the natural fixed
     * variable ordering {@code 0..inputBits-1}; such BDDs are canonical, so
     * structural equality of the (memoized) recursive comparison implies —
     * and is implied by — functional equality. Unlike raw byte comparison of
     * node tables, this is independent of the construction order in which the
     * two forms were built (e.g., direct compilation vs iterative composition
     * via {@link #apply}).
     */
    public boolean equivalentTo(BddForm other) {
        if (other == null || this.inputBits() != other.inputBits()) return false;
        Map<Long, Boolean> memo = new HashMap<>();
        return structurallyEqual(this.root, other.root, other, memo);
    }

    private boolean structurallyEqual(int a, int b, BddForm other, Map<Long, Boolean> memo) {
        // Terminals first: ids 0/1 are the shared terminals in BOTH forms' id
        // spaces, so terminal equality is meaningful. Non-terminal ids live in
        // per-instance id spaces and must be compared structurally.
        boolean ta = a <= 1;
        boolean tb = b <= 1;
        if (ta || tb) return ta && tb && a == b;
        if (this.var[a] != other.var[b]) return false;
        long key = ((long) a << 32) | (b & 0xffffffffL);
        Boolean cached = memo.get(key);
        if (cached != null) return cached;
        boolean result = structurallyEqual(this.low[a], other.low[b], other, memo)
                && structurallyEqual(this.high[a], other.high[b], other, memo);
        memo.put(key, result);
        return result;
    }

    // ─── ITE/apply helpers over the merged node-id space ───

    /** Recursive apply with a call-local memo keyed by {@code (u << 32) | v}. */
    private static int applyRec(BddForm a, BddForm b, int aNc, Builder out,
                                Map<Long, Integer> memo, int u, int v, Op op) {
        long key = ((long) u << 32) | (v & 0xFFFFFFFFL);
        Integer cached = memo.get(key);
        if (cached != null) return cached;

        int r;
        if (u < 2 && v < 2) {
            r = op.eval(u == 1, v == 1) ? 1 : 0;
        } else {
            int tu = u < 2 ? Integer.MAX_VALUE : mVar(u, a, b, aNc);
            int tv = v < 2 ? Integer.MAX_VALUE : mVar(v, a, b, aNc);
            int t = Math.min(tu, tv);
            int uHi = tu == t ? mHigh(u, a, b, aNc) : u;
            int uLo = tu == t ? mLow(u, a, b, aNc) : u;
            int vHi = tv == t ? mHigh(v, a, b, aNc) : v;
            int vLo = tv == t ? mLow(v, a, b, aNc) : v;
            int hi = applyRec(a, b, aNc, out, memo, uHi, vHi, op);
            int lo = applyRec(a, b, aNc, out, memo, uLo, vLo, op);
            r = out.mk(t, lo, hi);
        }
        memo.put(key, r);
        return r;
    }

    /** Recursive complement (¬) with a call-local memo. */
    private static int complement(BddForm a, int n, Builder out, Map<Long, Integer> memo) {
        Long key = (long) n;
        Integer cached = memo.get(key);
        if (cached != null) return cached;
        int r;
        if (n == 0) r = 1;
        else if (n == 1) r = 0;
        else {
            int lo = complement(a, a.low[n], out, memo);
            int hi = complement(a, a.high[n], out, memo);
            r = out.mk(a.var[n], lo, hi);
        }
        memo.put(key, r);
        return r;
    }

    /** Variable index of a merged node id (terminal → -1). */
    private static int mVar(int m, BddForm a, BddForm b, int aNc) {
        return m < aNc ? a.var[m] : b.var[m - aNc + 2];
    }

    /** Low child of a merged node id, translated back to merged ids. */
    private static int mLow(int m, BddForm a, BddForm b, int aNc) {
        return m < aNc ? a.low[m] : bMap(b.low[m - aNc + 2], aNc);
    }

    /** High child of a merged node id, translated back to merged ids. */
    private static int mHigh(int m, BddForm a, BddForm b, int aNc) {
        return m < aNc ? a.high[m] : bMap(b.high[m - aNc + 2], aNc);
    }

    /** Maps {@code b}'s local node id into the merged space (terminals shared). */
    private static int bMap(int local, int aNc) {
        return local < 2 ? local : local + aNc - 2;
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

        /**
         * Builds the BDD with an explicit root node.
         *
         * @param inputBits  number of input variables (all node variables must be < inputBits)
         * @param provenance provenance tag (SPEC-002 INV-4)
         * @param root       root node id as returned by the outermost {@link #mk} call
         *                   (0 or 1 for constant functions — constant-zero adds no nodes,
         *                   so the root is never derivable from the node table)
         */
        public BddForm build(int inputBits, String provenance, int root) {
            int n = nodes.size();
            if (root < 0 || root >= n) {
                throw new IllegalArgumentException("root " + root + " out of range 0.." + (n - 1));
            }
            int[] var = new int[n];
            int[] low = new int[n];
            int[] high = new int[n];
            for (int i = 0; i < n; i++) {
                var[i] = nodes.get(i)[0];
                low[i] = nodes.get(i)[1];
                high[i] = nodes.get(i)[2];
                if (i > 1 && var[i] >= inputBits) {
                    throw new IllegalArgumentException(
                            "node " + i + " has variable " + var[i] + " >= inputBits " + inputBits);
                }
            }
            return new BddForm(inputBits, var, low, high, n, root, provenance, 1.0);
        }
    }
}
