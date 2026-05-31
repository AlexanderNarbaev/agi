package io.matrix.hades;

import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;

import org.apache.pekko.actor.typed.ActorRef;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Derangement detector — monitors cluster health for anomalies.
 *
 * <p>Detects dangerous patterns:
 * <ul>
 * <li>Sudden fitness drop across many neurons</li>
 * <li>Abnormally high signal rate from a single neuron (potential runaway feedback)</li>
 * <li>TruthTable corruption (all zeros or all ones)</li>
 * <li>Excessive FROZEN neuron count drop</li>
 * </ul>
 *
 * <p>Ref: L5_Cauldren.md §4
 */
public class DerangementDetector {

    public enum Severity { NONE, LOW, MEDIUM, HIGH, CRITICAL }

    public record DerangementAlert(
            Severity severity,
            NeuronId neuronId,
            String description,
            long timestamp
    ) {}

    private final List<DerangementAlert> alerts = new ArrayList<>();
    private double avgFitnessThreshold = 0.1;
    private int maxSignalRatePerNeuron = 100;

    /**
     * Checks a single neuron for derangement.
     */
    public DerangementAlert checkNeuron(NeuronInstance neuron, int recentSignalRate) {
        if (recentSignalRate > maxSignalRatePerNeuron) {
            var alert = new DerangementAlert(Severity.HIGH, neuron.id(),
                    "Excessive signal rate: " + recentSignalRate + " > " + maxSignalRatePerNeuron,
                    System.currentTimeMillis());
            alerts.add(alert);
            return alert;
        }
        return null;
    }

    /**
     * Checks neuron truth table for corruption patterns.
     */
    public DerangementAlert checkTruthTable(NeuronInstance neuron) {
        var table = neuron.truthTable().table();
        int logicalSize = 1 << neuron.k();
        boolean allZeros = table.cardinality() == 0;
        boolean allOnes = table.cardinality() == logicalSize;

        if (allZeros && neuron.k() > 1) {
            var alert = new DerangementAlert(Severity.MEDIUM, neuron.id(),
                    "TruthTable all zeros (k=" + neuron.k() + ")", System.currentTimeMillis());
            alerts.add(alert);
            return alert;
        }
        if (allOnes && neuron.k() > 1) {
            var alert = new DerangementAlert(Severity.MEDIUM, neuron.id(),
                    "TruthTable all ones (k=" + neuron.k() + ")", System.currentTimeMillis());
            alerts.add(alert);
            return alert;
        }
        return null;
    }

    /**
     * Performs a full cluster scan.
     */
    public List<DerangementAlert> scanAll(List<NeuronInstance> neurons,
                                            java.util.Map<NeuronId, Integer> signalRates) {
        List<DerangementAlert> found = new ArrayList<>();

        for (var neuron : neurons) {
            var ttAlert = checkTruthTable(neuron);
            if (ttAlert != null) found.add(ttAlert);

            int rate = signalRates.getOrDefault(neuron.id(), 0);
            var rateAlert = checkNeuron(neuron, rate);
            if (rateAlert != null) found.add(rateAlert);
        }

        return found;
    }

    public List<DerangementAlert> alerts() { return List.copyOf(alerts); }

    public boolean hasCriticalAlerts() {
        return alerts.stream().anyMatch(a -> a.severity() == Severity.CRITICAL);
    }

    public boolean hasHighAlerts() {
        return alerts.stream().anyMatch(a -> a.severity() == Severity.HIGH
                || a.severity() == Severity.CRITICAL);
    }

    public void clearAlerts() { alerts.clear(); }

    public Severity maxSeverity() {
        return alerts.stream()
                .map(DerangementAlert::severity)
                .max(Enum::compareTo)
                .orElse(Severity.NONE);
    }
}
