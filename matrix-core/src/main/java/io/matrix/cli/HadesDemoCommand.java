package io.matrix.cli;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.hades.DerangementDetector;
import io.matrix.hades.HadesProtocol;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.SnapshotStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

@Command(
        name = "hades",
        mixinStandardHelpOptions = true,
        description = "Pilot #6: HADES — self-healing after neuron damage")
public class HadesDemoCommand implements Callable<Integer> {

    @Option(names = {"-s", "--seed"}, description = "Random seed", defaultValue = "42")
    long seed;

    @Option(names = {"-n", "--neurons"}, description = "Number of neurons", defaultValue = "40")
    int neuronCount;

    @Option(names = {"-d", "--damage"}, description = "Damage fraction (0.0-1.0)", defaultValue = "0.3")
    double damageFraction;

    @Override
    public Integer call() throws IOException {
        var rng = new Random(seed);
        Path tempDir = Files.createTempDirectory("hades-demo-");

        System.out.println("=== Pilot #6: HADES — Self-Healing After Damage ===");

        Map<NeuronId, NeuronInstance> neurons = new HashMap<>();
        Map<NeuronId, Integer> signalRates = new HashMap<>();
        for (int i = 0; i < neuronCount; i++) {
            var id = NeuronId.create();
            neurons.put(id, NeuronInstance.stable(id, TruthTable.random(4, rng)));
            signalRates.put(id, 10 + rng.nextInt(20));
        }
        System.out.println("Cluster: " + neuronCount + " neurons");

        int damagedCount = (int) (neuronCount * damageFraction);
        int damaged = 0;
        for (var entry : neurons.entrySet()) {
            if (damaged >= damagedCount) break;
            entry.setValue(NeuronInstance.stable(entry.getKey(),
                    TruthTable.of(4, new BitSet())));
            signalRates.put(entry.getKey(), 200 + rng.nextInt(100));
            damaged++;
        }
        System.out.printf("Damaged %d/%d neurons (%.0f%%)%n",
                damaged, neuronCount, damageFraction * 100);

        DerangementDetector detector = new DerangementDetector();
        var alerts = detector.scanAll(
                new java.util.ArrayList<>(neurons.values()), signalRates);
        System.out.println("DerangementDetector: " + alerts.size() + " alerts");

        SnapshotStore store = new SnapshotStore(tempDir, "hades-demo");
        store.save(store.createSnapshot(neurons, 0));

        HadesProtocol hades = new HadesProtocol(store);
        var result = hades.execute(neurons, signalRates, tempDir);

        System.out.printf("Recovery: isolated=%d, rolledBack=%d, state=%s%n",
                result.isolatedNeurons(), result.rolledBackNeurons(), result.state());
        System.out.println("HADES log:");
        for (var entry : hades.hadesLog()) {
            System.out.println("  " + entry);
        }

        System.out.println("\nPilot #6 complete.");
        return 0;
    }
}
