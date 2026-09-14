package io.matrix.neuron;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * RUN 477 — ConsciousBrain: integrated L7 Capability Level (DESIGN-60).
 *
 * <p>Combines all W60+ components into a single working "conscious-like"
 * system that demonstrates integrated behavior.
 */
public final class ConsciousBrain {

    private final HdcBrain hdc;
    private final int dims;
    private final Random rng;
    private final TwoStageConsolidator.HdcMemoryStore hippocampus;
    private final TwoStageConsolidator.HdcMemoryStore neocortex;
    private final Map<String, Double> meaningStore = new HashMap<>();
    private final SelfModel.SelfModelResult[] selfHistory = new SelfModel.SelfModelResult[100];
    private int selfHistoryIdx = 0;
    private long cycleCount = 0;
    /** Trajectory buffer for multi-timestep integration metrics. */
    private final long[] trajectory;
    private int trajectoryIdx = 0;
    /** Trajectory threshold before computing multi-state metrics. */
    private static final int TRAJECTORY_LEN = 8;
    private static final int N_METRICS = 8;

    public ConsciousBrain(int dims, long seed) {
        if (dims < 1) throw new IllegalArgumentException("dims must be ≥ 1");
        if (dims % 64 != 0) throw new IllegalArgumentException("dims must be multiple of 64");
        this.dims = dims;
        this.rng = new Random(seed);
        this.hdc = new HdcBrain(100, rng);
        this.hippocampus = new TwoStageConsolidator.HdcMemoryStore(dims);
        this.neocortex = new TwoStageConsolidator.HdcMemoryStore(dims);
        this.trajectory = new long[TRAJECTORY_LEN];
    }

    public CycleReport cycle(float[] observation) {
        if (observation == null || observation.length != dims) {
            throw new IllegalArgumentException("observation size mismatch");
        }

        // 1. Encode observation via HdcBrain
        String label = "obs_" + (cycleCount % 100);
        hdc.learn(observation, label, 0.5f, 0.01f);
        HdcBrain.Recall recall = hdc.forward(observation);

        // 2. Compute prediction error (use observation as its own prediction for now;
        //    real systems would have a generative model).
        PredictiveCoder.PredictionError error = PredictiveCoder.computeError(
                toDouble(observation), toDouble(observation));
        double surprise = error.magnitude();

        // 2b. Compute integration metrics — trajectory is updated on every cycle so that
        // by the time metrics are computed we have a multi-timestep history.
        // Always record into the trajectory buffer; emit metrics once buffer has ≥2 entries.
        appendTrajectory(observation);
        io.matrix.consciousness.IntegrationMetricsResult metrics =
                computeIntegrationMetrics(observation);

        // 3. Self-model (simplified: all vectors same dims)
        float[] primaryAction = new float[dims];
        SelfModel.SelfModelResult selfMod = SelfModel.modelStep(
                observation, observation.clone(), primaryAction, observation);
        selfHistory[selfHistoryIdx % selfHistory.length] = selfMod;
        selfHistoryIdx++;

        // 4. Wu-wei decision
        WuWeiPolicy.Decision decision = WuWeiPolicy.decide(
                surprise, dims * 0.005, 0);

        // 5. Store episode in hippocampus
        hippocampus.store(label, observation);
        // 6. Consolidate occasionally
        if (cycleCount > 0 && cycleCount % 5 == 0) {
            TwoStageConsolidator.consolidate(hippocampus, neocortex, 2, rng);
        }

        // 7. Pragmatic meaning
        PragmaticTest.PragmaticResult pragmatic = PragmaticTest.trial(
                observation, decision.isNoOp() ? "noop" : "act", !decision.shouldAct());
        Map<String, Double> tempStore = new HashMap<>(meaningStore);
        PragmaticTest.runTrials(
                java.util.Collections.singletonList(
                        new PragmaticTest.Trial(observation, pragmatic.action(),
                                                  pragmatic.success())),
                tempStore);
        meaningStore.putAll(tempStore);

        cycleCount++;
        // Build extended report
        Double phi = metrics != null ? metrics.phiBinary() : null;
        Double phiR = metrics != null ? metrics.phiR() : null;
        Double phiF = metrics != null ? metrics.phiF() : null;
        Double cN = metrics != null ? metrics.neuralComplexity() : null;
        Boolean tickling = metrics != null ? metrics.ticklingFlag() : null;
        return new CycleReport(label,
                recall != null ? recall.label : null,
                surprise, decision.shouldAct(),
                selfMod.selfRepresentation(), pragmatic.success(),
                phi, phiR, phiF, cN, tickling);
    }

    /**
     * Append the current observation to the trajectory buffer.
     * Called on every cycle so the trajectory accumulates multi-timestep history.
     */
    private void appendTrajectory(float[] observation) {
        int N = Math.min(N_METRICS, dims);
        trajectory[trajectoryIdx % trajectory.length] = extractBits(observation, N);
        trajectoryIdx++;
    }

