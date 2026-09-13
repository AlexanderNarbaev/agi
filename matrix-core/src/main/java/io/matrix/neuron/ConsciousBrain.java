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

    public ConsciousBrain(int dims, long seed) {
        if (dims < 1) throw new IllegalArgumentException("dims must be ≥ 1");
        if (dims % 64 != 0) throw new IllegalArgumentException("dims must be multiple of 64");
        this.dims = dims;
        this.rng = new Random(seed);
        this.hdc = new HdcBrain(100, rng);
        this.hippocampus = new TwoStageConsolidator.HdcMemoryStore(dims);
        this.neocortex = new TwoStageConsolidator.HdcMemoryStore(dims);
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
        return new CycleReport(label,
                recall != null ? recall.label : null,
                surprise, decision.shouldAct(),
                selfMod.selfRepresentation(), pragmatic.success());
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
            boolean success) {}
}
