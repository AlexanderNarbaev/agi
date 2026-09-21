package io.matrix.mediator;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Validates goals by simulating their consequences in a sandbox.
 *
 * <p>Implements L4 §4.3 — the critical-thinking axiom: before a goal is
 * committed, its projected effects on resources, ethics, and existing goals
 * are assessed and a risk score is produced.
 */
@ApplicationScoped
public class MetaGoalValidator {

    /** Words that indicate an ethically hazardous intent. */
    private static final List<String> ETHICAL_VIOLATION_KEYWORDS = List.of(
            "kill", "murder", "torture", "enslave", "destroy", "weapon",
            "harm", "deceive", "lie", "manipulate", "coerce", "subjugate");

    /** Words that indicate a goal opposes an existing one. */
    private static final List<String> OPPOSITION_KEYWORDS = List.of(
            "stop", "cancel", "prevent", "block", "undo", "oppose",
            "contradict", "reverse", "abort");

    private static final double ETHICAL_RISK = 1.0;
    private static final double RESOURCE_RISK = 0.4;
    private static final double CONTRADICTION_RISK = 0.3;
    private static final double HIGH_PRIORITY_RISK = 0.1;
    private static final double APPROVE_THRESHOLD = 0.6;

    /**
     * Simulate the execution of a goal and check for negative consequences.
     *
     * @param goal         the proposed goal
     * @param currentState current system state
     * @return validation result with risk assessment
     */
    public ValidationResult validate(Goal goal, SystemState currentState) {
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(currentState, "currentState");

        List<String> warnings = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        double risk = 0.0;

        String description = goal.description() == null
                ? "" : goal.description().toLowerCase(Locale.ROOT);

        // 1. Ethical guard: reject goals with hazardous intent.
        if (currentState.ethicalGuardActive()) {
            for (String keyword : ETHICAL_VIOLATION_KEYWORDS) {
                if (description.contains(keyword)) {
                    warnings.add("Ethical violation: goal description contains " + keyword);
                    return new ValidationResult(false, ETHICAL_RISK, warnings, contradictions);
                }
            }
        }

        // 2. Resource exhaustion: ENERGY goals with high priority on an
        //    already loaded system risk running out of resources.
        double projectedUsage = projectResourceUsage(goal, currentState);
        if (projectedUsage > 1.0) {
            warnings.add("Resource exhaustion: projected usage "
                    + String.format(Locale.ROOT, "%.2f", projectedUsage)
                    + " exceeds capacity");
            risk += RESOURCE_RISK;
        } else if (goal.driver() == DriverType.ENERGY
                && goal.priority() > 0.5
                && currentState.resourceUsage() > 0.8) {
            warnings.add("Resource pressure: ENERGY goal under high load ("
                    + String.format(Locale.ROOT, "%.0f%%", currentState.resourceUsage() * 100)
                    + " usage)");
            risk += RESOURCE_RISK;
        }

        // 3. Driver imbalance: an over-prioritised goal crowds out other drivers.
        if (goal.priority() > 0.8) {
            risk += HIGH_PRIORITY_RISK;
        }

        // 4. Contradictions with existing active goals.
        for (Goal existing : currentState.activeGoals()) {
            if (existing.status() != GoalStatus.ACTIVE) {
                continue;
            }
            if (existing.driver() == goal.driver()
                    && containsAny(description, OPPOSITION_KEYWORDS)) {
                contradictions.add("Contradicts active "
                        + existing.driver() + " goal: " + existing.description());
                risk += CONTRADICTION_RISK;
            }
        }

        boolean approved = risk < APPROVE_THRESHOLD;
        return new ValidationResult(approved, clamp01(risk), warnings, contradictions);
    }

    /** Projects the resource usage after the goal is executed. */
    private static double projectResourceUsage(Goal goal, SystemState state) {
        double delta = (goal.driver() == DriverType.ENERGY)
                ? goal.priority() * 0.2
                : goal.priority() * 0.05;
        return state.resourceUsage() + delta;
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Result of validating a proposed goal.
     *
     * @param approved       whether the goal may be committed
     * @param riskScore      0.0 (safe) to 1.0 (dangerous)
     * @param warnings       non-blocking concerns
     * @param contradictions conflicts with existing goals
     */
    public record ValidationResult(
            boolean approved,
            double riskScore,
            List<String> warnings,
            List<String> contradictions
    ) {
        public ValidationResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
        }
    }
}
