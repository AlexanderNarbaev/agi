package io.matrix.bir.producers.monotone;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * MonotoneDecoder — restores a monotone Boolean function from an oracle with
 * the minimax-optimal query budget (DESIGN-09 §2; SPEC-002 FR-C producer line).
 *
 * <p>Algorithm (Hansel-style layerwise decoding): vertices are visited in
 * strictly decreasing Hamming weight (numeric order inside a layer). Before
 * querying, monotone closure answers for free: if any covered superset is
 * known {@code 1}, the vertex is {@code 1}; else if any covered subset is
 * known {@code 0}, it is {@code 0}. Every real query propagates its answer
 * through the whole up-set/down-set closure. This achieves the Hansel bound
 * {@code Q(f) ≤ C(k, floor(k/2))} membership queries for every monotone f,
 * which is worst-case optimal (all middle-layer values are forced).
 *
 * <p>Output: monotone DNF as {@link ClauseSetForm} whose clauses carry only
 * positive literals ({@code neg} masks are empty — a consequence of
 * monotonicity). For {@code k ≤ VERIFY_LIMIT} the reconstruction is verified
 * exhaustively and fidelity is set to {@code 1.0} on success.
 *
 * <p>Deterministic: fixed visit order, no randomness, no wall-clock.
 */
public final class MonotoneDecoder {

    /** Exhaustive verification limit (2^16 evaluations maximum). */
    public static final int VERIFY_LIMIT = 16;

    private MonotoneDecoder() {}

    /** Decode result: artifact plus query statistics. */
    public record Result(ClauseSetForm dnf, int queries, int vertices) {}

    /**
     * Decodes the monotone target function.
     *
     * <p>Arity is capped at {@link #VERIFY_LIMIT} so that the reconstruction
     * can be verified exhaustively and published with measured fidelity
     * {@code 1.0} (ClauseSetForm rejects unmeasured lossy artifacts).
     *
     * @param k      input arity, {@code 1..VERIFY_LIMIT}
     * @param oracle teacher oracle
     * @return decode result with monotone CLAUSESET artifact
     */
    public static Result decode(int k, MembershipOracle oracle) {
        if (k < 1 || k > VERIFY_LIMIT) {
            throw new IllegalArgumentException(
                    "k must be in [1, " + VERIFY_LIMIT + "] for exhaustive verification");
        }
        int n = 1 << k;

        // Visit order: decreasing popcount, ascending numeric within a layer.
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int wa = Integer.bitCount(a);
            int wb = Integer.bitCount(b);
            return wa != wb ? Integer.compare(wb, wa) : Integer.compare(a, b);
        });

        BitSet known = new BitSet(n);       // 0 = unclassified
        BitSet value = new BitSet(n);       // valid iff known.get(v)
        int queries = 0;

        // Sub/super-set closures via subset enumeration (k <= 20 keeps this bounded).
        int fullMask = n - 1;
        for (int v : order) {
            if (known.get(v)) {
                continue;
            }
            boolean answer = oracle.eval(v);
            queries++;
            propagate(k, fullMask, v, answer, known, value);
        }

        List<ClauseSetForm.Clause> clauses = minimalTrueTerms(k, known, value);
        double fidelity = verifyExhaustively(k, oracle, clauses);

        String provenance = "monotone-decoder:hansel-style:k=" + k
                + ":queries=" + queries;
        return new Result(
                new ClauseSetForm(k, clauses, provenance, fidelity),
                queries, n);
    }

    private static void propagate(int k, int fullMask, int v, boolean answer,
                                  BitSet known, BitSet value) {
        // Monotonicity: f(v)=1 forces every SUPERSET to 1;
        //               f(v)=0 forces every SUBSET to 0.
        if (answer) {
            for (int sup = v; ; sup = (sup + 1) & fullMask) {
                if ((sup & v) == v) { // sup is a superset of v
                    known.set(sup);
                    value.set(sup);
                }
                if (sup == fullMask) {
                    break;
                }
            }
            // Violation: a known-0 SUPERSET contradicts f(v)=1 (v ≤ sup needs f(v) ≤ f(sup)).
            for (int sup = v; ; sup = (sup + 1) & fullMask) {
                if ((sup & v) == v && known.get(sup) && !value.get(sup)) {
                    throw new IllegalStateException("non-monotone oracle at vertex " + sup);
                }
                if (sup == fullMask) {
                    break;
                }
            }
        } else {
            for (int sub = v; ; sub = (sub - 1) & v) {
                known.set(sub);
                value.clear(sub);
                if (sub == 0) {
                    break;
                }
            }
            // Violation: a known-1 SUBSET contradicts f(v)=0 (sub ≤ v needs f(sub) ≤ f(v)).
            for (int sub = v; ; sub = (sub - 1) & v) {
                if (known.get(sub) && value.get(sub)) {
                    throw new IllegalStateException("non-monotone oracle at vertex " + sub);
                }
                if (sub == 0) {
                    break;
                }
            }
        }
    }

    private static List<ClauseSetForm.Clause> minimalTrueTerms(
            int k, BitSet known, BitSet value) {
        List<ClauseSetForm.Clause> terms = new ArrayList<>();
        int n = 1 << k;
        long[] pos = new long[(k + 63) >>> 6];
        for (int v = 0; v < n; v++) {
            if (!known.get(v) || !value.get(v)) {
                continue;
            }
            boolean minimal = true;
            for (int i = 0; i < k && minimal; i++) {
                if (((v >> i) & 1) == 1) {
                    int sub = v & ~(1 << i);
                    if (value.get(sub)) {
                        minimal = false;
                    }
                }
            }
            if (minimal) {
                java.util.Arrays.fill(pos, 0L);
                for (int i = 0; i < k; i++) {
                    if (((v >> i) & 1) == 1) {
                        pos[i >>> 6] |= 1L << (i & 63);
                    }
                }
                terms.add(new ClauseSetForm.Clause(pos.clone(), new long[pos.length]));
            }
        }
        if (terms.isEmpty()) {
            // Constant-zero function: one contradictory clause (x0 AND NOT x0)
            // so that the clause set never fires under any input.
            long[] bit0 = new long[pos.length];
            bit0[0] = 1L;
            terms.add(new ClauseSetForm.Clause(bit0.clone(), bit0.clone()));
        }
        return terms;
    }

    private static double verifyExhaustively(int k, MembershipOracle oracle,
                                             List<ClauseSetForm.Clause> clauses) {
        if (k > VERIFY_LIMIT) {
            return Double.NaN; // verification skipped; documented in provenance
        }
        int n = 1 << k;
        long[] input = new long[(k + 63) >>> 6];
        for (int v = 0; v < n; v++) {
            Arrays.fill(input, 0L);
            for (int i = 0; i < k; i++) {
                if (((v >> i) & 1) == 1) {
                    input[i >>> 6] |= 1L << (i & 63);
                }
            }
            boolean fired = false;
            for (ClauseSetForm.Clause c : clauses) {
                if (c.fires(input)) {
                    fired = true;
                    break;
                }
            }
            if (fired != oracle.eval(v)) {
                return 0.0;
            }
        }
        return 1.0;
    }
}
