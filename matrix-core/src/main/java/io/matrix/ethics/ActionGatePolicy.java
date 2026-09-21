package io.matrix.ethics;

import io.matrix.consciousness.ActionGate;

/**
 * RUN 225 — ActionGatePolicy (simplified 4-cascade).
 *
 * <p>Provides a static-factory wrapper that runs the 4-cascade
 * ActionGate checks against a given input. Useful as a stand-alone
 * gate enforcement entry point.
 */
public final class ActionGatePolicy {

    public enum CascadeStep { ADVERSARIAL, ETHICAL, STRUCTURAL, LIE, FROZEN }

    public record CascadeVerdict(ActionGate.GateDecision decision,
                                 CascadeStep step) {}

    public static CascadeVerdict check(String input, ActionGate gate) {
        ActionGate.GateDecision d = gate.check(input);
        CascadeStep step = switch (d.cascadeStep()) {
            case ActionGate.STEP_ADVERSARIAL -> CascadeStep.ADVERSARIAL;
            case ActionGate.STEP_ETHICAL -> CascadeStep.ETHICAL;
            case ActionGate.STEP_STRUCTURAL -> CascadeStep.STRUCTURAL;
            case ActionGate.STEP_LIE -> CascadeStep.LIE;
            case ActionGate.STEP_FROZEN_FNL -> CascadeStep.FROZEN;
            default -> CascadeStep.FROZEN;
        };
        return new CascadeVerdict(d, step);
    }
}
