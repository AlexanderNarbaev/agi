package io.matrix.explainability;

import java.util.Arrays;
import java.util.List;

/**
 * 16 explanation primitives for native neuron explainability.
 *
 * <p>Based on arXiv:2605.11595 — "Native Explainability for BCPNN".
 * Each primitive represents a distinct explanation technique applicable
 * to MPDT neuron decisions.
 *
 * <p>Primitives are grouped into 5 categories:
 * <ul>
 *   <li>{@link Category#ATTRIBUTION} — which inputs contributed most</li>
 *   <li>{@link Category#PROTOTYPE} — common input patterns</li>
 *   <li>{@link Category#CONCEPT} — semantic groupings of inputs</li>
 *   <li>{@link Category#COUNTERFACTUAL} — what-if scenarios</li>
 *   <li>{@link Category#MECHANISTIC} — internal mechanism analysis</li>
 * </ul>
 */
public enum ExplanationPrimitive {

    // ─── Attribution (P1–P4) ───

    P1("TruthTableAttribution",
            "Identifies which input bits flip the output by scanning the truth table",
            Category.ATTRIBUTION),

    P2("WeightSensitivity",
            "Measures how changing priority weights affects the decision",
            Category.ATTRIBUTION),

    P3("InputAblation",
            "Systematically removes inputs to measure their individual contribution",
            Category.ATTRIBUTION),

    P4("GradientAttribution",
            "Computes the Boolean gradient (partial derivatives) of the output w.r.t. each input",
            Category.ATTRIBUTION),

    // ─── Prototype (P5–P7) ───

    P5("CommonPatternExtraction",
            "Extracts the most frequent input patterns that produce a given output",
            Category.PROTOTYPE),

    P6("ClusterCentroid",
            "Identifies representative input vectors from decision clusters",
            Category.PROTOTYPE),

    P7("DecisionBoundaryPrototype",
            "Finds input vectors closest to the decision boundary (minimal change flips output)",
            Category.PROTOTYPE),

    // ─── Concept (P8–P10) ───

    P8("InputGrouping",
            "Groups inputs by their co-activation patterns across decisions",
            Category.CONCEPT),

    P9("SemanticMapping",
            "Maps input bit positions to semantic labels (e.g., sensor, memory, goal)",
            Category.CONCEPT),

    P10("FunctionalRoleAnalysis",
            "Classifies inputs by their functional role: excitatory, inhibitory, modulatory",
            Category.CONCEPT),

    // ─── Counterfactual (P11–P13) ───

    P11("MinimalPerturbation",
            "Finds the smallest input change that flips the output",
            Category.COUNTERFACTUAL),

    P12("WhatIfAnalysis",
            "Evaluates the output for hypothetical input modifications",
            Category.COUNTERFACTUAL),

    P13("WeightCounterfactual",
            "Shows how different priority weights would change the decision",
            Category.COUNTERFACTUAL),

    // ─── Mechanistic (P14–P16) ───

    P14("DecisionPathTrace",
            "Traces the exact path through the decision tree for a given input",
            Category.MECHANISTIC),

    P15("TruthTableMinimization",
            "Produces a minimized Boolean expression explaining the neuron's logic",
            Category.MECHANISTIC),

    P16("ActivationLandscape",
            "Maps the full activation landscape across all input combinations",
            Category.MECHANISTIC);

    private final String name;
    private final String description;
    private final Category category;

    ExplanationPrimitive(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    /**
     * Human-readable name of this primitive.
     */
    public String primitiveName() {
        return name;
    }

    /**
     * Detailed description of what this primitive explains.
     */
    public String description() {
        return description;
    }

    /**
     * Category this primitive belongs to.
     */
    public Category category() {
        return category;
    }

    /**
     * Returns all primitives belonging to the given category.
     */
    public static List<ExplanationPrimitive> byCategory(Category category) {
        return Arrays.stream(values())
                .filter(p -> p.category == category)
                .toList();
    }

    /**
     * Explanation category grouping related primitives.
     */
    public enum Category {
        /**
         * Identifies which inputs contributed most to the decision.
         */
        ATTRIBUTION,

        /**
         * Identifies common input patterns (prototypes) for decisions.
         */
        PROTOTYPE,

        /**
         * Extracts semantic groupings and concepts from inputs.
         */
        CONCEPT,

        /**
         * Generates what-if scenarios and alternative outcomes.
         */
        COUNTERFACTUAL,

        /**
         * Analyzes the internal mechanism of the decision.
         */
        MECHANISTIC
    }
}
