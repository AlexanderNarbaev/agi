package io.matrix.explainability;

import io.matrix.neuron.TruthTable;
import io.matrix.neuron.WeightVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Generates native explanations for MPDT neuron decisions.
 *
 * <p>Implements 16 explanation primitives organized into 5 categories
 * (ATTRIBUTION, PROTOTYPE, CONCEPT, COUNTERFACTUAL, MECHANISTIC).
 *
 * <p>Based on arXiv:2605.11595 — "Native Explainability for BCPNN".
 *
 * <p>Thread-safe: all state is confined to method parameters or immutable
 * structures. The prototype cache uses {@link ConcurrentHashMap}.
 */
public final class ExplanationGenerator {

    private final ConcurrentHashMap<Integer, Map<Integer, Integer>> prototypeCache =
            new ConcurrentHashMap<>();

    /**
     * Generates a full explanation for a neuron decision, applying all relevant
     * primitives based on the neuron's truth table.
     *
     * @param truthTable the neuron's truth table
     * @param input      the input that was evaluated (LSB-first)
     * @param output     the actual output
     * @return a complete provenance record with explanations
     */
    public DecisionProvenance explain(TruthTable truthTable, BitSet input, boolean output) {
        int k = truthTable.k();
        long[] inputLong = new long[]{input.toLongArray().length > 0 ? input.toLongArray()[0] : 0L};

        Map<String, String> results = new LinkedHashMap<>();
        List<ExplanationPrimitive> applied = new ArrayList<>();

        // Attribution
        Map<Integer, Double> attribution = computeAttribution(truthTable, input);
        results.put("P1_TruthTableAttribution", formatAttribution(attribution, k));
        applied.add(ExplanationPrimitive.P1);

        double weightSensitivity = computeWeightSensitivity(truthTable, input);
        results.put("P2_WeightSensitivity", String.format("%.4f", weightSensitivity));
        applied.add(ExplanationPrimitive.P2);

        Map<Integer, Boolean> ablation = computeInputAblation(truthTable, input, output);
        results.put("P3_InputAblation", formatAblation(ablation, k));
        applied.add(ExplanationPrimitive.P3);

        boolean[] gradient = computeBooleanGradient(truthTable, input, k);
        results.put("P4_GradientAttribution", formatGradient(gradient, k));
        applied.add(ExplanationPrimitive.P4);

        // Prototype
        Map<Integer, Integer> commonPatterns = extractCommonPatterns(truthTable, output, k);
        results.put("P5_CommonPatternExtraction", formatPatterns(commonPatterns, k));
        applied.add(ExplanationPrimitive.P5);

        int[] centroid = computeClusterCentroid(truthTable, output, k);
        results.put("P6_ClusterCentroid", formatBitVector(centroid, k));
        applied.add(ExplanationPrimitive.P6);

        int[] boundary = findDecisionBoundaryPrototype(truthTable, input, k);
        results.put("P7_DecisionBoundaryPrototype", formatBitVector(boundary, k));
        applied.add(ExplanationPrimitive.P7);

        // Concept
        List<List<Integer>> groups = computeInputGrouping(truthTable, k);
        results.put("P8_InputGrouping", formatGroups(groups));
        applied.add(ExplanationPrimitive.P8);

        Map<Integer, String> roles = classifyFunctionalRoles(truthTable, input, k);
        results.put("P10_FunctionalRoleAnalysis", formatRoles(roles));
        applied.add(ExplanationPrimitive.P10);

        // Counterfactual
        int[] minimalPerturbation = findMinimalPerturbation(truthTable, input, output, k);
        results.put("P11_MinimalPerturbation", formatBitVector(minimalPerturbation, k));
        applied.add(ExplanationPrimitive.P11);

        Map<String, Boolean> whatIf = computeWhatIf(truthTable, input, k);
        results.put("P12_WhatIfAnalysis", formatWhatIf(whatIf));
        applied.add(ExplanationPrimitive.P12);

        // Mechanistic
        String decisionPath = traceDecisionPath(truthTable, input, k);
        results.put("P14_DecisionPathTrace", decisionPath);
        applied.add(ExplanationPrimitive.P14);

        String minimized = minimizeTruthTable(truthTable, k);
        results.put("P15_TruthTableMinimization", minimized);
        applied.add(ExplanationPrimitive.P15);

        return new DecisionProvenance(
                null, null, null,
                inputLong, k, List.of(),
                truthTable.weights() != null ? truthTable.weights().toArray() : null,
                truthTable.hashCode(),
                List.of(), Map.of(),
                output, 1.0,
                applied, results
        );
    }