    /**
     * Compute integration metrics from multi-timestep trajectory.
     * Returns null if fewer than 2 timesteps have been recorded.
     */
    private io.matrix.consciousness.IntegrationMetricsResult computeIntegrationMetrics(
            float[] observation) {
        // N: bits used for state representation (max 8 for Phi metrics)
        int N = Math.min(N_METRICS, dims);
        // Need at least 2 timesteps for meaningful entropy
        int actualLen = Math.min(trajectoryIdx, trajectory.length);
        if (actualLen < 2) {
            return null; // Skip metrics until we have history
        }
        // Use the actual filled portion of the buffer, ordered by recency
        long[] traj;
        if (trajectoryIdx <= trajectory.length) {
            traj = new long[actualLen];
            System.arraycopy(trajectory, 0, traj, 0, actualLen);
        } else {
            // Circular: read from oldest at trajectoryIdx%length to end, then start to that point
            traj = new long[trajectory.length];
            int start = trajectoryIdx % trajectory.length;
            int firstLen = trajectory.length - start;
            System.arraycopy(trajectory, start, traj, 0, firstLen);
            System.arraycopy(trajectory, 0, traj, firstLen, start);
        }
        try {
            double phi = io.matrix.consciousness.IntegrationMetrics.phiBinary(traj, N);
            double phiR = io.matrix.consciousness.IntegrationMetrics.phiR(traj, N);
            double cN = io.matrix.consciousness.IntegrationMetrics.neuralComplexity(traj, N);
            // ΦF: compute density distribution forward and backward over the trajectory.
            // For phiF (Hamming-cube W1), distribution size must be a power of 2;
            // we round N+1 up to the next power of 2 (matching phiFFromBitLinear).
            int nBinsF = nextPow2(N + 1);
            double[] forward = new double[nBinsF];
            double[] backward = new double[nBinsF];
            for (int t = 0; t < traj.length; t++) {
                int density = Long.bitCount(traj[t]);
                if (density < nBinsF) forward[density] += 1.0;
            }
            for (int t = traj.length - 1; t >= 0; t--) {
                int density = Long.bitCount(traj[t]);
                if (density < nBinsF) backward[density] += 1.0;
            }
            double sumF = 0, sumB = 0;
            for (double v : forward) sumF += v;
            for (double v : backward) sumB += v;
            for (int i = 0; i < forward.length; i++) {
                forward[i] /= sumF;
                backward[i] /= sumB;
            }
            double phiF = io.matrix.consciousness.IntegrationMetrics.phiF(forward, backward);
            // Compute tickling flag
            io.matrix.consciousness.TicklingDetector.TicklingResult tickling =
                    io.matrix.consciousness.TicklingDetector.detect(phi, phiR);
            return new io.matrix.consciousness.IntegrationMetricsResult(
                    phi, phiR, phiF, cN, tickling.ticklingFlag());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Smallest power of 2 ≥ n, with floor of 2. */
    private static int nextPow2(int n) {
        int p = 2;
        while (p < n) p <<= 1;
        return Math.max(p, 2);
    }

    /**
     * Extract first N bits from a float[] observation as bit-packed long.
     */
    private static long extractBits(float[] obs, int N) {
        long state = 0;
        for (int i = 0; i < N; i++) {
            int bit = obs[i] > 0 ? 1 : 0;
            state |= ((long) bit << i);
        }
        return state;
    }

    public void consolidate(int replayCount) {
        TwoStageConsolidator.consolidate(hippocampus, neocortex, replayCount, rng);
    }

    public double freeEnergy(float[] observation) {
        float[] pred = new float[dims];
        if (selfHistoryIdx > 0) {
            int idx = (selfHistoryIdx - 1) % selfHistory.length;
            if (selfHistory[idx] != null) {
                float[] selfRep = selfHistory[idx].selfRepresentation();
                for (int i = 0; i < dims && i < selfRep.length; i++) {
                    pred[i] = selfRep[i];
                }
            }
        }
        FreeEnergyLoss.FreeEnergyResult r = FreeEnergyLoss.compute(
                observation, pred, null, null);
        return r.totalFreeEnergy();
    }

    public Map<String, Double> getMeaningStore() {
        return new HashMap<>(meaningStore);
    }

    public long getCycleCount() {
        return cycleCount;
    }

    private static double[] toDouble(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) d[i] = f[i];
        return d;
    }

    public record CycleReport(
            String observationLabel,
            String predictionLabel,
            double predictionError,
            boolean acted,
            float[] selfRepresentation,
            boolean success,
            Double phiBinary,
            Double phiR,
            Double phiF,
            Double neuralComplexity,
            Boolean ticklingFlag) {}
}
