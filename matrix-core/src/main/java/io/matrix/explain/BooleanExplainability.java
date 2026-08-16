package io.matrix.explain;

import io.matrix.neuron.DecisionTree;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * SHAP-style feature importance for Boolean neuron decisions.
 *
 * <p>Computes how much each input bit contributes to the output. Uses the
 * Boolean equivalent of SHAP: for each input bit i, computes the difference
 * in conditional expectations:
 * <pre>  E[output | bit_i = value_i] − E[output | bit_i = ¬value_i]</pre>
 *
 * <p>Designed after the research pattern from article #2 (BPMN: "explain the
 * reasoning, not just the decision"). Each feature importance carries both a
 * numeric SHAP value and a human-readable explanation.
 */
public final class BooleanExplainability {

    private BooleanExplainability() {
    }

    /**
     * Describes how important a single input bit was for a specific decision.
     *
     * @param bitIndex    position of the input bit (0-based, LSB)
     * @param inputValue  actual value of this bit in the current input
     * @param shapValue   SHAP-style contribution (positive → pushes output toward 1,
     *                    negative → pushes output toward 0)
     * @param explanation human-readable description of the effect
     */
    public record FeatureImportance(int bitIndex, boolean inputValue, double shapValue,
                                      String explanation) {
    }

    /**
     * Computes SHAP-style feature importance for a single decision.
     *
     * <p>Enumerates all possible input combinations and computes the marginal
     * effect of each input bit on the output. Bit contributions are averaged
     * over all possible assignments of the other bits (matching the SHAP
     * definition).
     *
     * @param tree  the Boolean function as a decision tree
     * @param input the concrete input vector that was evaluated
     * @return feature importances sorted by absolute SHAP value (most important first)
     */
    public static List<FeatureImportance> explain(DecisionTree tree, BitSet input) {
        int k = tree.inputCount();
        if (k == 0) {
            return List.of();
        }
        int size = 1 << k;
        boolean baseOutput = tree.evaluate(input);

        List<FeatureImportance> result = new ArrayList<>(k);
        for (int bit = 0; bit < k; bit++) {
            boolean actualValue = input.get(bit);

            double expectedWithActual = conditionalExpectation(tree, k, bit, actualValue, size);
            double expectedWithOther = conditionalExpectation(tree, k, bit, !actualValue, size);
            double shapValue = expectedWithActual - expectedWithOther;

            String explanation = formatExplanation(bit, actualValue, baseOutput, shapValue);
            result.add(new FeatureImportance(bit, actualValue, shapValue, explanation));
        }

        result.sort((a, b) -> Double.compare(Math.abs(b.shapValue()), Math.abs(a.shapValue())));
        return result;
    }

    /**
     * Explains a chain of decisions (BRC chain — layer-by-layer).
     *
     * <p>Each layer receives the output of the previous layer as input.
     * Returns a list where element {@code i} is the feature-importance
     * breakdown for layer {@code i}.
     *
     * @param trees  decision trees, one per layer
     * @param inputs input vectors, one per layer (must have same size as trees)
     * @return per-layer feature importance lists
     * @throws IllegalArgumentException if {@code trees} and {@code inputs} have different sizes
     */
    public static List<List<FeatureImportance>> explainChain(List<DecisionTree> trees,
                                                               List<BitSet> inputs) {
        if (trees.size() != inputs.size()) {
            throw new IllegalArgumentException(
                    "trees and inputs must have the same size, got "
                            + trees.size() + " and " + inputs.size());
        }
        List<List<FeatureImportance>> chain = new ArrayList<>(trees.size());
        for (int i = 0; i < trees.size(); i++) {
            chain.add(explain(trees.get(i), inputs.get(i)));
        }
        return chain;
    }

    /**
     * Returns the top-{@code k} most important features by absolute SHAP value.
     *
     * <p>If there are fewer than {@code k} features, returns all of them.
     *
     * @param importances full list (presorted by importance)
     * @param k           number of top features to keep
     * @return at most {@code k} features, in descending importance order
     */
    public static List<FeatureImportance> topFeatures(List<FeatureImportance> importances, int k) {
        if (k <= 0 || importances.isEmpty()) {
            return List.of();
        }
        return importances.size() <= k
                ? List.copyOf(importances)
                : List.copyOf(importances.subList(0, k));
    }

    /**
     * Produces a human-readable multi-line explanation string.
     *
     * <p>Supported format values:
     * <ul>
     *   <li>{@code "default"} — full multi-line with per-bit details</li>
     *   <li>{@code "brief"}  — one-line summary with top feature count</li>
     *   <li>{@code "single"} — single sentence describing the most important bit</li>
     * </ul>
     *
     * @param importances feature importances (presorted)
     * @param format      output style: {@code "default"}, {@code "brief"}, or {@code "single"}
     * @return formatted explanation string
     */
    public static String toExplanation(List<FeatureImportance> importances, String format) {
        if (importances == null || importances.isEmpty()) {
            return "No input features to explain (constant function or zero inputs).";
        }
        if ("brief".equals(format)) {
            long significant = importances.stream()
                    .filter(fi -> Math.abs(fi.shapValue()) > 0.01)
                    .count();
            String top = importances.get(0).bitIndex() + " (value="
                    + String.format("%+.3f", importances.get(0).shapValue()) + ")";
            return "Top-1 bit: " + top + "; " + significant + " significant features total.";
        }
        if ("single".equals(format)) {
            FeatureImportance top = importances.get(0);
            return top.explanation();
        }
        // default format
        StringBuilder sb = new StringBuilder("Boolean feature importance (SHAP-style):\n");
        for (FeatureImportance fi : importances) {
            sb.append("  ").append(fi.explanation()).append('\n');
        }
        return sb.toString();
    }

    // ─── internal helpers ───

    /**
     * Computes E[output | bit_i = fixedValue], averaged over all 2^{k-1}
     * assignments of the other bits.
     */
    private static double conditionalExpectation(DecisionTree tree, int k, int bit,
                                                  boolean fixedValue, int size) {
        int count = 0;
        int total = 0;
        for (int i = 0; i < size; i++) {
            boolean bitValue = ((i >> bit) & 1) == 1;
            if (bitValue == fixedValue) {
                total++;
                if (tree.evaluate(intToBitSet(i, k))) {
                    count++;
                }
            }
        }
        return total > 0 ? (double) count / total : 0.0;
    }

    /**
     * Converts an integer-encoded input to a {@link BitSet} of length {@code k}.
     */
    static BitSet intToBitSet(int value, int k) {
        BitSet bs = new BitSet(k);
        for (int i = 0; i < k; i++) {
            if ((value & (1 << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }

    /**
     * Builds a human-readable explanation for a single feature's contribution.
     */
    static String formatExplanation(int bitIndex, boolean actualValue,
                                     boolean baseOutput, double shapValue) {
        String direction = shapValue > 0 ? "pushes toward 1" : "pushes toward 0";
        String magnitude = Math.abs(shapValue) > 0.3 ? "high importance"
                : Math.abs(shapValue) > 0.05 ? "moderate importance"
                : "low importance";
        return "Bit " + bitIndex + " (" + (actualValue ? '1' : '0')
                + ") — SHAP " + String.format("%+.4f", shapValue)
                + " — " + direction + ", " + magnitude;
    }
}
