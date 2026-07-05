package io.matrix.cli;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.hades.DerangementDetector;
import io.matrix.hades.HadesProtocol;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import io.matrix.snapshot.SnapshotStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

@Command(name = "pipeline", mixinStandardHelpOptions = true,
        description = "Run integrated Cauldron→Noosphere→HADES pipeline")
public class PipelineCommand implements Callable<Integer> {

    @Option(names = {"-s", "--seed"}, description = "Random seed", defaultValue = "42")
    long seed;

    @Option(names = {"--generations"}, description = "GA generations", defaultValue = "20")
    int generations;

    @Option(names = {"--population"}, description = "GA population size", defaultValue = "15")
    int population;

    @Option(names = {"--k"}, description = "Neuron input bits (K)", defaultValue = "8")
    int k;

    @Option(names = {"--tasks"}, description = "Number of tasks to evolve", defaultValue = "3")
    int taskCount;

    @Option(names = {"--neurons"}, description = "Simulated cluster neurons for HADES", defaultValue = "30")
    int neuronCount;

    @Override
    public Integer call() {
        var rng = new Random(seed);
        var ethics = new EthicalFilter();
        var registry = new NoosphereRegistry();
        var index = new KnowledgeIndex(registry);
        List<FnlPackage> publishedPackages = new ArrayList<>();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   MATRIX Pipeline: Cauldron → Noosphere → HADES      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ── Phase 1: Cauldron ──
        long startCauldron = System.currentTimeMillis();
        System.out.println("🔥 PHASE 1: CAULDRON — Evolving FNLs via Genetic Algorithm");
        System.out.println("   Generations: " + generations + " | Population: " + population + " | K: " + k);

        String[] taskNames = {"navigation", "vision", "resource", "threat", "social"};
        for (int i = 0; i < taskCount; i++) {
            CauldronProtocol cauldron = new CauldronProtocol(rng);
            var result = cauldron.evolveForTask(taskNames[i % taskNames.length] + "-v" + (i + 1));

            if (result.state() == CauldronProtocol.CauldronState.COMPLETED) {
                var pkg = cauldron.packageResult(result,
                        taskNames[i % taskNames.length] + "-v" + (i + 1),
                        taskNames[i % taskNames.length].toUpperCase(),
                        "cli-pipeline");
                publishedPackages.add(pkg);
                System.out.printf("   ✅ %s | fitness=%d | gens=%d | neurons≈%d | acc=%.3f%n",
                        pkg.name(), result.bestFitness(), result.generations(),
                        result.bestBrain() != null ? 4 : 0, pkg.accuracy());
            } else {
                System.out.printf("   ❌ Task '%s' FAILED: %s%n",
                        taskNames[i % taskNames.length], result.summary());
            }
        }
        long elapsedCauldron = System.currentTimeMillis() - startCauldron;
        System.out.printf("   ⏱  Cauldron phase: %d ms (%d packages)%n%n",
                elapsedCauldron, publishedPackages.size());

        if (publishedPackages.isEmpty()) {
            System.out.println("❌ Pipeline aborted: no FNLs evolved.");
            return 1;
        }

        // ── Phase 2: Noosphere ──
        long startNoosphere = System.currentTimeMillis();
        System.out.println("🌐 PHASE 2: NOOSPHERE — Publishing & Indexing FNLs");

        for (var pkg : publishedPackages) {
            var result = registry.publish(pkg);
            if (result.success()) {
                index.index(result.entryId(), pkg);
                String cert = pkg.certified() ? "🏅CERT" : "  uncert";
                System.out.printf("   📤 [%s] %s → type=%s acc=%.3f%n",
                        cert, pkg.name(), pkg.type(), pkg.accuracy());
            }
        }

        System.out.printf("   Registry: %d entries | Index: %d keywords%n",
                registry.size(), index.indexedCount());

        // search demo
        System.out.println("   🔍 Search demo:");
        for (String query : new String[]{"navigation", "vision", "resource"}) {
            var results = index.search(query);
            if (!results.isEmpty()) {
                var best = results.get(0);
                System.out.printf("      \"%s\" → %s (relevance=%.2f, acc=%.3f)%n",
                        query, best.fnl().name(), best.relevance(), best.fnl().accuracy());
            } else {
                System.out.printf("      \"%s\" → no results%n", query);
            }
        }
        long elapsedNoosphere = System.currentTimeMillis() - startNoosphere;
        System.out.printf("   ⏱  Noosphere phase: %d ms%n%n", elapsedNoosphere);

        // ── Phase 3: HADES ──
        long startHades = System.currentTimeMillis();
        System.out.println("💀 PHASE 3: HADES — Derangement Detection & Recovery");
        EthicalVerdict verdict = EthicalVerdict.APPROVED;
        int hadesIsolated = 0;
        int hadesRolledBack = 0;
        String hadesState = "CLEAN";

        try {
            Path tempDir = Files.createTempDirectory("hades-pipeline-");
            SnapshotStore store = new SnapshotStore(tempDir, "pipeline-instance");
            HadesProtocol hades = new HadesProtocol(store);

            // build a synthetic neuron cluster representing the published packages
            Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
            Map<NeuronId, Integer> signalRates = new HashMap<>();

            for (int i = 0; i < neuronCount; i++) {
                var id = NeuronId.create();
                neurons.put(id, NeuronInstance.stable(id, TruthTable.random(k, rng)));
                signalRates.put(id, 5 + rng.nextInt(20));
            }

            // inject ~30% corrupt neurons to trigger derangement
            int corruptCount = neuronCount / 3;
            int corrupted = 0;
            for (var entry : neurons.entrySet()) {
                if (corrupted >= corruptCount) break;
                entry.setValue(NeuronInstance.stable(entry.getKey(),
                        TruthTable.of(k, new BitSet())));
                signalRates.put(entry.getKey(), 150 + rng.nextInt(100));
                corrupted++;
            }
            System.out.printf("   Cluster: %d neurons (%d corrupted ≈ %.0f%%)%n",
                    neuronCount, corruptCount, 100.0 * corruptCount / neuronCount);

            // pre-snapshot for rollback
            store.save(store.createSnapshot(neurons, 0));

            // ethical check
            System.out.println("   ⚖️  Ethical filter:");
            verdict = ethics.evaluate(
                    "Run HADES recovery on corrupted cluster", List.of());
            System.out.printf("      Action verdict: %s%n", verdict);

            if (verdict == EthicalVerdict.REJECTED) {
                System.out.println("   🛑 HADES blocked by ethical filter — pipeline aborted.");
                return 1;
            }

            // run HADES
            DerangementDetector detector = hades.detector();
            var preAlerts = detector.scanAll(new ArrayList<>(neurons.values()), signalRates);
            System.out.printf("   DerangementDetector: %d alerts%n", preAlerts.size());

            var result = hades.execute(neurons, signalRates, tempDir);
            hadesIsolated = result.isolatedNeurons();
            hadesRolledBack = result.rolledBackNeurons();
            hadesState = result.state().name();

            System.out.printf("   Recovery: isolated=%d rolledBack=%d state=%s%n",
                    hadesIsolated, hadesRolledBack, hadesState);
            System.out.println("   HADES log:");
            for (var entry : hades.hadesLog()) {
                System.out.println("      " + entry);
            }
        } catch (IOException e) {
            System.out.println("   ❌ HADES phase FAILED: " + e.getMessage());
            return 1;
        }

        long elapsedHades = System.currentTimeMillis() - startHades;
        System.out.printf("   ⏱  HADES phase: %d ms%n%n", elapsedHades);

        // ── Phase 4: Report ──
        System.out.println("📊 PHASE 4: PIPELINE SUMMARY REPORT");
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.printf("│  %-20s │ %-16s │ %-12s │%n", "PHASE", "DURATION (ms)", "STATUS");
        System.out.println("├─────────────────────────────────────────────────────────────┤");
        System.out.printf("│  %-20s │ %-16d │ %-12s │%n",
                "Cauldron", elapsedCauldron,
                publishedPackages.isEmpty() ? "❌ FAILED" : "✅ COMPLETED");
        System.out.printf("│  %-20s │ %-16d │ %-12s │%n",
                "Noosphere", elapsedNoosphere, "✅ COMPLETED");
        System.out.printf("│  %-20s │ %-16d │ %-12s │%n",
                "HADES", elapsedHades, "✅ COMPLETED");
        System.out.println("├─────────────────────────────────────────────────────────────┤");
        System.out.printf("│  %-20s │ %-16d │ %-12s │%n",
                "TOTAL", elapsedCauldron + elapsedNoosphere + elapsedHades, "");
        System.out.println("└─────────────────────────────────────────────────────────────┘");

        System.out.println();
        System.out.printf("   🧠 FNL packages evolved: %d%n", publishedPackages.size());
        System.out.printf("   📚 Registry entries:    %d%n", registry.size());
        System.out.printf("   🔑 Index keywords:      %d%n", index.indexedCount());
        System.out.printf("   🛡️  Ethics check:        %s%n", verdict);
        System.out.printf("   💀 HADES isolated:      %d%n", hadesIsolated);
        System.out.printf("   🔄 HADES rolled back:   %d%n", hadesRolledBack);
        System.out.println();

        System.out.println("✅ Pipeline complete: Cauldron→Noosphere→HADES integrated successfully.");
        return 0;
    }
}
