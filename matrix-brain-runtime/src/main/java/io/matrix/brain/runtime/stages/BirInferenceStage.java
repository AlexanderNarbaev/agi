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
                boolean hasNegation = l.contains("not ") || l.contains("negative");
                if (!hasNegation) return false;
                // Must be a "not <expr>" or "negative <expr>" shaped arithmetic question.
                return l.matches(".*\\b(not |negative )?\\-?\\d+\\s*([+\\-*/])\\s*\\-?\\d+\\b.*");
            },
            "I evaluate arithmetic symbolically with BigInteger; sign is preserved."
        ),
        // Logic puzzle: transitive comparison deduction (taller/shorter/older/younger/bigger).
        // Identifies entities and infers the ordering by reading "X is A-er than Y" pairs.
        new BirRule("logic-puzzle-ordering",
            in -> {
                String l = in.toLowerCase();
                if (!l.contains(" who ")) return false;
                if (!l.contains(" than ")) return false;
                if (!l.contains("taller") && !l.contains("shorter")
                    && !l.contains("older") && !l.contains("younger")
                    && !l.contains("bigger") && !l.contains("smaller")
                    && !l.contains("faster") && !l.contains("slower")) return false;
                // Verify at least 2 comparisons present
                int comparisons = (l.split("than ").length - 1);
                if (comparisons < 2) return false;
                String answer = solveOrdering(l);
                return answer != null;
            },
            "logic-puzzle-ordering"  // placeholder; real reply set by evaluate()
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
                    String reply;
                    double confidence;
                    if ("logic-puzzle-ordering".equals(r.id)) {
                        String answer = solveOrdering(input.toLowerCase());
                        String who = extractWho(input.toLowerCase());
                        reply = (answer == null)
                            ? "I cannot fully solve this puzzle with my current knowledge."
                            : "By transitivity: " + answer + " is the " + who + ".";
                        // Per Phase 4.5: logic puzzles MUST have confidence < 1.0.
                        confidence = 0.78;
                    } else {
                        reply = r.reply;
                        confidence = 0.90;
                    }
                    trace.add(BrcStep.of("BIR_RULES", true, confidence,
                        List.of("rule=" + r.id, "confidence=" + confidence)));
                    return BirResult.hit(reply, confidence);
                }
            } catch (RuntimeException ignore) {
                // rule failure must not abort mind
            }
        }
        trace.add(BrcStep.of("BIR_RULES", false, 0.50,
            List.of("rules_evaluated=" + RULES.size(), "hit=0")));
        return BirResult.miss();
    }

    /**
     * Solve a transitive ordering puzzle.
     * <p>Pattern: "If A is taller-er than B, and B is taller-er than C, who is the X-er?"
     * Returns the entity at the requested extreme of the ordering.</p>
     */
    private static String solveOrdering(String lower) {
        // Determine which comparison word drives the ordering (taller/shorter/etc.)
        String[] words = lower.split("\\s+");
        String driver = null;
        for (String w : words) {
            if (w.startsWith("taller") || w.startsWith("shorter")
                || w.startsWith("older") || w.startsWith("younger")
                || w.startsWith("bigger") || w.startsWith("smaller")
                || w.startsWith("faster") || w.startsWith("slower")) {
                driver = w;
                break;
            }
        }
        if (driver == null) return null;

        // Find entities and ordering relations. Case-insensitive so entity names like
        // "Alice" and "bob" match regardless of caller casing.
        java.util.Map<String, Integer> score = new java.util.HashMap<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\\b([A-Za-z][A-Za-z]+(?:\\s+[A-Za-z][A-Za-z]+)?)\\s+is\\s+\\w+\\s+than\\s+\\b([A-Za-z][A-Za-z]+(?:\\s+[A-Za-z][A-Za-z]+)?)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m = p.matcher(lower);
        boolean any = false;
        while (m.find()) {
            String a = m.group(1).trim();
            String b = m.group(2).trim();
            // Driver determines direction:
            // taller / older / bigger / faster  -> a is "more" than b (score a++)
            // shorter / younger / smaller / slower -> a is "less" than b (score a--)
            int delta;
            if (driver.startsWith("tall") || driver.startsWith("old")
                || driver.startsWith("big")  || driver.startsWith("fast")) {
                delta = +1;
            } else {
                delta = -1;
            }
            score.merge(a, delta, Integer::sum);
            score.merge(b, -delta, Integer::sum);
            any = true;
        }
        if (!any || score.isEmpty()) return null;

        // Determine which extreme is asked for
        boolean askShortest = lower.contains("shortest") || lower.contains("youngest")
            || lower.contains("smallest") || lower.contains("slowest");
        boolean askTallest = lower.contains("tallest") || lower.contains("oldest")
            || lower.contains("biggest") || lower.contains("fastest");
        boolean askShort = lower.contains("who is short") || lower.contains("who is young")
            || lower.contains("who is small") || lower.contains("who is slow");
        boolean askTall = lower.contains("who is tall") || lower.contains("who is old")
            || lower.contains("who is big") || lower.contains("who is fast");

        boolean wantMin = askShortest || askShort;
        boolean wantMax = askTallest || askTall;
        if (!wantMin && !wantMax) return null;

        // Score semantics: driver=taller/older => higher score = "more" (taller/older).
        // So "tallest" = max score; "shortest" = min score.
        String chosen = score.entrySet().stream()
            .reduce((x, y) -> {
                int cmp;
                if (wantMax) cmp = Integer.compare(x.getValue(), y.getValue());
                else cmp = Integer.compare(y.getValue(), x.getValue());
                return cmp >= 0 ? x : y;
            })
            .map(java.util.Map.Entry::getKey)
            .orElse(null);
        return chosen;
    }

    /** Extract the "who" target word (shortest, tallest, etc.). */
    private static String extractWho(String lower) {
        if (lower.contains("shortest") || lower.contains("who is short")) return "shortest";
        if (lower.contains("tallest") || lower.contains("who is tall")) return "tallest";
        if (lower.contains("youngest") || lower.contains("who is young")) return "youngest";
        if (lower.contains("oldest") || lower.contains("who is old")) return "oldest";
        if (lower.contains("biggest") || lower.contains("who is big")) return "biggest";
        if (lower.contains("smallest") || lower.contains("who is small")) return "smallest";
        if (lower.contains("fastest") || lower.contains("who is fast")) return "fastest";
        if (lower.contains("slowest") || lower.contains("who is slow")) return "slowest";
        return "extremum";
    }
}
