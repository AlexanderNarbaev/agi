package io.matrix.research;

import io.matrix.chain.ChainDescriptor;
import io.matrix.chain.ChainId;
import io.matrix.chain.ChainRegistry;
import io.matrix.chain.MultiChainEnsemble;
import io.matrix.chain.StandardPredicates;
import io.matrix.chain.TriggerRule;
import io.matrix.imports.EnrichedChainEvaluator;
import io.matrix.neuron.AttractorDetector;
import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.FreeEnergyEvaluator;
import io.matrix.neuron.SynapticPruner;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.FnlEntry;
import io.matrix.noosphere.FnlRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 359 — Phase W final integration: single E2E test exercising
 * the full Phases P-V stack:
 *  - EnrichedNeuron (DESIGN-20) + distillation
 *  - FnlRegistry with INV-FNL-ONE (DESIGN-22)
 *  - Multi-model distillation (3+ models in 1 pool)
 *  - EnrichedChainEvaluator + ChainRegistry (DESIGN-21)
 *  - SynapticPruner (DESIGN-24)
 *  - FreeEnergyEvaluator (DESIGN-23)
 *  - AttractorDetector (DESIGN-28)
 *  - MultiChainEnsemble (BYZANTINE consensus)
 *  - TaskCell v2 + FnlGate v2 (Phase S)
 *  - Cauldron v2 (Phase S)
 */
class Exp359FinalIntegrationTest {

    @Test
    void fullPhasesPtoVIntegration() throws Exception {
        // ===== 1. Distill 3 models into FnlRegistry (INV-FNL-ONE) =====
        FnlRegistry registry = FnlRegistry.getInstance();
        registry.clear();

        List<Path> models = findAllSafetensors();
        if (models.isEmpty()) {
            // Build synthetic models if none
            models = List.of();
        }
        int totalNeurons = 0;
        for (Path m : models) {
            totalNeurons += distillIntoPool(registry, m,
                    m.getParent().getFileName().toString(), 1 << 11);
        }
        if (models.isEmpty()) {
            // Synthetic: 3 small chains
            for (int i = 0; i < 3; i++) {
                totalNeurons += distillSyntheticIntoPool(registry, "synth-" + i, 50);
            }
        }
        assertThat(totalNeurons).isGreaterThan(0);
        assertThat(registry.size()).isEqualTo(totalNeurons);
        System.out.printf("[Exp359] INV-FNL-ONE: %,d neurons across %d provenances%n",
                registry.size(), registry.provenances().size());

        // ===== 2. Synaptic pruning (DESIGN-24) =====
        List<EnrichedNeuron> pruned = SynapticPruner.prune(registryToEnriched(registry),
                0.3, SynapticPruner.DEFAULT_STRENGTHEN_FACTOR,
                SynapticPruner.DEFAULT_TOP_STRENGTHEN_PERCENTILE);
        System.out.printf("[Exp359] SynapticPruner: %d → %d (survival=%.2f)%n",
                registry.size(), pruned.size(),
                SynapticPruner.survivalRatio(registry.size(), pruned.size()));

        // ===== 3. MultiChainEnsemble with BYZANTINE consensus =====
        List<io.matrix.imports.BooleanChainRunner> chainRunners = buildSmallChains(3);
        MultiChainEnsemble ensemble = MultiChainEnsemble.of(
                chainRunners, MultiChainEnsemble.Strategy.BYZANTINE);
        boolean[] input = new boolean[256];
        Random rng = new Random(0xFEEDL);
        for (int i = 0; i < input.length; i++) input[i] = rng.nextBoolean();
        var consensus = ensemble.evaluate(input);
        assertThat(consensus.contributions()).hasSize(3);
        System.out.printf("[Exp359] MultiChainEnsemble: 3 chains → %d bits via %s%n",
                consensus.bits().length, consensus.winningRationale());

        // ===== 4. FreeEnergy check (DESIGN-23) =====
        var enriched = new EnrichedChainEvaluator(chainRunners.get(0))
                .evaluateEnriched(input);
        double F = FreeEnergyEvaluator.freeEnergy(enriched);
        boolean converged = FreeEnergyEvaluator.hasConverged(enriched);
        System.out.printf("[Exp359] FreeEnergy: F=%.4f, converged=%s%n", F, converged);

        // ===== 5. Attractor detection (DESIGN-28) =====
        var attractor = AttractorDetector.detect(chainRunners.get(0), input);
        System.out.printf("[Exp359] Attractor: iter=%d, basinRadius=%.2f%n",
                attractor.convergenceIteration(), attractor.basinRadius());
        assertThat(attractor.convergenceIteration()).isGreaterThan(0);

        // ===== 6. ChainRegistry (DESIGN-21) =====
        ChainRegistry chains = ChainRegistry.getInstance();
        chains.clear();
        ChainId chainA = chains.register(ChainDescriptor.of("A", chainRunners.get(0), 100));
        ChainId chainB = chains.register(ChainDescriptor.of("B", chainRunners.get(1), 50));
        chains.addRule(TriggerRule.of(StandardPredicates.noveltyCuriosity(0.0, 1.0),
                chainB, 10, "high novelty → B"));
        chains.addRule(TriggerRule.of(output -> true, chainA, 1000, "FROZEN-shutoff mandatory"));
        var triggered = chains.evaluateTriggers(enriched);
        assertThat(triggered).isNotEmpty();
        System.out.printf("[Exp359] ChainRegistry: %d rules, %d triggered%n",
                chains.rules().size(), triggered.size());

        // ===== 7. TaskCell + FnlGate (Phase S) =====
        io.matrix.lifecycle.TaskCellV2 cell =
                new io.matrix.lifecycle.TaskCellV2(
                        io.matrix.lifecycle.TaskCellV2.TaskCellSpec.simple(0xCAFE, 1000));
        cell.run(spec -> "verdict-ok");
        assertThat(cell.state()).isEqualTo(io.matrix.lifecycle.TaskCellV2.State.COMPLETED);

        io.matrix.lifecycle.FnlGateV2 gate = new io.matrix.lifecycle.FnlGateV2(2);
        io.matrix.lifecycle.FnlGateV2.FnlEntry entry = new io.matrix.lifecycle.FnlGateV2.FnlEntry(
                UUID.randomUUID(), "integration-test",
                io.matrix.lifecycle.FnlGateV2.FnlEntry.Origin.DISTILL,
                10, new ArrayList<>(), 0);
        gate.admit(entry);
        gate.tick(true);
        gate.tick(true);
        assertThat(gate.size()).isEqualTo(1);

        // ===== 8. Cauldron (Phase S) =====
        io.matrix.cauldron.CauldronProtocolV2 cauldron =
                new io.matrix.cauldron.CauldronProtocolV2(20);
        cauldron.generateRow1(registryToEnriched(registry).subList(0,
                Math.min(5, registryToEnriched(registry).size())));
        assertThat(cauldron.candidates()).isNotEmpty();
        cauldron.validate(0, List.of(true, false, true, true, false),
                List.of(true, false, true, true, true));
        cauldron.admitBest(gate);
        assertThat(cauldron.stage())
                .isEqualTo(io.matrix.cauldron.CauldronProtocolV2.Stage.COMPLETED);

        // ===== 9. SynapticPruner + FreeEnergy combined =====
        List<EnrichedNeuron> survivors = SynapticPruner.prune(pruned,
                0.2, SynapticPruner.DEFAULT_STRENGTHEN_FACTOR,
                SynapticPruner.DEFAULT_TOP_STRENGTHEN_PERCENTILE);
        System.out.printf("[Exp359] Final pool: %d survivors after 2-stage pruning%n",
                survivors.size());
        assertThat(survivors).isNotEmpty();

        System.out.println("[Exp359] ===== ALL PHASES P-V INTEGRATION OK =====");
    }

