package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Minimizes boolean functions using the Quine-McCluskey algorithm.
 *
 * <p>This is the L5 §3 "compression as understanding" — finding the
 * minimal boolean formula equivalent to a truth table. Minimised
 * implicants are returned as a sum-of-products representation.
 *
 * <p>Each implicant is encoded as an {@code int[k]} where element {@code j}
 * is one of:
 * <ul>
 *   <li>{@code 0} — input bit {@code j} must be 0</li>
 *   <li>{@code 1} — input bit {@code j} must be 1</li>
 *   <li>{@code 2} — input bit {@code j} is a don't-care</li>
 * </ul>
 *
 * <p>Special cases:
 * <ul>
 *   <li>Constant false (no minterms) → empty list</li>
 *   <li>Constant true (all minterms) → single empty implicant {@code {}}</li>
 * </ul>
 *
 * <p>Complexity: O(3^k / k) — practical for {@code k ≤ 20}.
 */
public final class BooleanMinimizer {

    /** Don't-care sentinel used in the {@code int[]} implicant encoding. */
    public static final int DONT_CARE = 2;

    private BooleanMinimizer() {
    }

    /**
     * Minimize a truth table to its simplest sum-of-products equivalent.
     *
     * @param table input truth table (BitSet of 2^k bits)
     * @param k     number of inputs, 1..{@link TruthTable#K_MAX}
     * @return minimized list of implicants (see class javadoc for encoding)
     */
    public static List<int[]> minimize(BitSet table, int k) {
        if (k < 1 || k > TruthTable.K_MAX) {
            throw new IllegalArgumentException(
                    "k must be in [1, " + TruthTable.K_MAX + "], got: " + k);
        }
        Objects.requireNonNull(table, "table");

        int size = 1 << k;

        // Step 1: extract minterms (input combinations where output = 1)
        List<Integer> minterms = extractMinterms(table, k);
        if (minterms.isEmpty()) {
            return List.of();                       // always false
        }
        if (minterms.size() == size) {
            return List.of(new int[]{});            // always true
        }

        int fullMask = (k == 32) ? -1 : ((1 << k) - 1);

        // Step 2 & 3: find prime implicants via tabulation
        Set<Implicant> primes = findPrimeImplicants(minterms, fullMask);

        // Step 4 & 5: essential prime implicants + greedy cover for the rest
        Set<Integer> mintermSet = new LinkedHashSet<>(minterms);
        List<Implicant> cover = selectCover(primes, mintermSet, k);

        List<int[]> result = new ArrayList<>(cover.size());
        for (Implicant imp : cover) {
            result.add(imp.toIntArray(k));
        }
        return result;
    }

