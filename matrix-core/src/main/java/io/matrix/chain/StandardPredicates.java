package io.matrix.chain;

import io.matrix.imports.ChainEnrichedOutput;
import io.matrix.neuron.EnrichedNeuron;

import java.util.List;

/**
 * DESIGN-21 — Starter predicates for the three most common triggers.
 * Each is a pure function of {@link ChainEnrichedOutput}.
 *
 * <p>Use these as building blocks for richer predicates.
 */
public final class StandardPredicates {

    private StandardPredicates() {}

    /**
     * Curiosity: high average novelty AND low average certainty
     * → suggests uncertain novelty that needs exploration.
     */
    public static TriggerPredicate noveltyCuriosity(double noveltyThreshold,
                                                    double certaintyMaxThreshold) {
        return output -> output.meanNovelty() > noveltyThreshold
                && output.meanCertainty() < certaintyMaxThreshold;
    }

    /**
     * Consolidation: when mean certainty is very high, the chain's
     * firing pattern is stable — ripe for TR-phase consolidation.
     */
    public static TriggerPredicate consolidation(double certaintyThreshold) {
        return output -> output.meanCertainty() > certaintyThreshold;
    }

    /**
     * High-arousal: when mean excitation crosses a threshold, the
     * system is "alert" — may trigger priority attention routing.
     */
    public static TriggerPredicate highArousal(double excitationThreshold) {
        return output -> output.meanExcitation() > excitationThreshold;
    }

    /**
     * Low-magnitude collapse: if the average magnitude is very low
     * AND certainty is low, the chain is producing "vague" outputs
     * — possible derangement, trigger recovery.
     */
    public static TriggerPredicate lowMagnitudeCollapse(double magnitudeMax,
                                                       double certaintyMax) {
        return output -> output.meanMagnitude() < magnitudeMax
                && output.meanCertainty() < certaintyMax;
    }

    /**
     * FROZEN-shutoff predicate: returns true when the upstream
     * FROZEN-gate fires. Placeholder; actual FROZEN check is wired
     * in BrainLoopService.
     */
    public static TriggerPredicate frozenShutoff() {
        return output -> false;  // wired externally via EthicalFilter
    }

    /** Helper: mean of a specific chemical dimension. */
    public static double meanChem(ChainEnrichedOutput output, int dim) {
        return output.meanChemical(dim);
    }
}
