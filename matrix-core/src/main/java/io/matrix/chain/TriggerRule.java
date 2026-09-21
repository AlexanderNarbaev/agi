package io.matrix.chain;

import io.matrix.imports.ChainEnrichedOutput;

import java.util.Objects;

/**
 * DESIGN-21 — TriggerRule: predicate + target ChainId + priority.
 *
 * <p>Predicate is a pure function of {@link ChainEnrichedOutput}
 * (CONSTITUTION I compliant — no Random, no wall-clock).
 *
 * <p>When the predicate evaluates to true on a chain's enriched output,
 * the target chain becomes a candidate for activation. Multiple
 * triggers resolve by priority (descending).
 */
public record TriggerRule(
        TriggerPredicate predicate,
        ChainId target,
        int priority,
        String rationale
) {
    public TriggerRule {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(target, "target");
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }

    /** Factory helper. */
    public static TriggerRule of(TriggerPredicate predicate, ChainId target,
                                 int priority, String rationale) {
        return new TriggerRule(predicate, target, priority, rationale);
    }

    /** FROZEN-shutoff default rule (priority 1000, mandatory). */
    public static TriggerRule frozenShutoffDefault(ChainId frozenChainId) {
        return new TriggerRule(
                output -> false,  // placeholder; actual FROZEN check wired in BrainLoopService
                frozenChainId,
                1000,
                "FROZEN-gate violation → immediate shutoff (priority ≥ 1000)"
        );
    }
}