    /**
     * Extracts the minterms (input indices where the output bit is 1).
     */
    static List<Integer> extractMinterms(BitSet table, int k) {
        int size = 1 << k;
        List<Integer> minterms = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (table.get(i)) {
                minterms.add(i);
            }
        }
        return minterms;
    }

    /**
     * Tabulation method: iteratively combine implicants differing in exactly
     * one cared bit. Implicants never combined become prime implicants.
     */
    static Set<Implicant> findPrimeImplicants(List<Integer> minterms, int fullMask) {
        // Initial implicants: one per minterm, all bits cared.
        Set<Implicant> current = new LinkedHashSet<>();
        for (int m : minterms) {
            current.add(new Implicant(m, fullMask));
        }

        Set<Implicant> primes = new LinkedHashSet<>();

        while (!current.isEmpty()) {
            // Group by mask so combinations only happen within equal masks.
            Map<Integer, List<Implicant>> byMask = new HashMap<>();
            for (Implicant imp : current) {
                byMask.computeIfAbsent(imp.mask, x -> new ArrayList<>()).add(imp);
            }

            Set<Implicant> combined = new HashSet<>();
            Set<Implicant> nextLevel = new LinkedHashSet<>();

            for (List<Implicant> group : byMask.values()) {
                // Bucket by popcount of the fixed value bits to restrict
                // comparisons to adjacent buckets (classic QM optimisation).
                Map<Integer, List<Implicant>> byPopcount = new HashMap<>();
                for (Implicant imp : group) {
                    byPopcount.computeIfAbsent(Integer.bitCount(imp.value), x -> new ArrayList<>())
                            .add(imp);
                }

                for (Map.Entry<Integer, List<Implicant>> entry : byPopcount.entrySet()) {
                    int pc = entry.getKey();
                    List<Implicant> high = byPopcount.get(pc + 1);
                    if (high == null) {
                        continue;
                    }
                    for (Implicant a : entry.getValue()) {
                        for (Implicant b : high) {
                            Implicant merged = Implicant.tryCombine(a, b);
                            if (merged != null) {
                                combined.add(a);
                                combined.add(b);
                                nextLevel.add(merged);
                            }
                        }
                    }
                }
            }

            // Anything not combined in this round is a prime implicant.
            for (Implicant imp : current) {
                if (!combined.contains(imp)) {
                    primes.add(imp);
                }
            }
            current = nextLevel;
        }
        return primes;
    }

    /**
     * Selects a cover of all minterms: essential prime implicants first,
     * then a greedy cover for any remaining minterms.
     */
    static List<Implicant> selectCover(Set<Implicant> primes,
                                       Set<Integer> mintermSet, int k) {
        // Build coverage map: minterm -> list of covering prime implicants.
        Map<Integer, List<Implicant>> coveredBy = new HashMap<>();
        for (int m : mintermSet) {
            coveredBy.put(m, new ArrayList<>());
        }
        for (Implicant imp : primes) {
            for (int m : mintermSet) {
                if (imp.covers(m)) {
                    coveredBy.get(m).add(imp);
                }
            }
        }

        Set<Integer> remaining = new LinkedHashSet<>(mintermSet);
        Set<Implicant> chosen = new LinkedHashSet<>();

        // Step 4: essential prime implicants.
        for (Map.Entry<Integer, List<Implicant>> entry : coveredBy.entrySet()) {
            if (entry.getValue().size() == 1) {
                Implicant essential = entry.getValue().get(0);
                if (chosen.add(essential)) {
                    removeCovered(remaining, essential);
                }
            }
        }

        // Step 5: greedy cover for the rest — pick the implicant covering
        // the most remaining minterms until all are covered.
        while (!remaining.isEmpty()) {
            Implicant best = null;
            int bestCount = -1;
            for (Implicant imp : primes) {
                if (chosen.contains(imp)) {
                    continue;
                }
                int count = 0;
                for (int m : remaining) {
                    if (imp.covers(m)) {
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = imp;
                }
            }
            if (best == null || bestCount <= 0) {
                // Should not happen for a valid minterm set, but guard anyway.
                break;
            }
            chosen.add(best);
            removeCovered(remaining, best);
        }

        return new ArrayList<>(chosen);
    }

    private static void removeCovered(Set<Integer> remaining, Implicant imp) {
        remaining.removeIf(imp::covers);
    }

    /**
     * Converts minimised implicants to a {@link DecisionTree} (OR of ANDs
     * realised as a Shannon expansion of the reconstructed truth table).
     *
     * @param implicants minimised implicants (see class javadoc)
     * @param k          number of inputs
     * @return an equivalent decision tree
     */
    public static DecisionTree toDecisionTree(List<int[]> implicants, int k) {
        if (k < 1 || k > TruthTable.K_MAX) {
            throw new IllegalArgumentException(
                    "k must be in [1, " + TruthTable.K_MAX + "], got: " + k);
        }
        Objects.requireNonNull(implicants, "implicants");

        int size = 1 << k;
        BitSet table = new BitSet(size);

        if (implicants.isEmpty()) {
            // constant false
            return DecisionTree.constant(false);
        }

        // Reconstruct the output table by OR-ing each implicant's coverage.
        for (int[] implicant : implicants) {
            if (implicant.length == 0) {
                // empty implicant => constant true
                table.set(0, size);
                break;
            }
            if (implicant.length != k) {
                throw new IllegalArgumentException(
                        "Implicant length " + implicant.length + " != k " + k);
            }
            applyImplicant(table, implicant, k);
        }

        if (table.cardinality() == 0) {
            return DecisionTree.constant(false);
        }
        if (table.cardinality() == size) {
            return DecisionTree.constant(true);
        }
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        return shannonExpand(table, indices, k, 0);
    }

    /**
     * Sets every input index matched by the implicant in the given table.
     */
    private static void applyImplicant(BitSet table, int[] implicant, int k) {
        List<Integer> caredBits = new ArrayList<>();
        int fixedValue = 0;
        int caredMask = 0;
        for (int j = 0; j < k; j++) {
            int v = implicant[j];
            if (v == DONT_CARE) {
                continue;
            }
            if (v != 0 && v != 1) {
                throw new IllegalArgumentException(
                        "Invalid implicant value " + v + " at index " + j);
            }
            caredBits.add(j);
            caredMask |= (1 << j);
            if (v == 1) {
                fixedValue |= (1 << j);
            }
        }
        // Enumerate all don't-care combinations.
        int freeCount = k - caredBits.size();
        int combos = 1 << freeCount;
        for (int c = 0; c < combos; c++) {
            int index = fixedValue;
            int bit = 0;
            for (int j = 0; j < k; j++) {
                if ((caredMask & (1 << j)) == 0) {
                    if ((c & (1 << bit)) != 0) {
                        index |= (1 << j);
                    }
                    bit++;
                }
            }
            table.set(index);
        }
    }

    /**
     * Shannon expansion: recursively splits the index set on successive bits,
     * collapsing to a constant leaf as soon as all remaining indices share the
     * same output value. Operates on the full index space so the
     * {@link DecisionTree.Split} input indices correspond to the original
     * input bits.
     */
    private static DecisionTree shannonExpand(BitSet table, List<Integer> indices,
                                              int k, int bit) {
        boolean first = table.get(indices.get(0));
        boolean constant = true;
        for (int idx : indices) {
            if (table.get(idx) != first) {
                constant = false;
                break;
            }
        }
        if (constant) {
            return DecisionTree.constant(first);
        }
        if (bit >= k) {
            return DecisionTree.constant(first);
        }
        List<Integer> zeros = new ArrayList<>(indices.size() / 2 + 1);
        List<Integer> ones = new ArrayList<>(indices.size() / 2 + 1);
        for (int idx : indices) {
            if (((idx >> bit) & 1) == 0) {
                zeros.add(idx);
            } else {
                ones.add(idx);
            }
        }
        DecisionTree left = shannonExpand(table, zeros, k, bit + 1);
        DecisionTree right = shannonExpand(table, ones, k, bit + 1);
        return new DecisionTree.Split(bit, left, right);
    }

    /**
     * Compression ratio: fraction of the original table size compressed away
     * by the minimised representation.
     *
     * <p>Defined as {@code 1 - (minimisedLiteralCount / originalBitCount)},
     * clamped to {@code [0, 1]}. Returns {@code 0.0} for an empty original.
     *
     * @param original the original truth-table BitSet
     * @param minimized the minimised implicants
     * @return compression ratio in {@code [0, 1]}
     */
    public static double compressionRatio(BitSet original, List<int[]> minimized) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(minimized, "minimized");
        int originalBits = original.size();
        if (originalBits == 0) {
            return 0.0;
        }
        int literalCount = 0;
        for (int[] implicant : minimized) {
            for (int v : implicant) {
                if (v != DONT_CARE) {
                    literalCount++;
                }
            }
        }
        double ratio = 1.0 - ((double) literalCount / (double) originalBits);
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    /**
     * Verifies that the minimised implicants are logically equivalent to the
     * original truth table for all 2^k input combinations.
     */
    public static boolean isEquivalent(BitSet original, List<int[]> minimized, int k) {
        int size = 1 << k;
        for (int i = 0; i < size; i++) {
            boolean originalValue = original.get(i);
            boolean minimizedValue = evaluateImplicants(minimized, i, k);
            if (originalValue != minimizedValue) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateImplicants(List<int[]> implicants, int input, int k) {
        if (implicants.isEmpty()) {
            return false;                       // constant false
        }
        for (int[] implicant : implicants) {
            if (implicant.length == 0) {
                return true;                    // constant true
            }
            boolean match = true;
            for (int j = 0; j < k && j < implicant.length; j++) {
                int v = implicant[j];
                if (v == DONT_CARE) {
                    continue;
                }
                int bit = (input >> j) & 1;
                if (bit != v) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    /**
     * Immutable implicant representation used internally by the tabulation
     * method: a fixed {@code value} together with a {@code mask} whose set
     * bits identify the cared positions.
     */
    record Implicant(int value, int mask) {

        /**
         * Returns {@code true} if this implicant covers the given minterm.
         */
        boolean covers(int minterm) {
            return (minterm & mask) == (value & mask);
        }

        /**
         * Attempts to combine two implicants that share the same mask and
         * differ in exactly one cared bit. Returns the merged implicant or
         * {@code null} if they cannot be combined.
         */
        static Implicant tryCombine(Implicant a, Implicant b) {
            if (a.mask != b.mask) {
                return null;
            }
            int diff = a.value ^ b.value;
            if (Integer.bitCount(diff) != 1) {
                return null;
            }
            if ((diff & a.mask) == 0) {
                return null;
            }
            int newMask = a.mask & ~diff;
            int newValue = a.value & ~diff;
            return new Implicant(newValue, newMask);
        }

        /**
         * Encodes this implicant as an {@code int[k]} using the
         * {@code 0/1/2} convention defined by {@link BooleanMinimizer}.
         */
        int[] toIntArray(int k) {
            int[] arr = new int[k];
            for (int j = 0; j < k; j++) {
                if ((mask & (1 << j)) != 0) {
                    arr[j] = ((value & (1 << j)) != 0) ? 1 : 0;
                } else {
                    arr[j] = DONT_CARE;
                }
            }
            return arr;
        }
    }
}
