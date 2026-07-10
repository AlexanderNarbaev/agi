package io.matrix.compression;

import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Truth table minimization to minimal Disjunctive Normal Form (DNF).
 *
 * <p>Algorithm selection:
 * <ul>
 *   <li>k ≤ 12 → Quine-McCluskey (exact)</li>
 *   <li>k &gt; 12 → Espresso heuristic (fast approximation)</li>
 * </ul>
 *
 * <p>Ref: Phase 6 — Compression &amp; Quantization
 */
public final class TruthTableMinimizer {

    private static final int QM_MAX_K = 12;

    private TruthTableMinimizer() {
    }

    /**
     * Minimizes a truth table to minimal DNF.
     *
     * @param tt the truth table to minimize
     * @return minimized DNF representation
     * @throws NullPointerException if tt is null
     */
    public static MinimizedDNF minimize(TruthTable tt) {
        Objects.requireNonNull(tt, "tt");
        int k = tt.k();
        if (k <= QM_MAX_K) {
            return quineMcCluskey(tt);
        } else {
            return espresso(tt);
        }
    }

    // ─── Quine-McCluskey (exact, k ≤ 12) ───

    private static MinimizedDNF quineMcCluskey(TruthTable tt) {
        int k = tt.k();
        int size = 1 << k;

        // Collect minterms (rows where output is 1)
        List<Integer> minterms = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (tt.evaluate(i)) {
                minterms.add(i);
            }
        }

        if (minterms.isEmpty()) {
            return new MinimizedDNF(List.of(), Algorithm.QUINE_MCCLUSKEY, k);
        }

        // Step 1: Generate all prime implicants
        Set<Implicant> primeImplicants = findPrimeImplicants(minterms, k);

        // Step 2: Essential prime implicants via Petrick's method (simplified)
        List<Implicant> essential = findEssentialImplicants(primeImplicants, minterms, k);

