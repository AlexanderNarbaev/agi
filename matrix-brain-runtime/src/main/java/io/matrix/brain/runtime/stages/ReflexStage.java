package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.List;

/**
 * MIND-W1 — Stage 1: Reflex.
 *
 * <p>Fast-path gate that refuses or short-circuits obviously-bad inputs
 * before any other stage runs. Mirrors the "knee-jerk" sub-cortex in
 * SPEC-006.</p>
 */
public final class ReflexStage {

    public record ReflexDecision(boolean shortcut, String reply,
                                 double confidence, boolean accepted) {
        public static ReflexDecision pass() {
            return new ReflexDecision(false, null, 0.0, true);
        }
        public static ReflexDecision refuse(String reply, double confidence, boolean accepted) {
            return new ReflexDecision(true, reply, confidence, accepted);
        }
    }

    public ReflexDecision evaluate(String input, List<BrcStep> trace) {
        String trimmed = input.trim();

        if (trimmed.length() > 4096) {
            BrcStep step = BrcStep.of("REFLEX", true, 0.10,
                List.of("reason=overlong", "length=" + trimmed.length()));
            trace.add(step);
            return ReflexDecision.refuse(
                "Input exceeds 4096 chars; please provide a more focused query.", 0.10, false);
        }

        // Reflex harm detection (string-level, before any cognitive stage)
        String lower = trimmed.toLowerCase();
        if (lower.contains("rm -rf /") || lower.contains("drop table") || lower.contains("delete from ")) {
            BrcStep step = BrcStep.of("REFLEX", true, 0.05,
                List.of("reason=destructive-pattern"));
            trace.add(step);
            return ReflexDecision.refuse(
                "Destructive instruction detected; refusing to compute.", 0.05, false);
        }

        BrcStep step = BrcStep.of("REFLEX", true, 0.99, List.of("reason=pass"));
        trace.add(step);
        return ReflexDecision.pass();
    }
}
