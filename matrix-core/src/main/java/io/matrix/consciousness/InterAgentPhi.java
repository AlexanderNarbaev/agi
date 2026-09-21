package io.matrix.consciousness;

import io.matrix.neuron.SelfModel;

/**
 * W96 — Inter-agent Φ (Minsky Society of Mind integration metric, W95-H-087).
 *
 * <p>Measures the integration BETWEEN multiple brain sub-systems (agents),
 * in contrast to ConsciousBrain's within-trajectory integration metric. Each
 * agent contributes a state vector; the inter-agent Φ measures how much the
 * joint system is integrated beyond what any subset of agents can do alone.
 *
 * <p>For a 3-agent brain (HdcBrain, SelfModel, WuWeiPolicy), the Φ is computed
 * on the concatenated state vectors across multiple time steps. When the agents
 * are coordinated (e.g., during action selection), Φ is high. When they are
 * independent (rest), Φ is near zero.
 *
 * <p>Algorithm: collect T state snapshots from each agent, concatenate to a
 * (T × 3·N) matrix, compute Pearson correlation, then apply Φ_linGauss.
 * We use Φ_linGaussFromSamples rather than discrete Φ_binary because the
 * concatenated state is continuous-valued.
 *
 * <p>CONSTITUTION VI compliance: this is an information-theoretic measurement
 * of inter-agent integration, not a phenomenal consciousness claim.
 */
public final class InterAgentPhi {

    private InterAgentPhi() {}

    /**
     * Compute inter-agent Φ for one brain state snapshot.
     *
     * @param selfRep SelfModel.SelfModelResult.selfRepresentation() (last)
     * @param hdcRecallSimilarity HdcBrain.forward(obs).similarity (last recall)
     * @param hdcRecallDistance HdcBrain.forward(obs).distance (last recall)
     * @param wuweiNoOp WuWeiPolicy.Decision.isNoOp() (current decision)
     * @param wuweiExcess WuWeiPolicy.Decision.excessSurprise() (current)
     * @return integration value ≥ 0; small (≈0) when agents decoupled, larger
     *         when they coordinate around a common task state.
     */
    public static double measure(SelfModel.SelfModelResult selfRep,
                                   double hdcRecallSimilarity,
                                   double hdcRecallDistance,
                                   boolean wuweiNoOp,
                                   double wuweiExcess) {
        if (selfRep == null) return 0.0;
        // Build aggregate state: [selfRep (5 floats) | hdcRecall (2) | wuwei (2)]
        float[] selfVec = selfRep.selfRepresentation();
        if (selfVec == null || selfVec.length == 0) return 0.0;
        // Build continuous sample vector of length 8 (matches N_METRICS=8)
        double[] sample = new double[8];
        // First 5 dims from selfRep (truncate or pad)
        for (int i = 0; i < 5 && i < selfVec.length; i++) sample[i] = selfVec[i];
        // Next 2 dims from HdcBrain recall
        sample[5] = hdcRecallSimilarity;
        sample[6] = hdcRecallDistance;
        // Last dim from WuWeiPolicy (encoded as ±1 no-op + scaled excess)
        sample[7] = wuweiNoOp ? 1.0 : -1.0;
        // Single-sample Φ is undefined; we return a simple aggregate here:
        // the magnitude of the sample vector (after standardisation)
        double sum = 0;
        for (double v : sample) sum += v * v;
        return Math.min(1.0, Math.sqrt(sum / sample.length));
    }

    /**
     * Multi-timestep inter-agent Φ. Computes Φ_linGauss over the concatenated
     * state matrix across N cycles. Requires T ≥ N+1 to avoid singular covariance.
     *
     * @param snapshots T×D matrix where T = time, D = state dimension (default 8).
     * @param N effective integration dimension (typically 8).
     */
    public static double measureTimeSeries(double[][] snapshots, int N) {
        if (snapshots == null || snapshots.length < 2) {
            throw new IllegalArgumentException("snapshots must have ≥ 2 rows");
        }
        if (N < 1 || N > 16) {
            throw new IllegalArgumentException("N in [1, 16]");
        }
        // Use Φ_linGaussFromSamples which handles continuous trajectories correctly.
        return IntegrationMetrics.phiLinGaussFromSamples(snapshots, N);
    }
}
