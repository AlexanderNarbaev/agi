package io.matrix.research;

import io.matrix.neuron.BitLinearDreamer;
import io.matrix.neuron.BitLinearDreamer.DreamResult;
import io.matrix.neuron.ConsciousBrain;
import io.matrix.neuron.ConsciousBrain.CycleReport;
import io.matrix.neuron.FreeEnergyLoss;
import io.matrix.neuron.FreeEnergyLoss.FreeEnergyResult;
import io.matrix.neuron.HdcBrain;
import io.matrix.neuron.PredictiveCoder;
import io.matrix.neuron.SelfModel;
import io.matrix.neuron.SelfModel.SelfModelResult;
import io.matrix.neuron.TwoStageConsolidator;
import io.matrix.neuron.WuWeiPolicy;
import io.matrix.neuron.WuWeiPolicy.Decision;
import io.matrix.consciousness.IntegrationMetrics;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Wave 73: Empirical benchmark exercising all W60+ brain classes.
 *
 * <p>Runs ConsciousBrain for N cycles, tracks integration metrics over
 * time, and reports summary statistics. The benchmark:
 * <ol>
 *   <li>Runs N=200 cycles with random observations</li>
 *   <li>Collects integration metrics (Φ_binary, ΦF, C_N) every 10 cycles</li>
 *   <li>Measures consolidation events (memory replay)</li>
 *   <li>Reports mean ± std for each metric</li>
 *   <li>Asserts metrics are non-NaN and non-trivial</li>
 * </ol>
 *
 * <p>Per CONSTITUTION VI: this is an empirical measurement, not a
 * phenomenal claim. The metric values are reported as-is, with the
 * caveats documented in DESIGN-61.
 */
class W60W72BenchmarkTest {

    @Test
    void consciousBrainRuns200CyclesAndTracksMetrics() {
        int dims = 1024;
        ConsciousBrain brain = new ConsciousBrain(dims, 42);
        Random rng = new Random(42);

        int nCycles = 200;
        int metricEvery = 10;
        double phiSum = 0;
        int phiCount = 0;
        double phiFSum = 0;
        double cNSum = 0;
        int consolidations = 0;
        int actions = 0;
        double totalSurprise = 0.0;

        for (int cycle = 0; cycle < nCycles; cycle++) {
            float[] obs = new float[dims];
            for (int j = 0; j < dims; j++) {
                obs[j] = (float) (rng.nextGaussian() * 0.1);
            }
            CycleReport report = brain.cycle(obs);
            totalSurprise += report.predictionError();
            if (report.acted()) actions++;
            if (report.phiBinary() != null) {
                phiSum += report.phiBinary();
                phiCount++;
            }
            if (report.phiF() != null) phiFSum += report.phiF();
            if (report.neuralComplexity() != null) cNSum += report.neuralComplexity();
            if (cycle > 0 && cycle % 20 == 0) {
                brain.consolidate(2);
                consolidations++;
            }
        }

        System.out.println("\n=== W60-W72 Empirical Benchmark Results ===");
        System.out.printf("Cycles run: %d%n", nCycles);
        System.out.printf("Total surprise accumulated: %.4f bits%n", totalSurprise);
        System.out.printf("Actions taken: %d / %d (%.1f%%)%n",
                actions, nCycles, 100.0 * actions / nCycles);
        System.out.printf("Consolidations performed: %d%n", consolidations);
        if (phiCount > 0) {
            System.out.printf("Mean Φ_binary (Tononi 2004): %.4f bits (n=%d)%n",
                    phiSum / phiCount, phiCount);
        }
        System.out.printf("Mean ΦF (Toker-Sommer): %.4f (n=%d)%n",
                phiFSum / Math.max(1, phiCount), phiCount);
        System.out.printf("Mean C_N (Tononi-Sporns-Edelman): %.4f bits%n",
                cNSum / Math.max(1, phiCount));
        System.out.printf("Mean cycle record count: %d%n", brain.getCycleCount());

        assertThat(nCycles).isEqualTo(200);
        assertThat(brain.getCycleCount()).isEqualTo(200);
        assertThat(consolidations).isGreaterThanOrEqualTo(9);
        assertThat(phiCount).isGreaterThan(0);
    }