    // -- helpers --

    private static List<Path> findAllSafetensors() {
        List<Path> result = new ArrayList<>();
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path ext = p.resolve("models/external");
            if (!Files.exists(ext)) continue;
            try (var s = Files.list(ext)) {
                s.filter(Files::isDirectory).forEach(d -> {
                    Path st = d.resolve("model.safetensors");
                    if (Files.exists(st)) result.add(st);
                });
            } catch (IOException e) { /* skip */ }
            break;
        }
        return result;
    }

    private static int distillIntoPool(FnlRegistry registry, Path safetensors,
                                       String provenance, int budget) {
        io.matrix.imports.BooleanChainRunner runner =
                io.matrix.imports.BooleanChainRunner.loadFromSafetensors(
                        safetensors, "model", budget);
        if (runner == null || runner.layerCount() == 0) return 0;
        long now = System.currentTimeMillis();
        int n = 0;
        for (var layer : runner.layers()) {
            for (TruthTable t : layer.neurons()) {
                if (t == null) continue;
                EnrichedNeuron e = EnrichedNeuron.derive(t);
                registry.append(FnlEntry.fromEnriched(UUID.randomUUID(), e, provenance, now));
                n++;
            }
        }
        return n;
    }

    private static int distillSyntheticIntoPool(FnlRegistry registry, String provenance, int count) {
        long now = System.currentTimeMillis();
        Random rng = new Random(provenance.hashCode());
        for (int i = 0; i < count; i++) {
            int k = 8;
            BitSet bs = new BitSet(1 << k);
            int card = rng.nextInt(1 << k);
            for (int j = 0; j < card; j++) bs.set(rng.nextInt(1 << k));
            TruthTable t = TruthTable.of(k, bs);
            EnrichedNeuron e = EnrichedNeuron.derive(t);
            registry.append(FnlEntry.fromEnriched(UUID.randomUUID(), e, provenance, now));
        }
        return count;
    }

    private static List<EnrichedNeuron> registryToEnriched(FnlRegistry registry) {
        List<EnrichedNeuron> result = new ArrayList<>();
        for (FnlEntry e : registry.all()) {
            result.add(new EnrichedNeuron(e.table(), e.magnitude(),
                    e.chemicalVector(), e.tag()));
        }
        return result;
    }

    private static List<io.matrix.imports.BooleanChainRunner> buildSmallChains(int n) {
        List<io.matrix.imports.BooleanChainRunner> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(buildSmallChain(i, 0xAL + i * 0xCCCL));
        }
        return list;
    }

    private static io.matrix.imports.BooleanChainRunner buildSmallChain(int id, long seed) {
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
        return new io.matrix.imports.BooleanChainRunner("chain-" + id, "(test)", layers);
    }
}
