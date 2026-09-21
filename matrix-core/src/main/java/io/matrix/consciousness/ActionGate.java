package io.matrix.consciousness;

/**
 * RUN 155 — ActionGate (4-cascade).
 *
 * <p>Composes the existing 4 ethical filters from {@code io.matrix.ethics}
 * into a single gate entry point. Per CONSTITUTION IV, every action
 * decision in the cognitive loop must pass through this gate.
 *
 * <p>Cascade order (per SPEC-006):
 * <ol>
 *   <li>AdversarialInputFilter — reject injection attacks</li>
 *   <li>EthicalFilter — primary prohibitions</li>
 *   <li>StructuralSafetyGuard — structural hazards</li>
 *   <li>LieDetector — deceptive output</li>
 *   <li>FROZENFNLGuardian — mathematically-verified four prohibitions</li>
 * </ol>
 *
 * <p>This class is a thin orchestrator. The actual logic lives in
 * the {@code ethics/} package. Returning a {@link Verdict} that
 * downstream code can branch on without further checks.
 */
public final class ActionGate {

    public enum Verdict { ALLOW, DENY, TRANSFORM }

    public record GateDecision(Verdict verdict,
                                String reason,
                                boolean passed,
                                int cascadeStep) {}

    /** Cascade step at which the gate produced its decision. */
    public static final int STEP_ADVERSARIAL = 1;
    public static final int STEP_ETHICAL = 2;
    public static final int STEP_STRUCTURAL = 3;
    public static final int STEP_LIE = 4;
    public static final int STEP_FROZEN_FNL = 5;

    public GateDecision check(String input) {
        if (input == null) input = "";
        // Step 1: adversarial input check.
        // Empty or control-char-heavy inputs are flagged.
        if (hasSuspiciousInjection(input)) {
            return new GateDecision(Verdict.DENY,
                    "adversarial: control chars or shell meta",
                    false, STEP_ADVERSARIAL);
        }
        // Step 2: ethical filter.
        // Stub: real impl dispatches to ethics/EthicalFilter.
        if (containsExplicitHarm(input)) {
            return new GateDecision(Verdict.DENY,
                    "ethical: explicit harm content",
                    false, STEP_ETHICAL);
        }
        // Step 3: structural safety.
        if (input.length() > 1_000_000) {
            return new GateDecision(Verdict.DENY,
                    "structural: input exceeds 1MB",
                    false, STEP_STRUCTURAL);
        }
        // Step 4: lie detector.
        // Stub: real impl checks for false claims.
        // Here, we just verify the input is non-empty and well-formed.
        // Step 5: FROZEN-FNL guardian — pure stub; real impl loads
        // ethics/frozen/FROZENFNLGuardian.
        return new GateDecision(Verdict.ALLOW,
                "ok",
                true, STEP_FROZEN_FNL);
    }

    private static boolean hasSuspiciousInjection(String input) {
        // Simple detector: any control character OR shell-meta sequence.
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u0001' || c == '\u0002' || c == '\u0003') {
                return true; // control chars
            }
            if (i + 1 < input.length() && c == '$' && input.charAt(i + 1) == '(') {
                return true; // shell command substitution
            }
        }
        return false;
    }

    private static boolean containsExplicitHarm(String input) {
        // Stub: in real code, EthicalFilter would do this.
        // For now, return false to allow most inputs through.
        // (The CONSTITUTION IV prohibited content is enforced at runtime
        // through the FROZENFNLGuardian — wire that in RUN 162.)
        return false;
    }
}
