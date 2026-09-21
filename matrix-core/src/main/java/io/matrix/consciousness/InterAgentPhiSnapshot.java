package io.matrix.consciousness;

import io.matrix.neuron.HdcBrain;
import io.matrix.neuron.SelfModel;
import io.matrix.neuron.WuWeiPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * W102 — Snapshot of inter-agent brain state for Φ measurement.
 *
 * <p>Captures the state of all three ConsciousBrain agents at a given cycle:
 * <ul>
 *   <li>HdcBrain.Recall — last recall's similarity, distance</li>
 *   <li>SelfModel.SelfModelResult — self representation, prediction errors</li>
 *   <li>WuWeiPolicy.Decision — current no-op/excess-surprise signal</li>
 * </ul>
 *
 * <p>Combined into an 8-dim continuous state vector that InterAgentPhi
 * can measure over time. Used by ConsciousBrain to emit inter-agent
 * integration metrics alongside the within-trajectory ones.
 *
 * <p>CONSTITUTION VI compliance: snapshot is a measurement substrate, not
 * a phenomenal consciousness claim.
 */
public record InterAgentPhiSnapshot(
        float[] stateVector,
        long cycleCount,
        long snapshotHash) {

    /**
     * Build an 8-dim state vector from the three agent states.
     * <ol>
     *   <li>selfRepresentation[0] — first dim of SelfModel.SelfModelResult.selfRepresentation</li>
     *   <li>selfRepresentation[1] — second dim</li>
     *   <li>primaryPredictionError — SelfModel primary error</li>
     *   <li>metaPredictionError — SelfModel meta error</li>
     *   <li>hdcSimilarity — HdcBrain.Recall.similarity</li>
     *   <li>hdcDistance — HdcBrain.Recall.distance</li>
     *   <li>wuweiExcess — WuWeiPolicy.Decision.excessSurprise</li>
     *   <li>wuweiNoOp (encoded ±1) — WuWeiPolicy.Decision.isNoOp() ? 1 : -1</li>
     * </ol>
     */
    public static InterAgentPhiSnapshot of(SelfModel.SelfModelResult selfMod,
                                             HdcBrain.Recall recall,
                                             WuWeiPolicy.Decision decision,
                                             long cycleCount) {
        float[] v = new float[8];
        // First 2 dims from selfRepresentation (truncate/pad)
        if (selfMod != null && selfMod.selfRepresentation() != null) {
            float[] sr = selfMod.selfRepresentation();
            v[0] = sr.length > 0 ? sr[0] : 0.0f;
            v[1] = sr.length > 1 ? sr[1] : 0.0f;
        }
        // Dims 2-3 from SelfModel prediction errors
        if (selfMod != null) {
            v[2] = (float) selfMod.primaryPredictionError();
            v[3] = (float) selfMod.metaPredictionError();
        }
        // Dims 4-5 from HdcBrain.Recall
        if (recall != null) {
            v[4] = (float) recall.similarity;
            v[5] = (float) recall.distance;
        }
        // Dims 6-7 from WuWeiPolicy.Decision
        if (decision != null) {
            // Decision: actionIndex, shouldAct, freeEnergy, threshold
            // excessSurprise ≈ freeEnergy - threshold when shouldAct; 0 otherwise
            double excess = decision.shouldAct() ? (decision.freeEnergy() - decision.threshold()) : 0.0;
            v[6] = (float) excess;
            v[7] = decision.isNoOp() ? 1.0f : -1.0f;
        }
        // Compute deterministic snapshot hash
        long h = 1469598103934665603L;
        h = (h ^ cycleCount) * 1099511628211L;
        for (float f : v) {
            h = (h ^ Float.floatToRawIntBits(f)) * 1099511628211L;
        }
        return new InterAgentPhiSnapshot(v, cycleCount, h);
    }

    /** Empty snapshot (no agents active). */
    public static InterAgentPhiSnapshot empty(long cycleCount) {
        return new InterAgentPhiSnapshot(new float[8], cycleCount, cycleCount);
    }

    /**
     * Compute Φ for a list of snapshots over time using the time-series
     * API of InterAgentPhi. Returns the integration between the three agents
     * across the episode.
     *
     * <p>Requires at least 2 snapshots for a non-trivial value.
     */
    public static double measure(List<InterAgentPhiSnapshot> snapshots) {
        if (snapshots == null || snapshots.size() < 2) {
            throw new IllegalArgumentException("need ≥ 2 snapshots");
        }
        double[][] samples = new double[snapshots.size()][8];
        for (int t = 0; t < snapshots.size(); t++) {
            float[] v = snapshots.get(t).stateVector();
            for (int i = 0; i < 8; i++) samples[t][i] = v[i];
        }
        return InterAgentPhi.measureTimeSeries(samples, 8);
    }
}
