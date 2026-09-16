package io.matrix.neuron;

import io.matrix.consciousness.ExtendedIntegrationMetrics;
import io.matrix.consciousness.IntegrationMetrics;
import io.matrix.consciousness.PhiId;

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
    /**
     * Continuous ring buffer for Φ_linGauss. Must be larger than N_METRICS so the
     * T×N sample matrix has rank N (T &gt; N required for non-singular correlation).
     * 32 gives 4× headroom over N=8.
     */
    private static final int CONTINUOUS_TRAJECTORY_LEN = 32;
    /** Continuous ±1 samples, ring-buffered. */
    private final double[][] continuousTrajectory;
    private int continuousIdx = 0;
    private int continuousCount = 0;
    /**
     * Cadence for extended integration metrics (Φ_linGauss + PhiID). Discrete metrics
     * are computed every cycle; extended metrics every EXTENDED_EVERY cycles to amortize
     * the LU-decomposition cost.
     */
    private static final int EXTENDED_EVERY = 10;
    /** Last valid trajectory snapshot, used to feed extended metrics. */
    private long[] lastTrajectory = new long[0];
    private int lastTrajectoryLen = 0;
    /** Snapshot of continuous trajectory (double[][] samples × variables) for extended metrics. */
    private double[][] lastContinuousSnapshot;
    private int lastContinuousLen = 0;

    public ConsciousBrain(int dims, long seed) {
        if (dims < 1) throw new IllegalArgumentException("dims must be ≥ 1");
        if (dims % 64 != 0) throw new IllegalArgumentException("dims must be multiple of 64");
        this.dims = dims;
        this.rng = new Random(seed);
        this.hdc = new HdcBrain(100, rng);
        this.hippocampus = new TwoStageConsolidator.HdcMemoryStore(dims);
        this.neocortex = new TwoStageConsolidator.HdcMemoryStore(dims);
        this.trajectory = new long[TRAJECTORY_LEN];
        this.continuousTrajectory = new double[CONTINUOUS_TRAJECTORY_LEN][N_METRICS];
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
        // Extended metrics (Φ_linGauss + PhiID 4-atom) computed on slower cadence.
        // We only run the expensive LU decomposition every EXTENDED_EVERY cycles.
        // Snapshot the continuous trajectory (32 samples × 8 dims) in chronological order.
        io.matrix.consciousness.ExtendedIntegrationMetrics extended = null;
        if (cycleCount % EXTENDED_EVERY == 0 && cycleCount > 0 && continuousCount >= 2) {
            int snapLen = Math.min(continuousCount, CONTINUOUS_TRAJECTORY_LEN);
            int N = Math.min(N_METRICS, dims);
            // Compute the start index for the oldest sample in the ring buffer.
            // When the buffer is full (continuousCount == CONTINUOUS_TRAJECTORY_LEN),
            // the oldest sample is at continuousIdx (the next slot to write).
            int start;
            if (continuousCount < CONTINUOUS_TRAJECTORY_LEN) {
                start = 0;  // Buffer not full: samples start from 0
            } else {
                start = continuousIdx % CONTINUOUS_TRAJECTORY_LEN;
            }
            lastContinuousSnapshot = new double[snapLen][N];
            for (int i = 0; i < snapLen; i++) {
                System.arraycopy(continuousTrajectory[(start + i) % CONTINUOUS_TRAJECTORY_LEN],
                        0, lastContinuousSnapshot[i], 0, N);
            }
            lastContinuousLen = snapLen;
            extended = computeExtendedMetrics(lastContinuousSnapshot, N);
        }

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
                phi, phiR, phiF, cN, tickling,
                extended);
    }

    /**
     * Append the current observation to the trajectory buffers.
     * Called on every cycle so both the discrete (long[]) and continuous (double[][])
     * trajectories accumulate multi-timestep history.
     */
    private void appendTrajectory(float[] observation) {
        int N = Math.min(N_METRICS, dims);
        trajectory[trajectoryIdx % trajectory.length] = extractBits(observation, N);
        trajectoryIdx++;
        // Continuous samples: convert first N dims to ±1 (sign-bit representation).
        // This is what the original observation "looks like" in the brain's continuous
        // workspace, suitable for Φ_linGauss (closed-form linear-Gaussian) and PhiID.
        int slot = continuousIdx % CONTINUOUS_TRAJECTORY_LEN;
        for (int i = 0; i < N; i++) {
            continuousTrajectory[slot][i] = observation[i] > 0 ? 1.0 : -1.0;
        }
        continuousIdx++;
        if (continuousCount < CONTINUOUS_TRAJECTORY_LEN) continuousCount++;
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
        // Cache the trajectory snapshot for extended metrics (Φ_linGauss + PhiID) computed
        // on a slower cadence by the calling cycle(). The snapshot is in chronological order.
        lastTrajectory = traj;
        lastTrajectoryLen = traj.length;
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
     * W92 — Compute extended integration metrics: Φ_linGauss + PhiID 4-atom decomposition.
     *
     * <p>Operates on the same multi-timestep trajectory as computeIntegrationMetrics().
     * Computed on a separate cadence (every 10 cycles) because LU decomposition over
     * 2^(N-1) bipartitions is more expensive than the discrete Φ_binary enumeration.
     *
     * <p>PhiID uses a Gaussian model: bits are converted to ±1 samples, then
     * PhiId.system() averages pairwise atoms across all C(N,2) pairs.
     *
     * <p>For Φ_linGauss, we use the trajectory buffer of continuous ±1 samples
     * (T=8, N=8). With T=N the correlation matrix is mathematically singular, so
     * the bipartition MI collapses to 0. This is a known property of Φ_linGauss
     * (closed-form linear-Gaussian formula requires T >> N). For the brain to
     * emit non-trivial Φ_linGauss, the trajectory buffer must be longer than N.
     * We record both numbers and let consumers decide which interpretation fits.
     */
    private io.matrix.consciousness.ExtendedIntegrationMetrics computeExtendedMetrics(double[][] samples, int N) {
        if (samples == null || samples.length < 2) return null;
        try {
            Double phiLinGauss = IntegrationMetrics.phiLinGaussFromSamples(samples, N);
            PhiId.PhiIdSystem system = PhiId.system(samples);
            return ExtendedIntegrationMetrics.of(phiLinGauss, system);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
            Boolean ticklingFlag,
            io.matrix.consciousness.ExtendedIntegrationMetrics extended) {}
}
