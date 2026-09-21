package io.matrix.neuron;

import io.matrix.noosphere.FnlEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DESIGN-24 — Biology-inspired synaptic pruning.
 * Removes weak neurons (magnitude < threshold), strengthens top
 * 10%. Pure function (CONSTITUTION I).
 */
public final class SynapticPruner {

    public static final double DEFAULT_PRUNE_THRESHOLD = 0.2;
    public static final double DEFAULT_STRENGTHEN_FACTOR = 1.1;
    public static final double DEFAULT_TOP_STRENGTHEN_PERCENTILE = 0.10;

    private SynapticPruner() {}

    /** Prune + strengthen. Returns a NEW list (original unchanged). */
    public static List<EnrichedNeuron> prune(
            List<EnrichedNeuron> neurons,
            double pruneThreshold,
            double strengthenFactor,
            double topPercentile) {
        if (neurons == null) throw new IllegalArgumentException("null");
        if (pruneThreshold < 0 || pruneThreshold > 1) {
            throw new IllegalArgumentException("threshold must be in [0,1]");
        }
        // Sort by magnitude descending to find top 10%
        List<EnrichedNeuron> sorted = new ArrayList<>(neurons);
        sorted.sort(Comparator.comparingDouble(EnrichedNeuron::magnitude).reversed());
        int topN = Math.max(1, (int) (sorted.size() * topPercentile));

        List<EnrichedNeuron> result = new ArrayList<>();
        for (int i = 0; i < neurons.size(); i++) {
            EnrichedNeuron n = neurons.get(i);
            if (n.magnitude() < pruneThreshold) continue;  // prune
            if (i < topN) {
                // strengthen: scale magnitude, clamp
                double newMag = Math.min(1.0, n.magnitude() * strengthenFactor);
                // also boost certainty dimension
                double[] chem = n.chemicalVector().clone();
                if (chem.length > EnrichedNeuron.CERTAINTY) {
                    chem[EnrichedNeuron.CERTAINTY] = Math.min(1.0,
                            chem[EnrichedNeuron.CERTAINTY] * strengthenFactor);
                }
                Neurotransmitter tag = EnrichedNeuron.classify(chem);
                result.add(new EnrichedNeuron(n.table(), newMag, chem, tag));
            } else {
                result.add(n);  // unchanged
            }
        }
        return result;
    }

    public static List<EnrichedNeuron> prune(List<EnrichedNeuron> neurons) {
        return prune(neurons, DEFAULT_PRUNE_THRESHOLD,
                DEFAULT_STRENGTHEN_FACTOR, DEFAULT_TOP_STRENGTHEN_PERCENTILE);
    }

    /** Apply pruning to FnlEntry list (preserves provenance/parents). */
    public static List<FnlEntry> pruneEntries(
            List<FnlEntry> entries,
            double pruneThreshold,
            double strengthenFactor,
            double topPercentile) {
        List<EnrichedNeuron> neurons = new ArrayList<>();
        for (FnlEntry e : entries) {
            neurons.add(new EnrichedNeuron(e.table(), e.magnitude(),
                    e.chemicalVector(), e.tag()));
        }
        List<EnrichedNeuron> kept = prune(neurons, pruneThreshold,
                strengthenFactor, topPercentile);
        // Map back — match by index (preserves order for kept entries)
        List<FnlEntry> result = new ArrayList<>();
        int keptIdx = 0;
        for (FnlEntry e : entries) {
            if (e.magnitude() < pruneThreshold) continue;
            if (result.size() < kept.size()) {
                EnrichedNeuron k = kept.get(keptIdx++);
                result.add(new FnlEntry(e.id(), e.table(), k.magnitude(),
                        k.chemicalVector(), k.tag(), e.provenance(),
                        e.parents(), e.createdTimestamp(), e.generation()));
            }
        }
        return result;
    }

    /** Ratio of remaining neurons (0.0 = all pruned, 1.0 = none). */
    public static double survivalRatio(int before, int after) {
        if (before == 0) return 1.0;
        return (double) after / before;
    }
}
