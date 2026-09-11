package io.matrix.research;

import io.matrix.cauldron.CauldronProtocolV2;
import io.matrix.chain.ChainDescriptor;
import io.matrix.chain.ChainId;
import io.matrix.chain.ChainRegistry;
import io.matrix.chain.MultiChainEnsemble;
import io.matrix.chain.StandardPredicates;
import io.matrix.chain.TriggerRule;
import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.EnrichedChainEvaluator;
import io.matrix.neuron.AttractorDetector;
import io.matrix.neuron.BoltzmannSampler;
import io.matrix.neuron.ChainHebbian;
import io.matrix.neuron.ContrastiveNeuron;
import io.matrix.neuron.CurriculumEngine;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.EnrichedVectorOps;
import io.matrix.neuron.FreeEnergyEvaluator;
import io.matrix.neuron.GrayScottSimulator;
import io.matrix.neuron.HopfieldAssociator;
import io.matrix.neuron.InfoBottleneck;
import io.matrix.neuron.KalmanStateEstimator;
import io.matrix.neuron.KauffmanNetwork;
import io.matrix.neuron.KohonenSOM;
import io.matrix.neuron.LSystem;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.PredictiveCoder;
import io.matrix.neuron.StdpUpdate;
import io.matrix.neuron.SynapticPruner;
import io.matrix.neuron.TensorTrain;
import io.matrix.neuron.GradientFlow;
import io.matrix.neuron.ThompsonSampler;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.FnlEntry;
import io.matrix.noosphere.FnlRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 385 — Master integration: exercises all 18 algorithm design
 * docs (DESIGN-23..43) + Phases P-V + multi-model distillation.
 *
 * <p>Single test, ~10s. Validates the entire algorithm library
 * works together: chemistry (Gray-Scott), biology (STDP, Hebbian,
 * pruning, REM, Kauffman), math (TT, KL, Kalman, Thompson, gradient
 * flow), physics (free energy, Hopfield, Boltzmann, attractors,
 * Kalman), learning (ZPD, contrastive, info bottleneck, predictive
 * coding), and the core MATRIX primitives (enriched neurons,
 * multi-model FNL, chain triggering, consensus, task cells, FNL
 * gate, cauldron).
 */
class Exp385MasterIntegrationTest {

