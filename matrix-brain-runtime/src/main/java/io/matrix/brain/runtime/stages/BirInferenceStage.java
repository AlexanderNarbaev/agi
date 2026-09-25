package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.List;

/**
 * MIND-W1 — Stage 6: BIR rule inference.
 *
 * <p>Looks up Boolean Inference Rule patterns in the local rule base.
 * For MIND-W1 the rule base is a tiny in-memory set; W2 wires this to
 * the persisted BIR store.</p>
 */
public final class BirInferenceStage {

    /** Hand-encoded rules for primitive compositions. */
    private static final List<BirRule> RULES = List.of(
        new BirRule("greets",
            in -> List.of("hello", "hi", "hey", "good morning", "good evening").stream()
                .anyMatch(g -> in.toLowerCase().contains(g)),
            "Hello. I am MATRIX — a hybrid neuro-symbolic mind."
        ),
        new BirRule("time",
            in -> in.toLowerCase().matches(".*\\b(what time|current time|what.*time is it)\\b.*"),
            "Time is structural to me: every cycle has a duration_ms, but I have no clock in the decision path (CONSTITUTION Article I)."
        ),
        new BirRule("name",
            in -> in.toLowerCase().matches(".*\\b(what is your name|who are you|your name)\\b.*"),
            "I am MATRIX — a hybrid neuro-symbolic mind."
        ),
        new BirRule("capabilities",
            in -> in.toLowerCase().matches(".*\\b(what can you do|capabilities|features)\\b.*"),
            "I think via a 9-stage cognitive cycle: REFLEX → SIGNAL → SALIENCE → ARITHMETIC → ANALOGY → BIR → HDC → TSETLIN → MCTS → MODULATORS. Every answer carries a verifiable BRC trace."
        ),
        new BirRule("math-neg",
            in -> {
                String l = in.toLowerCase();
                if (!l.contains("not ") && !l.contains("negative")) return false;
                return l.matches(".*\\b(not |negative )?\\d+\\s*([+\\-*/])\\s*\\d+\\b.*")
                    && l.contains("not ") || l.contains("negative");
            },
            "I evaluate arithmetic symbolically with BigInteger; sign is preserved."
        )
    );

    public record BirRule(String id, java.util.function.Predicate<String> match, String reply) {}

    public record BirResult(boolean matched, String reply, double confidence) {
        public static BirResult miss() {
            return new BirResult(false, "", 0.0);
        }
        public static BirResult hit(String reply, double confidence) {
            return new BirResult(true, reply, confidence);
        }
    }

    public BirResult evaluate(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        for (BirRule r : RULES) {
            try {
                if (r.match.test(input)) {
                    trace.add(BrcStep.of("BIR_RULES", true, 0.90,
                        List.of("rule=" + r.id)));
                    return BirResult.hit(r.reply, 0.90);
                }
            } catch (RuntimeException ignore) {
                // rule failure must not abort mind
            }
        }
        trace.add(BrcStep.of("BIR_RULES", false, 0.50,
            List.of("rules_evaluated=" + RULES.size(), "hit=0")));
        return BirResult.miss();
    }
}