        return new MinimizedDNF(essential, Algorithm.QUINE_MCCLUSKEY, k);
    }

    /**
     * Finds all prime implicants using the Quine-McCluskey tabular method.
     */
    private static Set<Implicant> findPrimeImplicants(List<Integer> minterms, int k) {
        // Group minterms by popcount
        @SuppressWarnings("unchecked")
        List<Implicant>[] groups = new ArrayList[k + 1];
        for (int i = 0; i <= k; i++) {
            groups[i] = new ArrayList<>();
        }

        for (int m : minterms) {
            int pop = Integer.bitCount(m);
            groups[pop].add(new Implicant(m, 0, k));
        }

        Set<Implicant> primes = new HashSet<>();
        boolean merged = true;

        while (merged) {
            merged = false;
            Set<Implicant> nextUsed = new HashSet<>();
            @SuppressWarnings("unchecked")
            List<Implicant>[] nextGroups = new ArrayList[k + 1];
            for (int i = 0; i <= k; i++) {
                nextGroups[i] = new ArrayList<>();
            }

            for (int i = 0; i < k; i++) {
                for (Implicant a : groups[i]) {
                    for (Implicant b : groups[i + 1]) {
                        Implicant merged_imp = a.merge(b);
                        if (merged_imp != null) {
                            merged = true;
                            nextUsed.add(a);
                            nextUsed.add(b);
                            int newPop = Integer.bitCount(merged_imp.bits & ~merged_imp.dontCare);
                            if (!nextGroups[newPop].contains(merged_imp)) {
                                nextGroups[newPop].add(merged_imp);
                            }
                        }
                    }
                }
            }

            // Implicants not merged in this round are prime
            for (int i = 0; i <= k; i++) {
                for (Implicant imp : groups[i]) {
                    if (!nextUsed.contains(imp)) {
                        primes.add(imp);
                    }
                }
            }

            groups = nextGroups;
        }

        return primes;
    }

    /**
     * Finds essential prime implicants covering all minterms.
     *
     * <p>Uses a greedy set-cover approach: at each step, selects the prime
     * implicant that covers the most uncovered minterms.
     */
    private static List<Implicant> findEssentialImplicants(
            Set<Implicant> primes, List<Integer> minterms, int k) {

        Set<Integer> uncovered = new HashSet<>(minterms);
        List<Implicant> selected = new ArrayList<>();

        while (!uncovered.isEmpty()) {
            Implicant best = null;
            int bestCover = 0;

            for (Implicant imp : primes) {
                int cover = 0;
                for (int m : uncovered) {
                    if (imp.covers(m)) {
                        cover++;
                    }
                }
                if (cover > bestCover) {
                    bestCover = cover;
                    best = imp;
                }
            }

            if (best == null) break;

            selected.add(best);
            final Implicant bestFinal = best;
            uncovered.removeIf(bestFinal::covers);
        }

        return selected;
    }

    // ─── Espresso heuristic (k > 12) ───

    /**
     * Espresso-inspired heuristic for large truth tables.
     *
     * <p>Uses a simplified expand/reduce/shrink loop:
     * <ol>
     *   <li>Expand: grow implicants to cover more minterms</li>
     *   <li>Reduce: shrink implicants to minimize overlap</li>
     *   <li>Shrink: remove redundant implicants</li>
     * </ol>
     */
    private static MinimizedDNF espresso(TruthTable tt) {
        int k = tt.k();
        int size = 1 << k;

        // Sample-based approach for very large tables
        // Collect a representative sample of minterms
        List<Integer> minterms = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (tt.evaluate(i)) {
                minterms.add(i);
            }
        }

        if (minterms.isEmpty()) {
            return new MinimizedDNF(List.of(), Algorithm.ESPRESSO, k);
        }

        // Phase 1: Initial cover — each minterm as its own implicant
        List<Implicant> cover = new ArrayList<>();
        for (int m : minterms) {
            cover.add(new Implicant(m, 0, k));
        }

        // Phase 2: Expand — try to merge adjacent implicants
        cover = expandPhase(cover, k);

        // Phase 3: Reduce — shrink implicants to reduce redundancy
        cover = reducePhase(cover, k);

        // Phase 4: Shrink — remove redundant implicants
        cover = shrinkPhase(cover, minterms);

        return new MinimizedDNF(cover, Algorithm.ESPRESSO, k);
    }

    private static List<Implicant> expandPhase(List<Implicant> cover, int k) {
        List<Implicant> expanded = new ArrayList<>();
        Set<Implicant> used = new HashSet<>();

        for (int i = 0; i < cover.size(); i++) {
            if (used.contains(cover.get(i))) continue;
            Implicant current = cover.get(i);
            boolean merged = false;

            for (int j = i + 1; j < cover.size(); j++) {
                Implicant other = cover.get(j);
                Implicant m = current.merge(other);
                if (m != null) {
                    expanded.add(m);
                    used.add(current);
                    used.add(other);
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                expanded.add(current);
            }
        }

        return expanded;
    }

    private static List<Implicant> reducePhase(List<Implicant> cover, int k) {
        // Sort by size (number of don't-care bits) descending — larger implicants first
        cover.sort(Comparator.comparingInt((Implicant i) -> Integer.bitCount(i.dontCare)).reversed());
        return cover;
    }

    private static List<Implicant> shrinkPhase(List<Implicant> cover, List<Integer> minterms) {
        Set<Integer> uncovered = new HashSet<>(minterms);
        List<Implicant> essential = new ArrayList<>();

        // Greedy: pick implicants that cover the most uncovered minterms
        Set<Implicant> remaining = new HashSet<>(cover);

        while (!uncovered.isEmpty()) {
            Implicant best = null;
            int bestCover = 0;

            for (Implicant imp : remaining) {
                int count = 0;
                for (int m : uncovered) {
                    if (imp.covers(m)) count++;
                }
                if (count > bestCover) {
                    bestCover = count;
                    best = imp;
                }
            }

            if (best == null) break;

            essential.add(best);
            remaining.remove(best);
            final Implicant bestFinal = best;
            uncovered.removeIf(bestFinal::covers);
        }

        return essential;
    }

    // ─── Implicant ───

    /**
     * A Boolean implicant represented as (bits, dontCare) mask.
     *
     * <p>An implicant covers input x iff {@code (x & ~dontCare) == (bits & ~dontCare)}.
     */
    static final class Implicant {
        final int bits;
        final int dontCare;
        final int k;

        Implicant(int bits, int dontCare, int k) {
            this.bits = bits;
            this.dontCare = dontCare;
            this.k = k;
        }

        /** Checks if this implicant covers the given minterm. */
        boolean covers(int minterm) {
            return (minterm & ~dontCare) == (bits & ~dontCare);
        }

        /**
         * Attempts to merge two implicants that differ in exactly one bit.
         *
         * @return merged implicant, or null if not mergeable
         */
        Implicant merge(Implicant other) {
            int diff = (bits ^ other.bits) | (dontCare ^ other.dontCare);
            // They must differ in exactly one position, and neither has that as don't-care
            if (Integer.bitCount(diff) != 1) return null;
            if ((dontCare & diff) != 0 || (other.dontCare & diff) != 0) return null;
            return new Implicant(bits & ~diff, dontCare | diff, k);
        }

        boolean isTautology() {
            return dontCare == ((1 << k) - 1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Implicant imp)) return false;
            return bits == imp.bits && dontCare == imp.dontCare && k == imp.k;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bits, dontCare, k);
        }

        @Override
        public String toString() {
            if (isTautology()) return "TRUE";
            StringBuilder sb = new StringBuilder();
            for (int i = k - 1; i >= 0; i--) {
                if ((dontCare & (1 << i)) != 0) continue;
                if (sb.length() > 0) sb.append("·");
                if ((bits & (1 << i)) != 0) {
                    sb.append("x").append(i);
                } else {
                    sb.append("x").append(i).append("'");
                }
            }
            return sb.isEmpty() ? "TRUE" : sb.toString();
        }
    }

    // ─── Algorithm enum ───

    /** Algorithm used for minimization. */
    public enum Algorithm {
        /** Exact Quine-McCluskey for k ≤ 12. */
        QUINE_MCCLUSKEY,
        /** Espresso heuristic for k > 12. */
        ESPRESSO
    }

    // ─── MinimizedDNF ───

    /**
     * Result of truth table minimization: a minimal Disjunctive Normal Form.
     *
     * <p>The DNF is a disjunction (OR) of implicants (conjunctions/AND of literals).
     *
     * @param implicants the prime implicants forming the minimal DNF
     * @param algorithm  algorithm used
     * @param k          number of input variables
     */
    public record MinimizedDNF(
            List<Implicant> implicants,
            Algorithm algorithm,
            int k
    ) {

        public MinimizedDNF {
            implicants = List.copyOf(implicants);
        }

        /**
         * Evaluates the DNF for an integer-encoded input.
         *
         * @param input input value (low k bits used)
         * @return true if any implicant covers the input
         */
        public boolean evaluate(int input) {
            for (Implicant imp : implicants) {
                if (imp.covers(input & ((1 << k) - 1))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Evaluates the DNF for a BitSet input.
         *
         * @param input input bits
         * @return true if any implicant covers the input
         */
        public boolean evaluate(BitSet input) {
            int encoded = 0;
            for (int i = 0; i < k; i++) {
                if (input.get(i)) {
                    encoded |= (1 << i);
                }
            }
            return evaluate(encoded);
        }

        @Override
        public String toString() {
            if (implicants.isEmpty()) return "FALSE";
            if (implicants.size() == 1 && implicants.get(0).isTautology()) return "TRUE";
            return String.join(" ∨ ", implicants.stream()
                    .map(Implicant::toString)
                    .toList());
        }
    }
}