    @Test
    void allAlgorithmsWorkTogether() {
        // ===== Setup =====
        FnlRegistry registry = FnlRegistry.getInstance();
        registry.clear();
        ChainRegistry chains = ChainRegistry.getInstance();
        chains.clear();

        // ===== 1. Distill synthetic neurons (INV-FNL-ONE) =====
        long now = System.currentTimeMillis();
        int distilled = 0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 20; j++) {
                int k = 8;
                BitSet bs = new BitSet(1 << k);
                new Random(i * 1000L + j).nextBytes(new byte[8]);
                Random r = new Random(i * 1000L + j);
                for (int x = 0; x < r.nextInt(1 << k); x++) {
                    bs.set(r.nextInt(1 << k));
                }
                TruthTable t = TruthTable.of(k, bs);
                EnrichedNeuron e = EnrichedNeuron.derive(t);
                registry.append(FnlEntry.fromEnriched(UUID.randomUUID(),
                        e, "model-" + i, now));
                distilled++;
            }
        }
        assertThat(distilled).isEqualTo(100);
        assertThat(registry.size()).isEqualTo(100);
        assertThat(registry.provenances().size()).isEqualTo(5);

        // ===== 2. Apply SynapticPruner (DESIGN-24) =====
        List<EnrichedNeuron> all = new ArrayList<>();
        for (FnlEntry e : registry.all()) {
            all.add(new EnrichedNeuron(e.table(), e.magnitude(),
                    e.chemicalVector(), e.tag()));
        }
        List<EnrichedNeuron> pruned = SynapticPruner.prune(all, 0.3,
                SynapticPruner.DEFAULT_STRENGTHEN_FACTOR,
                SynapticPruner.DEFAULT_TOP_STRENGTHEN_PERCENTILE);
        assertThat(pruned.size()).isLessThanOrEqualTo(100);

        // ===== 3. InfoBottleneck selection (DESIGN-41) =====
        int[][] activations = new int[10][5];
        int[] targets = new int[10];
        new Random(0xCAFE).nextBytes(new byte[1]);
        Random r2 = new Random(0xCAFE);
        for (int i = 0; i < 10; i++) {
            targets[i] = r2.nextInt(2);
            for (int j = 0; j < 5; j++) {
                activations[i][j] = (j == 0) ? targets[i] : r2.nextInt(2);
            }
        }
        int[] selected = InfoBottleneck.selectNeurons(activations, targets, 3);
        assertThat(selected).isNotEmpty();

        // ===== 4. Hopfield association (DESIGN-31) =====
        boolean[][] patterns = {
                {true, false, true, false, true, false, true, false},
                {false, true, false, true, false, true, false, true}
        };
        double[][] w = HopfieldAssociator.learn(patterns);
        boolean[] recalled = HopfieldAssociator.associate(patterns[0], w);
        assertThat(recalled).isEqualTo(patterns[0]);

        // ===== 5. Boltzmann sampling (DESIGN-32) =====
        boolean[] init = {true, false, true, false};
        double[][] wb = {{0, 1, 0, 1}, {1, 0, 1, 0}, {0, 1, 0, 1}, {1, 0, 1, 0}};
        boolean[] sampled = BoltzmannSampler.sample(init, wb, 0xCAFEL);
        assertThat(sampled).hasSize(4);

        // ===== 6. Kohonen SOM (DESIGN-33) =====
        double[][] som = {{0.1, 0.1}, {0.5, 0.5}, {0.9, 0.9}};
        int bmu = KohonenSOM.findBMU(som, new double[]{0.55, 0.5});
        assertThat(bmu).isEqualTo(1);

        // ===== 7. Thompson sampling (DESIGN-34) =====
        int[] succ = {90, 10};
        int[] fail = {10, 90};
        int arm0Wins = 0;
        for (long s = 0; s < 30; s++) {
            if (ThompsonSampler.sample(succ, fail, s) == 0) arm0Wins++;
        }
        assertThat(arm0Wins).isGreaterThan(20);

        // ===== 8. Kalman filter (DESIGN-35) =====
        var est = new KalmanStateEstimator.Estimate(0.0, 1.0);
        for (int i = 0; i < 30; i++) {
            est = KalmanStateEstimator.filterStep(est, 0.7, 0.001, 0.05);
        }
        assertThat(Math.abs(est.value() - 0.7)).isLessThan(0.1);

        // ===== 9. Tensor-train decomposition (DESIGN-36) =====
        boolean[] table = {true, false, true, true, false, false, true, false};
        var cores = TensorTrain.decompose1D(table, 2);
        assertThat(cores).hasSize(3);

        // ===== 10. L-system (DESIGN-37) =====
        Map<Character, String> rules = new HashMap<>();
        rules.put('A', "AB");
        rules.put('B', "A");
        String lsys = LSystem.generate("A", rules, 3);
        assertThat(lsys).isEqualTo("ABAAB");

        // ===== 11. Natural gradient (DESIGN-38) =====
        double[] natGrad = GradientFlow.naturalGradient(
                new double[]{0.5}, new double[]{0.1}, new double[]{0.5});
        assertThat(natGrad[0]).isCloseTo(-0.2,
                org.assertj.core.data.Offset.offset(1e-9));

        // ===== 12. Gray-Scott (DESIGN-39) =====
        int w8 = 4, h8 = 4;
        double[][] u = new double[w8][h8];
        double[][] v = new double[w8][h8];
        for (int i = 0; i < w8; i++) for (int j = 0; j < h8; j++) u[i][j] = 1.0;
        v[2][2] = 1.0;
        var gsResult = GrayScottSimulator.step(u, v, 0.05);
        assertThat(gsResult.length).isEqualTo(2);

        // ===== 13. Kauffman (DESIGN-40) =====
        var kspec = KauffmanNetwork.random(8, 2, 0xFEEDL);
        boolean[] ks = {true, false, true, false, true, false, true, false};
        boolean[] ks2 = KauffmanNetwork.step(ks, kspec.functions(), kspec.inputs());
        assertThat(ks2).hasSize(8);

        // ===== 14. Info bottleneck (DESIGN-41) =====
        int[] sel2 = InfoBottleneck.selectNeurons(activations, targets, 2);
        assertThat(sel2.length).isEqualTo(2);

        // ===== 15. Predictive coding (DESIGN-43) =====
        var pe = PredictiveCoder.computeError(
                new double[]{0.5, 0.5}, new double[]{0.0, 0.0});
        assertThat(pe.magnitude()).isCloseTo(0.707,
                org.assertj.core.data.Offset.offset(0.01));

        // ===== 16. STDP (DESIGN-29) =====
        double stdp = StdpUpdate.deltaMagnitude(0, 5);
        assertThat(stdp).isGreaterThan(0);

        // ===== 17. Contrastive Hebbian (DESIGN-30) =====
        EnrichedNeuron cn = new EnrichedNeuron(makeTable(8, 50, 0xAL), 0.5,
                new double[]{0.5, 0.5, 0.5, 0.5}, Neurotransmitter.SEROTONIN);
        EnrichedNeuron cnUp = ContrastiveNeuron.contrastive(cn, true, true, 0.05);
        assertThat(cnUp.magnitude()).isGreaterThan(cn.magnitude());

        // ===== 18. Hebbian chain learning (DESIGN-25) =====
        EnrichedNeuron cnHeb = ChainHebbian.strengthenOnCoFire(
                cn, List.of(cn, cn, cn), 0.1);
        assertThat(cnHeb.magnitude()).isGreaterThanOrEqualTo(cn.magnitude());

        // ===== 19. Dream replay (DESIGN-26) =====
        FnlEntry oldEntry = new FnlEntry(UUID.randomUUID(), makeTable(8, 50, 0xAL),
                0.5, new double[]{0.5, 0.5, 0.5, 0.5},
                Neurotransmitter.SEROTONIN, "test",
                List.of(), System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000, 0);
        var decayed = io.matrix.noosphere.DreamReplayer.replay(oldEntry,
                System.currentTimeMillis());
        assertThat(decayed.magnitude()).isLessThan(0.5);

        // ===== 20. Curriculum ZPD (DESIGN-27) =====
        List<CurriculumEngine.Scenario> scenarios = List.of(
                new CurriculumEngine.Scenario("math-1", 1, "math"),
                new CurriculumEngine.Scenario("math-5", 5, "math"));
        Map<String, Double> comp = new HashMap<>();
        comp.put("math-1", 0.95);
        comp.put("math-5", 0.70);
        var next = CurriculumEngine.selectNext(scenarios, comp, 0xAL);
        assertThat(next).isNotNull();

        // ===== 21. MultiChainEnsemble (BYZANTINE) =====
        List<BooleanChainRunner> runners = List.of(
                buildSmallChain(0xAA), buildSmallChain(0xBB));
        MultiChainEnsemble ens = MultiChainEnsemble.of(runners,
                MultiChainEnsemble.Strategy.BYZANTINE);
        var consensus = ens.evaluate(new boolean[256]);
        assertThat(consensus.contributions()).hasSize(2);

        // ===== 22. ChainRegistry + triggering =====
        ChainId cidA = chains.register(ChainDescriptor.of("A", runners.get(0), 100));
        ChainId cidB = chains.register(ChainDescriptor.of("B", runners.get(1), 50));
        chains.addRule(TriggerRule.of(StandardPredicates.noveltyCuriosity(0.0, 1.0),
                cidB, 10, "novelty → B"));
        chains.addRule(TriggerRule.of(o -> true, cidA, 1000, "FROZEN-shutoff mandatory"));
        var evOut = new EnrichedChainEvaluator(runners.get(0))
                .evaluateEnriched(new boolean[256]);
        var triggered = chains.evaluateTriggers(evOut);
        assertThat(triggered).isNotEmpty();

        // ===== 23. Attractor detection =====
        var attractor = AttractorDetector.detect(runners.get(0), new boolean[256]);
        assertThat(attractor.convergenceIteration()).isGreaterThan(0);

        // ===== 24. Free energy =====
        double F = FreeEnergyEvaluator.freeEnergy(evOut);
        assertThat(F).isFinite();

        // ===== 25. Vector ops (cosine similarity) =====
        double sim = EnrichedVectorOps.cosineSimilarity(
                new double[]{1.0, 0.0, 0.0, 0.0},
                new double[]{1.0, 0.0, 0.0, 0.0});
        assertThat(sim).isEqualTo(1.0);

        // ===== 26. TaskCell v2 + FnlGate v2 + Cauldron v2 =====
        var cell = new io.matrix.lifecycle.TaskCellV2(
                io.matrix.lifecycle.TaskCellV2.TaskCellSpec.simple(0xCAFE, 1000));
        cell.run(spec -> "ok");
        assertThat(cell.state()).isEqualTo(
                io.matrix.lifecycle.TaskCellV2.State.COMPLETED);

        var gate = new io.matrix.lifecycle.FnlGateV2(2);
        var fEntry = new io.matrix.lifecycle.FnlGateV2.FnlEntry(
                UUID.randomUUID(), "test",
                io.matrix.lifecycle.FnlGateV2.FnlEntry.Origin.DISTILL,
                5, new ArrayList<>(), 0);
        gate.admit(fEntry);
        gate.tick(true);
        gate.tick(true);
        assertThat(gate.size()).isEqualTo(1);

        var cauldron = new CauldronProtocolV2(20);
        cauldron.generateRow1(all.subList(0, Math.min(5, all.size())));
        cauldron.validate(0, List.of(true, false, true, true, false),
                List.of(true, false, true, true, true));
        cauldron.admitBest(gate);
        assertThat(cauldron.stage())
                .isEqualTo(CauldronProtocolV2.Stage.COMPLETED);

        // ===== Final assertion: pool integrity =====
        assertThat(registry.size()).isEqualTo(100);
        System.out.println("[Exp385] ===== ALL 26 ALGORITHM STAGES INTEGRATION OK =====");
    }

    // -- helpers --

    private static TruthTable makeTable(int k, int cardinality, long seed) {
        BitSet bs = new BitSet(1 << k);
        Random rng = new Random(seed);
        for (int i = 0; i < cardinality; i++) bs.set(rng.nextInt(1 << k));
        return TruthTable.of(k, bs);
    }

    private static BooleanChainRunner buildSmallChain(long seed) {
        List<io.matrix.imports.TruthTableLayer> layers = new ArrayList<>();
        Random rng = new Random(seed);
        for (int li = 0; li < 2; li++) {
            List<TruthTable> neurons = new ArrayList<>();
            for (int ni = 0; ni < 4; ni++) {
                int k = 8;
                BitSet bs = new BitSet(1 << k);
                int card = rng.nextInt(1 << k);
                for (int j = 0; j < card; j++) bs.set(rng.nextInt(1 << k));
                neurons.add(TruthTable.of(k, bs));
            }
            layers.add(new io.matrix.imports.TruthTableLayer(neurons, 8));
        }
        return new BooleanChainRunner("chain", "(test)", layers);
    }
}
