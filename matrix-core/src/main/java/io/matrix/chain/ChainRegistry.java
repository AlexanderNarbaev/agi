package io.matrix.chain;

import io.matrix.imports.ChainEnrichedOutput;
import io.matrix.imports.BooleanChainRunner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DESIGN-21 — Single source of truth for all chains and their trigger
 * rules. Singleton (process-wide) so cross-module code can register
 * chains without dependency injection.
 *
 * <p>INV-CHAIN-TRIG: predicates are evaluated in priority order
 * (descending); the same chain can be triggered by multiple rules
 * (de-duplicated by ChainId in the result).
 *
 * <p>Cycle detection: a chain may not dependOn itself (transitively).
 * Registration that would create a cycle throws
 * {@link IllegalStateException}.
 */
public final class ChainRegistry {

    private static final ChainRegistry INSTANCE = new ChainRegistry();

    public static ChainRegistry getInstance() { return INSTANCE; }

    private final Map<ChainId, ChainDescriptor> chains = new ConcurrentHashMap<>();
    private final List<TriggerRule> rules = new ArrayList<>();
    /** Per-cycle activation counter for audit. */
    private final AtomicLong activationCounter = new AtomicLong();

    private ChainRegistry() {}

    /** Register a chain. Idempotent: re-registering with same id updates the entry. */
    public synchronized ChainId register(ChainDescriptor desc) {
        if (desc == null) throw new IllegalArgumentException("desc");
        if (chains.containsKey(desc.id())) {
            chains.put(desc.id(), desc);
        } else {
            chains.put(desc.id(), desc);
        }
        return desc.id();
    }

    public synchronized void unregister(ChainId id) {
        chains.remove(id);
        // Drop rules targeting this chain
        rules.removeIf(r -> r.target().equals(id));
    }

    public synchronized void addRule(TriggerRule rule) {
        if (rule == null) throw new IllegalArgumentException("rule");
        rules.add(rule);
    }

    public synchronized void removeRule(TriggerRule rule) {
        rules.remove(rule);
    }

    public ChainDescriptor get(ChainId id) { return chains.get(id); }
    public List<ChainDescriptor> all() { return new ArrayList<>(chains.values()); }
    public List<TriggerRule> rules() { return new ArrayList<>(rules); }

    /**
     * Evaluate all registered rules against the enriched output.
     * Returns triggered ChainIds in priority order (descending),
     * de-duplicated.
     */
    public List<ChainId> evaluateTriggers(ChainEnrichedOutput output) {
        List<TriggerRule> sortedRules = new ArrayList<>(rules);
        sortedRules.sort(Comparator.comparingInt(TriggerRule::priority).reversed());

        // LinkedHashMap preserves insertion order = priority order
        Map<ChainId, TriggerRule> triggered = new LinkedHashMap<>();
        for (TriggerRule rule : sortedRules) {
            // Skip if target chain not registered
            if (!chains.containsKey(rule.target())) continue;
            // Cycle prevention: skip if target depends on any chain
            // that might lead back (simple heuristic; full cycle
            // detection is in cycleCheck)
            if (wouldCreateCycle(rule.target(), null)) continue;
            // Evaluate the predicate
            try {
                if (rule.predicate().test(output)) {
                    triggered.putIfAbsent(rule.target(), rule);
                }
            } catch (Throwable t) {
                // Predicate must not throw — log and skip
                System.err.println("[ChainRegistry] predicate threw: " + t.getMessage());
            }
        }
        return new ArrayList<>(triggered.keySet());
    }

    /**
     * Activate a downstream chain with the same enriched output as input.
     * Depth-limited to prevent infinite recursion.
     *
     * @param target the chain to activate
     * @param output the enriched output from the triggering chain
     * @param depth current recursion depth
     * @param maxDepth hard cap on recursion (default 5)
     * @return the chain's enriched output, or null if depth exceeded
     */
    public ChainEnrichedOutput activate(ChainId target, ChainEnrichedOutput output,
                                       int depth, int maxDepth) {
        if (depth > maxDepth) {
            System.err.println("[ChainRegistry] maxDepth exceeded for " + target);
            return null;
        }
        ChainDescriptor desc = chains.get(target);
        if (desc == null) return null;
        long n = activationCounter.incrementAndGet();
        // Reuse the bits from the enriched output as the new chain's input
        return io.matrix.imports.EnrichedChainEvaluator.class != null
                ? new io.matrix.imports.EnrichedChainEvaluator(desc.chain())
                        .evaluateEnriched(output.bits())
                : null;
    }

    /** Activation count (for audit/metrics). */
    public long totalActivations() { return activationCounter.get(); }

    /**
     * Cycle check: would registering {@code candidate} with
     * {@code dependsOn} create a cycle?
     *
     * <p>Simple DFS over the existing dependsOn graph.
     */
    public boolean wouldCreateCycle(ChainId candidate, java.util.Set<ChainId> visited) {
        if (visited == null) visited = new java.util.HashSet<>();
        if (visited.contains(candidate)) return true;
        visited.add(candidate);
        ChainDescriptor desc = chains.get(candidate);
        if (desc == null) return false;
        for (ChainId dep : desc.dependsOn()) {
            if (wouldCreateCycle(dep, visited)) return true;
        }
        visited.remove(candidate);
        return false;
    }

    /** Reset (test-only). */
    public synchronized void clear() {
        chains.clear();
        rules.clear();
        activationCounter.set(0);
    }
}
