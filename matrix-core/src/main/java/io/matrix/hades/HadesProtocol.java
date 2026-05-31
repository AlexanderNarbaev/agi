package io.matrix.hades;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HADES Protocol — Healing and Derangement Eradication System.
 *
 * <p>Responds to Derangement alerts with a structured recovery process:
 * <ol>
 * <li><b>ISOLATE</b> — quarantine affected neurons</li>
 * <li><b>SNAPSHOT</b> — create emergency snapshot before changes</li>
 * <li><b>ROLLBACK</b> — restore from last known good snapshot</li>
 * <li><b>ANALYZE</b> — forensic analysis of the failure</li>
 * <li><b>REPORT</b> — publish anonymized HADES log to Noosphere</li>
 * </ol>
 *
 * <p>Ref: L5_Cauldren.md §5, L8_Roadmap.md §3.5
 */
public class HadesProtocol {

    public enum HadesState { IDLE, ISOLATING, ROLLING_BACK, ANALYZING, COMPLETED, FAILED }

    public record HadesResult(
            HadesState state,
            int isolatedNeurons,
            int rolledBackNeurons,
            String snapshotId,
            String analysis,
            List<NeuronId> affectedNeuronIds
    ) {
        public static HadesResult completed(int isolated, int rolledBack,
                                              String snapshotId, String analysis,
                                              List<NeuronId> affected) {
            return new HadesResult(HadesState.COMPLETED, isolated, rolledBack,
                    snapshotId, analysis, affected);
        }

        public static HadesResult failed(String analysis) {
            return new HadesResult(HadesState.FAILED, 0, 0, null,
                    analysis, List.of());
        }
    }

    private final SnapshotStore snapshotStore;
    private final DerangementDetector detector;
    private final List<String> hadesLog = new ArrayList<>();
    private HadesState state = HadesState.IDLE;

    public HadesProtocol(SnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
        this.detector = new DerangementDetector();
    }

    public DerangementDetector detector() { return detector; }

    public HadesState state() { return state; }

    public List<String> hadesLog() { return List.copyOf(hadesLog); }

    /**
     * Executes the full HADES recovery procedure.
     *
     * @param neurons    current cluster neurons
     * @param signalRates recent signal rates per neuron
     * @param storeDir   directory for emergency snapshot
     */
    public HadesResult execute(Map<NeuronId, NeuronInstance> neurons,
                                Map<NeuronId, Integer> signalRates,
                                Path storeDir) throws IOException {
        hadesLog.add("HADES:START neurons=" + neurons.size());

        // Phase 1: Scan for derangement
        state = HadesState.ISOLATING;
        var alerts = detector.scanAll(
                new ArrayList<>(neurons.values()), signalRates);
        hadesLog.add("HADES:SCAN alerts=" + alerts.size());

        if (alerts.isEmpty()) {
            state = HadesState.IDLE;
            hadesLog.add("HADES:CLEAN no derangement detected");
            return HadesResult.completed(0, 0, null,
                    "No derangement detected", List.of());
        }

        // Phase 2: Isolate affected neurons
        List<NeuronId> affected = alerts.stream()
                .map(DerangementDetector.DerangementAlert::neuronId)
                .distinct()
                .toList();

        List<NeuronInstance> quarantine = new ArrayList<>();
        for (NeuronId id : affected) {
            var neuron = neurons.get(id);
            if (neuron != null) {
                quarantine.add(neuron);
                neurons.remove(id);
            }
        }
        hadesLog.add("HADES:ISOLATE count=" + quarantine.size()
                + " ids=" + affected);

        // Phase 3: Create emergency snapshot
        state = HadesState.ROLLING_BACK;
        ClusterSnapshot emergencySnapshot = snapshotStore.createSnapshot(
                neurons, 0);
        Path emergencyPath = snapshotStore.save(emergencySnapshot);
        hadesLog.add("HADES:EMERGENCY_SNAP " + emergencyPath.getFileName());

        // Phase 4: Rollback attempt — reload last good snapshot
        ClusterSnapshot lastGood = snapshotStore.loadLatest();
        int rolledBack = 0;
        String snapId = null;
        if (lastGood != null && !lastGood.snapshotId().equals(emergencySnapshot.snapshotId())) {
            List<NeuronInstance> restored = snapshotStore.restoreNeurons(lastGood);
            neurons.clear();
            for (var n : restored) {
                neurons.put(n.id(), n);
            }
            rolledBack = restored.size();
            snapId = lastGood.snapshotId();
            hadesLog.add("HADES:ROLLBACK " + rolledBack + " neurons from " + snapId);
        } else {
            hadesLog.add("HADES:ROLLBACK skipped — no prior snapshot");
        }

        // Phase 5: Analyze
        state = HadesState.ANALYZING;
        long criticalCount = alerts.stream()
                .filter(a -> a.severity() == DerangementDetector.Severity.CRITICAL
                        || a.severity() == DerangementDetector.Severity.HIGH)
                .count();
        String analysis = "Derangement: " + alerts.size() + " alerts ("
                + criticalCount + " high/critical), "
                + quarantine.size() + " neurons isolated, "
                + rolledBack + " restored from snapshot";

        hadesLog.add("HADES:ANALYSIS " + analysis);

        state = HadesState.COMPLETED;
        hadesLog.add("HADES:DONE");

        return HadesResult.completed(quarantine.size(), rolledBack,
                snapId, analysis, affected);
    }
}
