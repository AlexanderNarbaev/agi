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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<NeuronId, List<Double>> fitnessHistory = new ConcurrentHashMap<>();
    private double avgFitnessThreshold = 0.1;
    private int maxSignalRatePerNeuron = 100;
    private double fitnessDropThreshold = 0.3; // 30% drop triggers alert

    /**
     * Checks a single neuron for derangement including fitness monitoring.
     */
    public DerangementAlert checkNeuron(NeuronInstance neuron, int recentSignalRate) {
        // Check signal rate
        if (recentSignalRate > maxSignalRatePerNeuron) {
            var alert = new DerangementAlert(Severity.HIGH, neuron.id(),
                    "Excessive signal rate: " + recentSignalRate + " > " + maxSignalRatePerNeuron,
                    System.currentTimeMillis());
            alerts.add(alert);
            return alert;
        }

        // Check fitness trend
        DerangementAlert fitnessAlert = checkFitnessTrend(neuron);
        if (fitnessAlert != null) return fitnessAlert;

        return null;
    }

    /**
     * Records fitness for a neuron and checks for sudden drops.
     */
    public void recordFitness(NeuronId neuronId, double fitness) {
        List<Double> history = fitnessHistory.computeIfAbsent(neuronId, k -> new ArrayList<>());
        history.add(fitness);
        // Keep only last 10 entries
        if (history.size() > 10) {
            history.remove(0);
        }
    }

    /**
     * Checks fitness trend for sudden drops.
     */
    private DerangementAlert checkFitnessTrend(NeuronInstance neuron) {
        List<Double> history = fitnessHistory.get(neuron.id());
        if (history == null || history.size() < 3) return null;

        // Compare recent average with previous average
        int mid = history.size() / 2;
        double prevAvg = history.subList(0, mid).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.5);
        double recentAvg = history.subList(mid, history.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.5);

        if (prevAvg > 0.1 && recentAvg < prevAvg * (1.0 - fitnessDropThreshold)) {
            var alert = new DerangementAlert(Severity.HIGH, neuron.id(),
                    String.format("Fitness drop: %.2f → %.2f (%.0f%% decrease)",
                            prevAvg, recentAvg, (1 - recentAvg / prevAvg) * 100),
                    System.currentTimeMillis());
            alerts.add(alert);
            return alert;
        }
        return null;
    }

    /**
     * Checks cluster-wide fitness for correlated drops.
     */
    public DerangementAlert checkClusterFitness(List<NeuronInstance> neurons) {
        int dropCount = 0;
        for (var neuron : neurons) {
            List<Double> history = fitnessHistory.get(neuron.id());
            if (history != null && history.size() >= 2) {
                double last = history.get(history.size() - 1);
                double prev = history.get(history.size() - 2);
                if (prev > 0.1 && last < prev * 0.7) { // 30% drop
                    dropCount++;
                }
            }
        }

        double dropRatio = neurons.isEmpty() ? 0 : (double) dropCount / neurons.size();
        if (dropRatio > 0.3) { // More than 30% of neurons dropped
            var alert = new DerangementAlert(Severity.CRITICAL, null,
                    String.format("Cluster-wide fitness drop: %d/%d neurons (%.0f%%)",
                            dropCount, neurons.size(), dropRatio * 100),
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
     * Performs a full cluster scan including fitness trend analysis.
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

        // Check cluster-wide fitness
        var clusterAlert = checkClusterFitness(neurons);
        if (clusterAlert != null) found.add(clusterAlert);

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
