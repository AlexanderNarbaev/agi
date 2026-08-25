package io.matrix.lifecycle;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FnlGate — two-stage quarantine gate for freshly born elements
 * (DESIGN-12 §2; refines the single-step Φ-validation of CauldronProtocol).
 *
 * <p>Progression: {@code SHADOW → CANDIDATE → PROMOTED}, with {@code DEMOTED}
 * as the absorbing failure state. Each advance requires a shadow-run score
 * at or above an explicitly supplied threshold; thresholds are chosen by the
 * caller per stage, keeping the gate itself policy-free and deterministic.
 *
 * <p>Deterministic: no randomness, no wall-clock — scores come from the
 * caller's measurement window.
 */
public final class FnlGate {

    /** Gate states of the FNL quarantine ladder. */
    public enum GateState { SHADOW, CANDIDATE, PROMOTED, DEMOTED }

    private final Map<String, GateState> states = new ConcurrentHashMap<>();

    /** Admits an element into the shadow stage. */
    public void admit(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
        states.put(elementId, GateState.SHADOW);
    }

    /**
     * Attempts to advance one stage using the measured score.
     *
     * @param elementId admitted element
     * @param score     measured shadow/candidate score
     * @param threshold minimum score required for this stage transition
     * @return new state; {@code PROMOTED}/{@code DEMOTED} are terminal
     */
    public GateState advance(String elementId, double score, double threshold) {
        GateState current = states.get(elementId);
        if (current == null) {
            throw new IllegalStateException("element not admitted: " + elementId);
        }
        GateState next = switch (current) {
            case SHADOW -> score >= threshold ? GateState.CANDIDATE : GateState.DEMOTED;
            case CANDIDATE -> score >= threshold ? GateState.PROMOTED : GateState.DEMOTED;
            case PROMOTED, DEMOTED -> current;
        };
        states.put(elementId, next);
        return next;
    }

    /** Current gate state, if admitted. */
    public Optional<GateState> state(String elementId) {
        return Optional.ofNullable(states.get(elementId));
    }
}