    // ─── Attribution methods ───

    /**
     * Computes per-input attribution scores by measuring how often flipping
     * a specific input bit changes the output across all input combinations.
     *
     * @return map of input index → attribution score [0.0 .. 1.0]
     */
    public Map<Integer, Double> computeAttribution(TruthTable truthTable, BitSet input) {
        int k = truthTable.k();
        int size = truthTable.size();
        boolean baseOutput = truthTable.evaluate(input);

        Map<Integer, Double> attribution = new LinkedHashMap<>();
        for (int bit = 0; bit < k; bit++) {
            int flips = 0;
            for (int i = 0; i < size; i++) {
                BitSet testInput = intToBitSet(i, k);
                boolean orig = truthTable.evaluate(testInput);
                BitSet flipped = (BitSet) testInput.clone();
                flipped.flip(bit);
                boolean flippedOutput = truthTable.evaluate(flipped);
                if (orig != flippedOutput) {
                    flips++;
                }
            }
            attribution.put(bit, (double) flips / size);
        }
        return attribution;
    }

    /**
     * Computes sensitivity of the decision to weight changes.
     * Returns 0.0 if no weights are set.
     */
    public double computeWeightSensitivity(TruthTable truthTable, BitSet input) {
        if (truthTable.weights() == null) {
            return 0.0;
        }
        WeightVector originalWeights = truthTable.weights();
        boolean baseOutput = truthTable.evaluate(input);
        int k = truthTable.k();
        int changes = 0;
        int total = 0;

        for (int bit = 0; bit < k; bit++) {
            int[] w = originalWeights.toArray();
            for (int newWeight = WeightVector.MIN_WEIGHT;
                    newWeight <= WeightVector.MAX_WEIGHT; newWeight++) {
                if (w[bit] == newWeight) continue;
                w[bit] = newWeight;
                TruthTable modified = TruthTable.of(k, truthTable.table(), new WeightVector(w));
                if (modified.evaluate(input) != baseOutput) {
                    changes++;
                }
                total++;
            }
        }
        return total > 0 ? (double) changes / total : 0.0;
    }

    /**
     * Computes per-input ablation: what happens when each input is forced to 0.
     *
     * @return map of input index → whether ablating that input changes the output
     */
    public Map<Integer, Boolean> computeInputAblation(TruthTable truthTable,
                                                       BitSet input, boolean output) {
        int k = truthTable.k();
        Map<Integer, Boolean> ablation = new LinkedHashMap<>();
        for (int bit = 0; bit < k; bit++) {
            BitSet ablated = (BitSet) input.clone();
            ablated.clear(bit);
            boolean ablatedOutput = truthTable.evaluate(ablated);
            ablation.put(bit, ablatedOutput != output);
        }
        return ablation;
    }

    /**
     * Computes the Boolean gradient: for each input bit, whether flipping it
     * changes the output for the given input.
     */
    public boolean[] computeBooleanGradient(TruthTable truthTable, BitSet input, int k) {
        boolean baseOutput = truthTable.evaluate(input);
        boolean[] gradient = new boolean[k];
        for (int bit = 0; bit < k; bit++) {
            BitSet flipped = (BitSet) input.clone();
            flipped.flip(bit);
            gradient[bit] = truthTable.evaluate(flipped) != baseOutput;
        }
        return gradient;
    }

