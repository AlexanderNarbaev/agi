package io.matrix.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Cascade tombstoning — when a resource is deleted, all dependent resources
 * are automatically tombstoned as well.
 *
 * <p>Use case: an agent in the Noosphere is associated with one or more
 * neurons, one or more snapshots, and one or more derived knowledge
 * packages. When GDPR Art. 17 is invoked on the agent, we must cascade
 * the erasure to all dependents — otherwise we have "lingering fingerprints"
 * of the deleted data subject in derived artefacts.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@link #register(String, String, CascadeRule)} declares a cascade
 *       rule: "when resource of type {@code A} with id {@code id} is
 *       tombstoned, also tombstone all resources of type {@code B} returned
 *       by the resolver."</li>
 *   <li>Multiple rules can target the same dependent type (union semantics).</li>
 *   <li>Recursion depth is bounded (default 3) to prevent infinite cascades.</li>
 * </ul>
 *
 * <p>Audit: every cascade creates a child tombstone whose {@code reason}
 * contains the parent's id for traceability.
 *
 * <p>Ref: L6 §6.7 (GDPR cascade), L12 §4 (legal framework).
 */
public final class CascadeTombstoneService {

    private static final Logger log = LoggerFactory.getLogger(CascadeTombstoneService.class);

    /** Default max cascade depth — prevents infinite loops. */
    public static final int DEFAULT_MAX_DEPTH = 3;

    /** A single rule: "when A is tombstoned, also tombstone each B returned by resolver". */
    public record CascadeRule(
            String sourceType,            // e.g. "Agent"
            String targetType,            // e.g. "Neuron"
            BiFunction<String, Integer, List<String>> idResolver,  // (sourceId, depth) -> [targetIds]
            String cascadeReasonPrefix) {  // e.g. "cascade.from.agent"

        public CascadeRule {
            Objects.requireNonNull(sourceType, "sourceType");
            Objects.requireNonNull(targetType, "targetType");
            Objects.requireNonNull(idResolver, "idResolver");
            Objects.requireNonNull(cascadeReasonPrefix, "cascadeReasonPrefix");
        }
    }

    private final TombstoneService delegate;
    private final int maxDepth;
    private final Map<String, List<CascadeRule>> rulesBySourceType = new ConcurrentHashMap<>();
    private final AtomicLong cascadeCount = new AtomicLong();
    private final AtomicLong cascadeSkipped = new AtomicLong();

    public CascadeTombstoneService(TombstoneService delegate) {
        this(delegate, DEFAULT_MAX_DEPTH);
    }

    public CascadeTombstoneService(TombstoneService delegate, int maxDepth) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be >= 1");
        this.maxDepth = maxDepth;
    }

    /** Register a cascade rule. */
    public CascadeTombstoneService register(CascadeRule rule) {
        rulesBySourceType.computeIfAbsent(rule.sourceType(), k -> new ArrayList<>())
                .add(rule);
        return this;
    }

    /** Number of registered rules. */
    public int ruleCount() {
        return rulesBySourceType.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Tombstone a resource and cascade the deletion to all dependents.
     * The original tombstone is recorded under {@code reason}; cascades
     * are recorded under {@code <prefix>.<sourceId>}.
     */
    public List<Tombstone> tombstoneAndCascade(String subjectId, String sourceType,
                                                String sourceId, String reason,
                                                String requesterId) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(requesterId, "requesterId");

        // Step 1: tombstone the source itself.
        Tombstone root = delegate.tombstone(subjectId, sourceType, sourceId,
                reason, "", requesterId);
        List<Tombstone> result = new ArrayList<>();
        result.add(root);

        // Step 2: cascade to dependents (depth-bounded).
        cascade(subjectId, sourceType, sourceId, reason, requesterId, 0, result);
        return result;
    }

    private void cascade(String subjectId, String sourceType, String sourceId,
                          String originalReason, String requesterId,
                          int depth, List<Tombstone> accumulator) {
        if (depth >= maxDepth) {
            cascadeSkipped.incrementAndGet();
            return;
        }
        List<CascadeRule> rules = rulesBySourceType.get(sourceType);
        if (rules == null || rules.isEmpty()) return;

        for (CascadeRule rule : rules) {
            List<String> targetIds;
            try {
                targetIds = rule.idResolver().apply(sourceId, depth);
            } catch (RuntimeException re) {
                log.warn("Cascade resolver for {}→{} threw: {}",
                        sourceType, rule.targetType(), re.getMessage());
                continue;
            }
            if (targetIds == null || targetIds.isEmpty()) continue;
            for (String targetId : targetIds) {
                // Skip if already tombstoned (idempotent).
                if (delegate.isTombstoned(rule.targetType(), targetId)) continue;
                String cascadeReason = rule.cascadeReasonPrefix()
                        + "." + sourceType + "." + sourceId;
                // Reuse the original root signature for provenance
                String sourceSig = accumulator.isEmpty() ? "" : accumulator.get(0).signature();
                Tombstone child = delegate.tombstone(subjectId, rule.targetType(),
                        targetId, cascadeReason, sourceSig, requesterId);
                accumulator.add(child);
                cascadeCount.incrementAndGet();
                // Recurse with the child as the new source.
                cascade(subjectId, rule.targetType(), targetId, originalReason,
                        requesterId, depth + 1, accumulator);
            }
        }
    }

    public TombstoneService delegate() { return delegate; }
    public long cascadeCount() { return cascadeCount.get(); }
    public long cascadeSkipped() { return cascadeSkipped.get(); }
    public Set<String> configuredSourceTypes() {
        return java.util.Set.copyOf(rulesBySourceType.keySet());
    }

    /** Builder convenience. */
    public static CascadeRule rule(String sourceType, String targetType,
                                     BiFunction<String, Integer, List<String>> idResolver,
                                     String reasonPrefix) {
        return new CascadeRule(sourceType, targetType, idResolver, reasonPrefix);
    }
}