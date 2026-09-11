package io.matrix.neuron;

import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DESIGN-22 §3 — Merge two EnrichedNeurons into one if they are
 * near-duplicates (Hamming distance, magnitude distance, chemical
 * distance all below thresholds). Pure function (CONSTITUTION I).
 *
 * <p>If merge succeeds, returns the merged neuron with averaged
 * magnitude/chemicalVector. Otherwise returns Optional.empty().
 */
public final class NeuronMerger {

    /** Default Hamming distance threshold (5%). */
    public static final double DEFAULT_EPSILON = 0.05;
    /** Default magnitude distance threshold. */
    public static final double DEFAULT_DELTA_MAGNITUDE = 0.10;
    /** Default chemical Euclidean distance threshold. */
    public static final double DEFAULT_DELTA_CHEMICAL = 0.15;

    private NeuronMerger() {}

    /**
     * Try to merge two neurons. Returns the merged neuron if all three
     * distance metrics are below thresholds; otherwise empty.
     *
     * <p>The merged neuron has:
     *  - averaged magnitude
     *  - averaged chemicalVector (per-dimension)
     *  - tag derived from averaged chemicalVector
     *  - XOR of tables (best-effort; for very similar tables the
     *    density stays close to one of them — but the XOR may not
     *    actually represent either). Caller should verify fidelity.
     */
    public static Optional<EnrichedNeuron> tryMerge(
            EnrichedNeuron a, EnrichedNeuron b,
            double epsilon, double deltaMagnitude, double deltaChemical) {

        if (a == null || b == null) return Optional.empty();
        if (a.table().k() != b.table().k()) return Optional.empty();

        double hamming = EnrichedNeuron.hammingDistance(a, b);
        if (hamming > epsilon) return Optional.empty();

        double magDiff = Math.abs(a.magnitude() - b.magnitude());
        if (magDiff > deltaMagnitude) return Optional.empty();

        double chemDist = EnrichedNeuron.chemicalDistance(a, b);
        if (chemDist > deltaChemical) return Optional.empty();

        // Merge: average magnitudes
        double mergedMag = (a.magnitude() + b.magnitude()) / 2.0;

        // Average chemical vectors
        double[] mergedChem = new double[EnrichedNeuron.CHEMICAL_DIM];
        for (int i = 0; i < EnrichedNeuron.CHEMICAL_DIM; i++) {
            mergedChem[i] = (a.chemicalVector()[i] + b.chemicalVector()[i]) / 2.0;
        }

        // Merge table: take the one with higher magnitude as base,
        // XOR in the other's bits. For very similar tables the XOR
        // adds very few bits.
        TruthTable baseTable = a.magnitude() >= b.magnitude() ? a.table() : b.table();
        TruthTable otherTable = a.magnitude() >= b.magnitude() ? b.table() : a.table();
        // If both refer to the same underlying table, no XOR needed
        TruthTable mergedTable;
        if (baseTable == otherTable) {
            mergedTable = baseTable;
        } else {
            BitSet xor = (BitSet) baseTable.table().clone();
            xor.xor(otherTable.table());
            mergedTable = TruthTable.of(baseTable.k(), xor);
        }

        Neurotransmitter mergedTag = EnrichedNeuron.classify(mergedChem);

        return Optional.of(new EnrichedNeuron(
                mergedTable, mergedMag, mergedChem, mergedTag));
    }

    /** Convenience overload with default thresholds. */
    public static Optional<EnrichedNeuron> tryMerge(EnrichedNeuron a, EnrichedNeuron b) {
        return tryMerge(a, b, DEFAULT_EPSILON, DEFAULT_DELTA_MAGNITUDE,
                DEFAULT_DELTA_CHEMICAL);
    }

    /**
     * Bulk merge: given a list of neurons, repeatedly merge the closest
     * pair until no pair is mergeable. Returns the merged list.
     *
     * <p>Deterministic — same input → same output (CONSTITUTION I).
     */
    public static List<EnrichedNeuron> mergeAll(List<EnrichedNeuron> neurons) {
        return mergeAll(neurons, DEFAULT_EPSILON, DEFAULT_DELTA_MAGNITUDE,
                DEFAULT_DELTA_CHEMICAL);
    }

    public static List<EnrichedNeuron> mergeAll(
            List<EnrichedNeuron> neurons,
            double epsilon, double deltaMagnitude, double deltaChemical) {
        if (neurons == null || neurons.isEmpty()) return new ArrayList<>();
        List<EnrichedNeuron> working = new ArrayList<>(neurons);
        boolean merged;
        do {
            merged = false;
            int bestI = -1, bestJ = -1;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int i = 0; i < working.size(); i++) {
                for (int j = i + 1; j < working.size(); j++) {
                    EnrichedNeuron ni = working.get(i);
                    EnrichedNeuron nj = working.get(j);
                    if (ni.table().k() != nj.table().k()) continue;
                    double hamming = EnrichedNeuron.hammingDistance(ni, nj);
                    double mag = Math.abs(ni.magnitude() - nj.magnitude());
                    double chem = EnrichedNeuron.chemicalDistance(ni, nj);
                    if (hamming <= epsilon && mag <= deltaMagnitude
                            && chem <= deltaChemical) {
                        double totalDist = hamming + mag + chem;
                        if (totalDist < bestDist) {
                            bestDist = totalDist;
                            bestI = i;
                            bestJ = j;
                        }
                    }
                }
            }
            if (bestI >= 0 && bestJ >= 0) {
                EnrichedNeuron mergedNeuron = tryMerge(
                        working.get(bestI), working.get(bestJ),
                        epsilon, deltaMagnitude, deltaChemical).orElse(null);
                if (mergedNeuron != null) {
                    working.remove(Math.max(bestI, bestJ));
                    working.remove(Math.min(bestI, bestJ));
                    working.add(mergedNeuron);
                    merged = true;
                }
            }
        } while (merged);
        return working;
    }

    /** Helper: convert FnlEntry to EnrichedNeuron (for merging). */
    public static Optional<EnrichedNeuron> tryMergeFromEntries(
            UUID idA, io.matrix.noosphere.FnlEntry entryA,
            UUID idB, io.matrix.noosphere.FnlEntry entryB) {
        EnrichedNeuron a = new EnrichedNeuron(entryA.table(),
                entryA.magnitude(), entryA.chemicalVector(), entryA.tag());
        EnrichedNeuron b = new EnrichedNeuron(entryB.table(),
                entryB.magnitude(), entryB.chemicalVector(), entryB.tag());
        return tryMerge(a, b);
    }
}