    // ─── Prototype methods ───

    /**
     * Extracts the most common input patterns that produce the given output.
     * Returns the top-3 patterns with their frequency.
     */
    public Map<Integer, Integer> extractCommonPatterns(TruthTable truthTable,
                                                        boolean targetOutput, int k) {
        int cacheKey = truthTable.hashCode() * 31 + (targetOutput ? 1 : 0);
        return prototypeCache.computeIfAbsent(cacheKey, key -> {
            int size = truthTable.size();
            Map<Integer, Integer> patterns = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                BitSet input = intToBitSet(i, k);
                if (truthTable.evaluate(input) == targetOutput) {
                    patterns.merge(i, 1, Integer::sum);
                }
            }
            return patterns;
        });
    }

    /**
     * Computes the centroid (majority vote) of all inputs producing the target output.
     */
    public int[] computeClusterCentroid(TruthTable truthTable, boolean targetOutput, int k) {
        int[] centroid = new int[k];
        int count = 0;
        int size = truthTable.size();
        for (int i = 0; i < size; i++) {
            BitSet input = intToBitSet(i, k);
            if (truthTable.evaluate(input) == targetOutput) {
                for (int bit = 0; bit < k; bit++) {
                    if (input.get(bit)) centroid[bit]++;
                }
                count++;
            }
        }
        int[] result = new int[k];
        for (int bit = 0; bit < k; bit++) {
            result[bit] = centroid[bit] > count / 2 ? 1 : 0;
        }
        return result;
    }

    /**
     * Finds the input vector closest to the given input that produces the opposite output.
     * Uses Hamming distance.
     */
    public int[] findDecisionBoundaryPrototype(TruthTable truthTable, BitSet input, int k) {
        boolean baseOutput = truthTable.evaluate(input);
        int bestDist = k + 1;
        int bestIdx = -1;
        int size = truthTable.size();

        for (int i = 0; i < size; i++) {
            BitSet candidate = intToBitSet(i, k);
            if (truthTable.evaluate(candidate) != baseOutput) {
                int dist = hammingDistance(input, candidate, k);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
        }
        return bestIdx >= 0 ? intToBits(bestIdx, k) : new int[k];
    }

    // ─── Concept methods ───

    /**
     * Groups inputs by co-activation: inputs that always have the same value
     * across all true-output rows are grouped together.
     */
    public List<List<Integer>> computeInputGrouping(TruthTable truthTable, int k) {
        int size = truthTable.size();
        boolean[][] values = new boolean[size][k];

        for (int i = 0; i < size; i++) {
            BitSet input = intToBitSet(i, k);
            for (int bit = 0; bit < k; bit++) {
                values[i][bit] = input.get(bit);
            }
        }

        boolean[] used = new boolean[k];
        List<List<Integer>> groups = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            if (used[i]) continue;
            List<Integer> group = new ArrayList<>();
            group.add(i);
            used[i] = true;
            for (int j = i + 1; j < k; j++) {
                if (used[j]) continue;
                boolean coActivated = true;
                for (int row = 0; row < size; row++) {
                    if (values[row][i] != values[row][j]) {
                        coActivated = false;
                        break;
                    }
                }
                if (coActivated) {
                    group.add(j);
                    used[j] = true;
                }
            }
            groups.add(group);
        }
        return groups;
    }

    /**
     * Classifies each input bit by its functional role based on how flipping
     * it affects the output across all inputs.
     *
     * @return map of input index → role (EXCITATORY, INHIBITORY, MODULATORY, INERT)
     */
    public Map<Integer, String> classifyFunctionalRoles(TruthTable truthTable,
                                                         BitSet sampleInput, int k) {
        int size = truthTable.size();
        Map<Integer, String> roles = new LinkedHashMap<>();

        for (int bit = 0; bit < k; bit++) {
            int flipToTrue = 0;
            int flipToFalse = 0;
            int total = 0;

            for (int i = 0; i < size; i++) {
                BitSet input = intToBitSet(i, k);
                boolean orig = truthTable.evaluate(input);
                BitSet flipped = (BitSet) input.clone();
                flipped.flip(bit);
                boolean flippedOutput = truthTable.evaluate(flipped);

                if (orig != flippedOutput) {
                    total++;
                    if (flippedOutput) flipToTrue++;
                    else flipToFalse++;
                }
            }

            if (total == 0) {
                roles.put(bit, "INERT");
            } else if (flipToTrue > flipToFalse * 2) {
                roles.put(bit, "EXCITATORY");
            } else if (flipToFalse > flipToTrue * 2) {
                roles.put(bit, "INHIBITORY");
            } else {
                roles.put(bit, "MODULATORY");
            }
        }
        return roles;
    }

    // ─── Counterfactual methods ───

    /**
     * Finds the minimal set of input bit flips that change the output.
     * Returns the modified input vector (Hamming-1 perturbation).
     */
    public int[] findMinimalPerturbation(TruthTable truthTable, BitSet input,
                                          boolean output, int k) {
        boolean baseOutput = truthTable.evaluate(input);
        if (baseOutput != output) {
            return bitSetToBits(input, k);
        }

        // Try single-bit flips first
        for (int bit = 0; bit < k; bit++) {
            BitSet flipped = (BitSet) input.clone();
            flipped.flip(bit);
            if (truthTable.evaluate(flipped) != baseOutput) {
                return bitSetToBits(flipped, k);
            }
        }

        // Try two-bit flips
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                BitSet flipped = (BitSet) input.clone();
                flipped.flip(i);
                flipped.flip(j);
                if (truthTable.evaluate(flipped) != baseOutput) {
                    return bitSetToBits(flipped, k);
                }
            }
        }
        return bitSetToBits(input, k);
    }

    /**
     * Computes what-if analysis: evaluates the output for each single-bit flip.
     *
     * @return map of "bit_N_flipped" → resulting output
     */
    public Map<String, Boolean> computeWhatIf(TruthTable truthTable, BitSet input, int k) {
        Map<String, Boolean> whatIf = new LinkedHashMap<>();
        boolean baseOutput = truthTable.evaluate(input);
        whatIf.put("original", baseOutput);

        for (int bit = 0; bit < k; bit++) {
            BitSet flipped = (BitSet) input.clone();
            flipped.flip(bit);
            whatIf.put("bit_" + bit + "_flipped", truthTable.evaluate(flipped));
        }
        return whatIf;
    }

    /**
     * Shows how different weight configurations would change the decision.
     * Only applicable if the truth table has weights.
     */
    public Map<String, Boolean> computeWeightCounterfactual(TruthTable truthTable,
                                                             BitSet input, int k) {
        if (truthTable.weights() == null) {
            return Map.of("no_weights", truthTable.evaluate(input));
        }
        Map<String, Boolean> results = new LinkedHashMap<>();
        results.put("original", truthTable.evaluate(input));

        int[] origWeights = truthTable.weights().toArray();
        for (int bit = 0; bit < k; bit++) {
            for (int w = WeightVector.MIN_WEIGHT; w <= WeightVector.MAX_WEIGHT; w++) {
                if (origWeights[bit] == w) continue;
                int[] newWeights = origWeights.clone();
                newWeights[bit] = w;
                TruthTable modified = TruthTable.of(k, truthTable.table(),
                        new WeightVector(newWeights));
                results.put("bit_" + bit + "_weight_" + w, modified.evaluate(input));
            }
        }
        return results;
    }

    // ─── Mechanistic methods ───

    /**
     * Traces the decision path: which input bits were tested and the result.
     * For a truth table, this shows the index computation.
     */
    public String traceDecisionPath(TruthTable truthTable, BitSet input, int k) {
        int index = 0;
        StringBuilder path = new StringBuilder("DecisionPath[");
        for (int i = 0; i < k; i++) {
            boolean bit = input.get(i);
            if (bit) index |= (1 << i);
            if (i > 0) path.append(", ");
            path.append("b").append(i).append("=").append(bit ? 1 : 0);
        }
        boolean output = truthTable.evaluate(input);
        path.append("] → index=").append(index)
            .append(" → output=").append(output);
        return path.toString();
    }

    /**
     * Produces a minimized representation of the truth table.
     * Returns a string showing the Boolean function in sum-of-products form.
     */
    public String minimizeTruthTable(TruthTable truthTable, int k) {
        int size = truthTable.size();
        List<String> minterms = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            BitSet input = intToBitSet(i, k);
            if (truthTable.evaluate(input)) {
                minterms.add(formatMinterm(i, k));
            }
        }
        if (minterms.isEmpty()) return "0";
        if (minterms.size() == size) return "1";
        return String.join(" + ", minterms);
    }

    // ─── Formatting helpers ───

    private String formatAttribution(Map<Integer, Double> attr, int k) {
        StringBuilder sb = new StringBuilder();
        attr.forEach((bit, score) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append("b").append(bit).append("=").append(String.format("%.3f", score));
        });
        return sb.toString();
    }

    private String formatAblation(Map<Integer, Boolean> ablation, int k) {
        StringBuilder sb = new StringBuilder();
        ablation.forEach((bit, changed) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append("b").append(bit).append("=").append(changed ? "CHANGED" : "unchanged");
        });
        return sb.toString();
    }

    private String formatGradient(boolean[] gradient, int k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < k; i++) {
            if (i > 0) sb.append(", ");
            sb.append("b").append(i).append("=").append(gradient[i] ? 1 : 0);
        }
        return sb.toString();
    }

    private String formatPatterns(Map<Integer, Integer> patterns, int k) {
        return patterns.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> String.format("input_%d(freq=%d)", e.getKey(), e.getValue()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private String formatBitVector(int[] bits, int k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < k; i++) {
            if (i > 0) sb.append(", ");
            sb.append("b").append(i).append("=").append(bits[i]);
        }
        return sb.toString();
    }

    private String formatGroups(List<List<Integer>> groups) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append("group_").append(i).append("=[");
            List<Integer> group = groups.get(i);
            for (int j = 0; j < group.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append("b").append(group.get(j));
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private String formatRoles(Map<Integer, String> roles) {
        StringBuilder sb = new StringBuilder();
        roles.forEach((bit, role) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append("b").append(bit).append("=").append(role);
        });
        return sb.toString();
    }

    private String formatWhatIf(Map<String, Boolean> whatIf) {
        StringBuilder sb = new StringBuilder();
        whatIf.forEach((key, val) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(key).append("=").append(val);
        });
        return sb.toString();
    }

    private String formatMinterm(int index, int k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < k; i++) {
            if (i > 0) sb.append("·");
            sb.append((index & (1 << i)) != 0 ? "b" + i : "¬b" + i);
        }
        return sb.toString();
    }

    // ─── Bit manipulation helpers ───

    static BitSet intToBitSet(int value, int k) {
        BitSet bs = new BitSet(k);
        for (int i = 0; i < k; i++) {
            if ((value & (1 << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }

    static int[] intToBits(int value, int k) {
        int[] bits = new int[k];
        for (int i = 0; i < k; i++) {
            bits[i] = (value & (1 << i)) != 0 ? 1 : 0;
        }
        return bits;
    }

    static int[] bitSetToBits(BitSet bs, int k) {
        int[] bits = new int[k];
        for (int i = 0; i < k; i++) {
            bits[i] = bs.get(i) ? 1 : 0;
        }
        return bits;
    }

    static int hammingDistance(BitSet a, BitSet b, int k) {
        int dist = 0;
        for (int i = 0; i < k; i++) {
            if (a.get(i) != b.get(i)) dist++;
        }
        return dist;
    }
}
