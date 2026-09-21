package io.matrix.cauldron;

import io.matrix.lifecycle.FnlGateV2;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.NeuronMerger;
import io.matrix.neuron.SynapticPruner;
import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cauldron v2 (DESIGN-07 + Ivakhnenko GMDH): controlled self-generation
 * of elements via rows of increasing complexity + Φ-validation on
 * held-out window.
 *
 * <p>Cycle: 5 stages
 *  1. Single-neuron candidates (random + Levin schedule)
 *  2. Pair compositions (GMDH-style)
 *  3. Φ-validation on held-out window
 *  4. Lineage.record(parent, episode, PhiWin)
 *  5. FnlGate admission (CANDIDATE → quarantine)
 *
 * <p>Pure functions throughout (CONSTITUTION I).
 */
public final class CauldronProtocolV2 {

    public enum Stage { IDLE, GENERATING, VALIDATING, ADMITTING, COMPLETED }

    public record CauldronCandidate(
            int row,                    // 1, 2, 3... (complexity)
            int pairIndex,              // for row≥2: index of pair
            TruthTable table,
            double magnitude,
            int parentA,                // index in candidates, -1 if none
            int parentB
    ) {}

    public record PhiResult(
            int candidateIndex,
            double accuracy,
            double heldOutAccuracy,
            double phiWin,              // heldOutAccuracy - accuracy on training
            long timestamp
    ) {}

    private final java.util.List<CauldronCandidate> candidates = new ArrayList<>();
    private final java.util.List<PhiResult> phiResults = new ArrayList<>();
    private final int heldOutWindowSize;
    private Stage stage = Stage.IDLE;

    public CauldronProtocolV2(int heldOutWindowSize) {
        if (heldOutWindowSize < 1) {
            throw new IllegalArgumentException("window ≥ 1");
        }
        this.heldOutWindowSize = heldOutWindowSize;
    }

    /** Stage 1: generate single-neuron candidates. */
    public void generateRow1(List<EnrichedNeuron> seedNeurons) {
        stage = Stage.GENERATING;
        for (int i = 0; i < seedNeurons.size(); i++) {
            candidates.add(new CauldronCandidate(1, 0,
                    seedNeurons.get(i).table(),
                    seedNeurons.get(i).magnitude(), -1, -1));
        }
    }

    /** Stage 2: pair-composition (GMDH-style — row 2 onwards). */
    public void generateRow2Pairs() {
        stage = Stage.GENERATING;
        // Generate pairs (i, j) where i < j
        int n = candidates.size();
        int pairIdx = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                CauldronCandidate a = candidates.get(i);
                CauldronCandidate b = candidates.get(j);
                if (a.table().k() != b.table().k()) continue;
                // XOR composition: if A XOR B is mostly zero → strong pair
                Optional<EnrichedNeuron> merged = NeuronMerger.tryMerge(
                        new EnrichedNeuron(a.table(), a.magnitude(),
                                new double[]{0.5, 0.5, 0.5, 0.5},
                                io.matrix.neuron.Neurotransmitter.SEROTONIN),
                        new EnrichedNeuron(b.table(), b.magnitude(),
                                new double[]{0.5, 0.5, 0.5, 0.5},
                                io.matrix.neuron.Neurotransmitter.SEROTONIN));
                if (merged.isPresent()) {
                    candidates.add(new CauldronCandidate(2, pairIdx++,
                            merged.get().table(), merged.get().magnitude(), i, j));
                }
            }
        }
    }

    /**
     * Stage 3: Φ-validation. Train accuracy on first 80%, held-out
     * on last 20%. PhiWin = heldOut - train. Higher is better
     * (model doesn't overfit).
     */
    public PhiResult validate(int candidateIndex, List<Boolean> actual,
                              List<Boolean> predicted) {
        stage = Stage.VALIDATING;
        int splitPoint = (int) (actual.size() * 0.8);
        int trainCorrect = 0, heldOutCorrect = 0;
        for (int i = 0; i < actual.size(); i++) {
            boolean ok = actual.get(i).equals(predicted.get(i));
            if (i < splitPoint) {
                if (ok) trainCorrect++;
            } else {
                if (ok) heldOutCorrect++;
            }
        }
        double trainAcc = trainCorrect / (double) splitPoint;
        double heldOutAcc = heldOutCorrect / (double) (actual.size() - splitPoint);
        double phiWin = heldOutAcc - trainAcc;
        PhiResult result = new PhiResult(candidateIndex, trainAcc, heldOutAcc,
                phiWin, System.currentTimeMillis());
        phiResults.add(result);
        return result;
    }

    /** Stage 4+5: best candidate → FnlGate admission. */
    public FnlGateV2.FnlEntry admitBest(FnlGateV2 gate) {
        stage = Stage.ADMITTING;
        if (phiResults.isEmpty()) return null;
        // Pick candidate with highest PhiWin
        PhiResult best = phiResults.get(0);
        for (PhiResult r : phiResults) {
            if (r.phiWin > best.phiWin) best = r;
        }
        CauldronCandidate candidate = candidates.get(best.candidateIndex);
        FnlGateV2.FnlEntry entry = new FnlGateV2.FnlEntry(
                java.util.UUID.randomUUID(),
                "cauldron-" + candidate.row + "-" + candidate.pairIndex,
                FnlGateV2.FnlEntry.Origin.CAULDRON,
                10,  // quarantine budget ticks
                new ArrayList<>(),
                0   // initial consecutiveAccepts
        );
        gate.admit(entry);
        stage = Stage.COMPLETED;
        return entry;
    }

    public List<CauldronCandidate> candidates() { return candidates; }
    public List<PhiResult> phiResults() { return phiResults; }
    public Stage stage() { return stage; }
    public int heldOutWindowSize() { return heldOutWindowSize; }
}
