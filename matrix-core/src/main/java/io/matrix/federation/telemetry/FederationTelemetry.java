package io.matrix.federation.telemetry;

import io.matrix.federation.proto.TelemetryEvent;
import io.matrix.federation.runtime.FederationRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * W363 — Federation telemetry hooks.
 * 
 * Records metrics for federation operations:
 * - Proposal counts (by type and status)
 * - Vote counts (by decision)
 * - Registry mutations
 * - Per-operation latency
 * 
 * Per SPEC-013 metrics & observability section.
 * Per CONSTITUTION I v3: seeded Random for any sampling.
 */
public final class FederationTelemetry {
    
    /** Aggregated telemetry snapshot. */
    public record TelemetrySnapshot(
        int totalEvents,
        Map<String, Integer> eventCountsByType,
        Map<String, Integer> proposalCountsByStatus,
        Map<String, Integer> voteCountsByDecision,
        int registryMutations,
        long snapshotTimestampNs
    ) {}
    
    private final Random rng;
    private final FederationRuntime runtime;
    private final AtomicInteger totalEvents = new AtomicInteger(0);
    private final Map<String, Integer> eventCountsByType = new ConcurrentHashMap<>();
    private final Map<String, Integer> proposalCountsByStatus = new ConcurrentHashMap<>();
    private final Map<String, Integer> voteCountsByDecision = new ConcurrentHashMap<>();
    private final AtomicInteger registryMutations = new AtomicInteger(0);
    
    public FederationTelemetry(FederationRuntime runtime, long seed) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime cannot be null");
        }
        this.runtime = runtime;
        this.rng = new Random(seed);
    }
    
    /**
     * Record a proposal event.
     */
    public void recordProposal(String status) {
        if (status == null || status.isEmpty()) return;
        totalEvents.incrementAndGet();
        eventCountsByType.merge("proposal", 1, Integer::sum);
        proposalCountsByStatus.merge(status, 1, Integer::sum);
    }
    
    /**
     * Record a vote event.
     */
    public void recordVote(String decision) {
        if (decision == null || decision.isEmpty()) return;
        totalEvents.incrementAndGet();
        eventCountsByType.merge("vote", 1, Integer::sum);
        voteCountsByDecision.merge(decision, 1, Integer::sum);
    }
    
    /**
     * Record a registry mutation (add/update/remove).
     */
    public void recordMutation() {
        totalEvents.incrementAndGet();
        eventCountsByType.merge("mutation", 1, Integer::sum);
        registryMutations.incrementAndGet();
    }
    
    /**
     * Build a ProtoBuf TelemetryEvent for an arbitrary string event type.
     */
    public TelemetryEvent buildEvent(String eventType, String nodeId, String value) {
        return TelemetryEvent.newBuilder()
            .setEventType(eventType)
            .setNodeId(nodeId)
            .setStringValue(value)
            .setTimestampNs(System.nanoTime())
            .build();
    }
    
    /**
     * Get current telemetry snapshot.
     */
    public TelemetrySnapshot snapshot() {
        return new TelemetrySnapshot(
            totalEvents.get(),
            new java.util.HashMap<>(eventCountsByType),
            new java.util.HashMap<>(proposalCountsByStatus),
            new java.util.HashMap<>(voteCountsByDecision),
            registryMutations.get(),
            System.nanoTime()
        );
    }
    
    /**
     * Reset all counters.
     */
    public void reset() {
        totalEvents.set(0);
        eventCountsByType.clear();
        proposalCountsByStatus.clear();
        voteCountsByDecision.clear();
        registryMutations.set(0);
    }
    
    /**
     * Get total events recorded.
     */
    public int getTotalEventCount() {
        return totalEvents.get();
    }
    
    /**
     * Get deterministic random.
     */
    public Random getRng() {
        return rng;
    }
}
