package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.List;

/**
 * MIND-W1 — Stage 8: TSETLIN small-footprint classifier.
 *
 * <p>Lightweight classifier over learned boolean clauses. For MIND-W1 the
 * clause base is hand-encoded; W3 sleep-cycle will populate it via
 * induction from episodic memory.</p>
 */
public final class TsetlinStage {

    /**
     * RECON-W1 — D-3 truthfulness guard.
     *
     * <p>The legacy hardcoded clauses (chitchat/acknowledge/meta) are
     * NOT real Tsetlin predictions; they are string-matching canned
     * replies. The default production path returns {@link TsetlinResult#miss()}
     * and marks the stage as a SIMULACRUM. Real Tsetlin training is
     * wired in RECON-W3 (sleep-driven rule induction).</p>
     */
    public static boolean simulacrumEnabled = false;

    /** Clause predicates: matched = positive feedback, fallback reply. */
    private static final List<TsetlinClause> CLAUSES = List.of(
        new TsetlinClause("chitchat",
            in -> isGreeting(in) || in.toLowerCase().matches(".*\\b(how are you|what's up)\\b.*"),
            "I'm operational. How may I assist?"
        ),
        new TsetlinClause("acknowledge",
            in -> in.toLowerCase().matches(".*\\b(thank you|thanks|ty|appreciate)\\b.*"),
            "You're welcome."
        ),
        new TsetlinClause("meta",
            in -> in.toLowerCase().matches(".*\\b(how do you work|how are you built|architecture)\\b.*"),
            "I'm a hybrid: REFLEX → SIGNAL → SALIENCE → ARITHMETIC → ANALOGY → BIR → HDC → TSETLIN → MCTS → MODULATORS. Every answer carries a BRC trace."
        )
    );

    public record TsetlinClause(String id, java.util.function.Predicate<String> match, String reply) {}

    public record TsetlinResult(boolean matched, String reply, double confidence) {
        public static TsetlinResult miss() {
            return new TsetlinResult(false, "", 0.40);
        }
        public static TsetlinResult hit(String reply, double confidence) {
            return new TsetlinResult(true, reply, confidence);
        }
    }

    public TsetlinResult classify(String input, List<BrcStep> trace) {
        if (!simulacrumEnabled) {
            // SIMULACRUM: real Tsetlin inference is wired in RECON-W3.
            if (trace != null) {
                trace.add(BrcStep.of("TSETLIN_SIMULACRUM", false, 0.0,
                    List.of("simulacrum=true",
                            "note=D-3 real Tsetlin inference deferred to RECON-W3")));
            }
            return TsetlinResult.miss();
        }
        // Legacy demo path (simulacrumEnabled=true):
        for (TsetlinClause c : CLAUSES) {
            try {
                if (c.match.test(input)) {
                    trace.add(BrcStep.of("TSETLIN", true, 0.75,
                        List.of("clause=" + c.id)));
                    return TsetlinResult.hit(c.reply, 0.75);
                }
            } catch (RuntimeException ignore) {
                // never abort the mind
            }
        }
        trace.add(BrcStep.of("TSETLIN", false, 0.40,
            List.of("clauses_evaluated=" + CLAUSES.size(), "hit=0")));
        return TsetlinResult.miss();
    }

    private static boolean isGreeting(String in) {
        String l = in.toLowerCase().trim();
        return l.equals("hello") || l.equals("hi") || l.startsWith("hello ")
            || l.startsWith("hi ") || l.startsWith("hey ")
            || l.contains("good morning") || l.contains("good afternoon")
            || l.contains("good evening");
    }
}