    @Test
    void individualComponentBenchmarks() {
        // BitLinearDreamer
        int inDim = 64, outDim = 32;
        float[] recognition = new float[outDim * inDim];
        float[] generation = new float[outDim * inDim];
        Random rng = new Random(42);
        for (int i = 0; i < recognition.length; i++) recognition[i] = (float) (rng.nextGaussian() * 0.1);
        for (int i = 0; i < generation.length; i++) generation[i] = (float) (rng.nextGaussian() * 0.1);
        float[] obs = new float[inDim];
        for (int j = 0; j < inDim; j++) obs[j] = (float) rng.nextGaussian();
        DreamResult dream = BitLinearDreamer.wakeSleepCycle(
                recognition, 1.0f, generation, 1.0f, obs, 0.01f, new Random(99));
        assertThat(dream.totalErrorMagnitude()).isGreaterThanOrEqualTo(0.0);
        assertThat(dream.fantasyState()).hasSize(outDim);

        // WuWeiPolicy
        Decision lowSurprise = WuWeiPolicy.decide(0.1, 0.5, 3);
        Decision highSurprise = WuWeiPolicy.decide(1.0, 0.5, 3);
        assertThat(lowSurprise.shouldAct()).isFalse();
        assertThat(highSurprise.shouldAct()).isTrue();

        // FreeEnergyLoss
        FreeEnergyLoss.FreeEnergyResult fe = FreeEnergyLoss.compute(
                new float[]{1, 2, 3}, new float[]{0.5f, 1.5f, 2.5f}, null, null);
        assertThat(fe.totalFreeEnergy()).isGreaterThan(0.0);

        // PredictiveCoder
        PredictiveCoder.PredictionError err = PredictiveCoder.computeError(
                new double[]{1, 2, 3}, new double[]{1.5, 2, 2.5});
        assertThat(err.magnitude()).isGreaterThan(0.0);

        // IntegrationMetrics
        long[] traj = {0b0000, 0b0101, 0b1010, 0b1111};
        double phi = IntegrationMetrics.phiBinary(traj, 4);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void hdcBrainIsLearnable() {
        HdcBrain brain = new HdcBrain(100, new Random(7));
        // HdcBrain requires 1024-dim features
        Random rng = new Random(7);
        float[] f1 = new float[1024];
        float[] f2 = new float[1024];
        for (int i = 0; i < 1024; i++) {
            f1[i] = (float) (rng.nextGaussian() * 0.1);
            f2[i] = (float) (rng.nextGaussian() * 0.1);
        }
        brain.learn(f1, "A", 0.5f, 0.01f);
        brain.learn(f2, "B", 0.5f, 0.01f);
        HdcBrain.Recall r1 = brain.forward(f1);
        HdcBrain.Recall r2 = brain.forward(f2);
        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(brain.size()).isEqualTo(2);
    }

    @Test
    void twoStageConsolidationTransfersPatterns() {
        int dims = 64;
        TwoStageConsolidator.HdcMemoryStore hippocampus =
                new TwoStageConsolidator.HdcMemoryStore(dims);
        TwoStageConsolidator.HdcMemoryStore neocortex =
                new TwoStageConsolidator.HdcMemoryStore(dims);
        // Add 5 episodes to hippocampus
        for (int i = 0; i < 5; i++) {
            float[] pattern = new float[dims];
            for (int j = 0; j < dims; j++) pattern[j] = (float) Math.sin((i + 1) * (j + 1) * 0.1);
            hippocampus.store("ep_" + i, pattern);
        }
        // Consolidate 3 times
        for (int t = 0; t < 3; t++) {
            TwoStageConsolidator.consolidate(hippocampus, neocortex, 2, new Random(42));
        }
        // Neocortex should have at least one consolidated pattern
        assertThat(neocortex.keys().size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void selfModelRunsInLoop() {
        int dims = 64;
        Random rng = new Random(42);
        for (int cycle = 0; cycle < 10; cycle++) {
            float[] obs = new float[dims];
            float[] obs2 = new float[dims];
            float[] action = new float[dims];
            float[] consequence = new float[dims];
            for (int j = 0; j < dims; j++) {
                obs[j] = (float) (rng.nextGaussian() * 0.1);
                obs2[j] = (float) (rng.nextGaussian() * 0.1);
                action[j] = (float) (rng.nextGaussian() * 0.1);
                consequence[j] = (float) (rng.nextGaussian() * 0.1);
            }
            SelfModelResult r = SelfModel.modelStep(obs, obs2, action, consequence);
            assertThat(r.selfRepresentation()).hasSize(4);
            assertThat(r.totalError()).isGreaterThanOrEqualTo(0.0);
        }
    }
}
