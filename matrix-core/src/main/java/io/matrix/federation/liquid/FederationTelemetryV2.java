package io.matrix.federation.liquid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * W574 — Federation Telemetry v2.
 *
 * Adds metrics: modulator_levels, role_changes, consensus_rounds.
 * Export to Prometheus + JSON log.
 */
public final class FederationTelemetryV2 {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, Double> gauges = new ConcurrentHashMap<>();
    private final List<TelemetryEvent> eventLog = Collections.synchronizedList(new ArrayList<>());

    public record TelemetryEvent(String name, Map<String, String> labels, double value, long timestampNs) {}

    public FederationTelemetryV2() {
        // Initialize standard counters
        counters.put("consensus_rounds_total", new AtomicLong(0));
        counters.put("role_changes_total", new AtomicLong(0));
        counters.put("votes_cast_total", new AtomicLong(0));
        counters.put("tasks_completed_total", new AtomicLong(0));
        counters.put("violations_total", new AtomicLong(0));
    }

    /**
     * Increment a counter.
     */
    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Increment a counter by a specific amount.
     */
    public void incrementCounter(String name, long amount) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(amount);
    }

    /**
     * Set a gauge value.
     */
    public void setGauge(String name, double value) {
        gauges.put(name, value);
    }

    /**
     * Record a telemetry event.
     */
    public void recordEvent(String name, Map<String, String> labels, double value) {
        eventLog.add(new TelemetryEvent(name, labels, value, System.nanoTime()));
    }

    /**
     * Record modulator levels.
     */
    public void recordModulatorLevels(Map<String, Double> levels) {
        for (var entry : levels.entrySet()) {
            setGauge("modulator_" + entry.getKey().toLowerCase(), entry.getValue());
        }
    }

    /**
     * Record a role change event.
     */
    public void recordRoleChange(long nodeId, NodeRole from, NodeRole to) {
        incrementCounter("role_changes_total");
        recordEvent("role_change", Map.of(
                "node_id", String.valueOf(nodeId),
                "from", from.name(),
                "to", to.name()
        ), to.ordinal());
    }

    /**
     * Record a consensus round.
     */
    public void recordConsensusRound(String proposalId, CapabilityConsensusEngine.ConsensusStatus status) {
        incrementCounter("consensus_rounds_total");
        recordEvent("consensus_round", Map.of(
                "proposal_id", proposalId,
                "status", status.name()
        ), status.ordinal());
    }

    /**
     * Get a counter value.
     */
    public long getCounter(String name) {
        AtomicLong counter = counters.get(name);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Get a gauge value.
     */
    public double getGauge(String name) {
        return gauges.getOrDefault(name, 0.0);
    }

    /**
     * Get all counters.
     */
    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new HashMap<>();
        counters.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Get all gauges.
     */
    public Map<String, Double> getAllGauges() {
        return Collections.unmodifiableMap(gauges);
    }

    /**
     * Get event log.
     */
    public List<TelemetryEvent> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    /**
     * Export metrics in Prometheus format.
     */
    public String exportPrometheus() {
        StringBuilder sb = new StringBuilder();
        counters.forEach((name, counter) -> {
            sb.append("# TYPE ").append(name).append(" counter\n");
            sb.append(name).append(" ").append(counter.get()).append("\n");
        });
        gauges.forEach((name, value) -> {
            sb.append("# TYPE ").append(name).append(" gauge\n");
            sb.append(name).append(" ").append(value).append("\n");
        });
        return sb.toString();
    }

    /**
     * Export metrics in JSON format.
     */
    public String exportJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"counters\":{");
        boolean first = true;
        for (var entry : counters.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().get());
            first = false;
        }
        sb.append("},\"gauges\":{");
        first = true;
        for (var entry : gauges.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }
}
