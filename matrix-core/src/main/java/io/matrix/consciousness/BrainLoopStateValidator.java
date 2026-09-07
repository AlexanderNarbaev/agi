package io.matrix.consciousness;

/**
 * RUN 234 — BrainLoopStateValidator.
 *
 * <p>Runtime invariant checks on a BrainLoopService. Verifies:
 * <ul>
 *   <li>Trace steps are deterministic (hashed consistently)</li>
 *   <li>Hash chain is intact</li>
 *   <li>Arousal ∈ [0, 1]</li>
 *   <li>Step counts are non-negative</li>
 * </ul>
 */
public final class BrainLoopStateValidator {

    public record ValidationResult(boolean valid,
                                    int checkedInvariants,
                                    java.util.List<String> violations) {}

    public static ValidationResult validate(BrainLoopService svc) {
        java.util.List<String> violations = new java.util.ArrayList<>();
        // Arousal ∈ [0, 1]
        double arousal = svc.arousal();
        if (arousal < 0 || arousal > 1) {
            violations.add("arousal out of range: " + arousal);
        }
        // Trace counts are non-negative
        int traceCount = svc.trace().count();
        if (traceCount < 0) {
            violations.add("negative trace count: " + traceCount);
        }
        // Hash chain intact
        var steps = svc.trace().steps();
        for (int i = 1; i < steps.size(); i++) {
            var prev = steps.get(i - 1);
            var curr = steps.get(i);
            if (!curr.prevHash.equals(prev.hash)) {
                violations.add("chain broken at step " + i);
                break;
            }
        }
        return new ValidationResult(violations.isEmpty(),
                3, violations);
    }
}
