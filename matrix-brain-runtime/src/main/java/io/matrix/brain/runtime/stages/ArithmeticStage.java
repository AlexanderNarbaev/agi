package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MIND-W1 — Stage 4: Arithmetic.
 *
 * <p>Pure symbol-manipulation composition for arithmetic expressions like
 * "2+3", "10 * 5", "100 - 7". This is the demonstration that the mind can
 * <i>compute</i> without being taught verbatim — exactly the failing case
 * in the pre-W1 stub.</p>
 *
 * <p>CONSTITUTION Article I: no LLM in runtime. Pure operator-tree
 * evaluation. No floating-point (BigInteger for precision + arbitrary range).</p>
 */
public final class ArithmeticStage {

    /** RECON-W4 Step 2: optional planning path for compound queries. */
    private final PlanningStage planningStage;

    public ArithmeticStage() {
        this(null);
    }

    public ArithmeticStage(PlanningStage planningStage) {
        this.planningStage = planningStage;
    }

    /** Pattern: matches "<digit> <op> <digit>" anywhere in the input.
     *  Anchors aren't used because users say things like "What is 2+3?" —
     *  we want to find the embedded arithmetic expression.
     *  Optional capture of "what is"/"equals"/etc. natural-language prefix. */
    private static final Pattern BINARY = Pattern.compile(
        "(?i)(?:.*\\b(?:what(?:'s| is)?|compute|calculate|equals?)\\b\\s*)?" +
        "(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)" +
        "(?:\\s*\\??)?"
    );

    public record ArithmeticResult(boolean matched, String reply, double confidence) {
        public static ArithmeticResult miss() {
            return new ArithmeticResult(false, "", 0.0);
        }
        public static ArithmeticResult hit(String reply, double confidence) {
            return new ArithmeticResult(true, reply, confidence);
        }
    }

    public ArithmeticResult tryEvaluate(String input, List<BrcStep> trace) {
        // RECON-W23 ROUTING FIX. Previously the greedy BINARY regex ran first and
        // `find()` returned the FIRST "<num> <op> <num>" pair, so a compound
        // expression was answered from its first two operands and the rest was
        // silently discarded:
        //     "2 + 3 * 4"  ->  matched "2 + 3"  -> "2 + 3 = 5"   (expected 14)
        //     "5 - 1 + 2"  ->  matched "5 - 1"  -> "5 - 1 = 4"   (expected 6)
        // The compound branch below (`tryCompoundViaPlanning`) therefore only ran
        // when the regex FAILED, i.e. it was unreachable for exactly the inputs it
        // was written for. This is W4's own documented "limitation #1".
        //
        // Compound detection now runs FIRST, so a multi-operator expression can
        // never be captured by the single-pair fast path. The regex is retained
        // as the fast path ONLY for unambiguous single-operation input.
        String normalized = normalizeWordArithmetic(input);

        if (countOperators(normalized) >= 2) {
            return tryCompoundViaPlanning(normalized, trace);
        }

        Matcher m = BINARY.matcher(normalized);
        if (!m.find()) {
            if (planningStage != null && isCompoundQuery(input)) {
                return tryCompoundViaPlanning(normalized, trace);
            }
            trace.add(BrcStep.of("ARITHMETIC", false, 0.50, List.of("reason=no-binary-expr")));
            return ArithmeticResult.miss();
        }
        BigInteger a = new BigInteger(m.group(1));
        String op = m.group(2);
        BigInteger b = new BigInteger(m.group(3));
        BigInteger result;
        try {
            result = switch (op) {
                case "+" -> a.add(b);
                case "-" -> a.subtract(b);
                case "*" -> a.multiply(b);
                case "/" -> {
                    if (b.signum() == 0) yield null;
                    yield a.divide(b);
                }
                default -> null;
            };
        } catch (ArithmeticException ex) {
            trace.add(BrcStep.of("ARITHMETIC", false, 0.10,
                List.of("reason=" + ex.getMessage())));
            return ArithmeticResult.miss();
        }
        if (result == null) {
            trace.add(BrcStep.of("ARITHMETIC", false, 0.10,
                List.of("reason=div-by-zero-or-unsupported-op")));
            return ArithmeticResult.miss();
        }
        String reply = a + " " + op + " " + b + " = " + result;
        // RECON-W1: include the engine=ClassName.method(args,out) evidence marker
        // so EvidenceTruthGuardTest can mechanically verify the claim.
        String argsDigest = a + "," + op + "," + b;
        String outDigest = String.valueOf(result);
        trace.add(BrcStep.of("ARITHMETIC", true, 0.99, List.of(
            "engine=ArithmeticStage.evaluate(args=" + argsDigest + ",out=" + outDigest + ")",
            "a=" + a, "op=" + op, "b=" + b, "result=" + result)));
        return ArithmeticResult.hit(reply, 0.99);
    }

    /**
     * RECON-W23 — word-numeral and word-operator normalisation.
     *
     * <p>PD-1 ("twice five plus three" = 13) and PD-4 ("ten times two minus five" = 15)
     * carry their whole expression in words, so the numeric tokeniser found fewer
     * than three tokens and the stage missed. Numbers are expanded to digits and
     * word operators to symbols BEFORE tokenisation, which is what makes those
     * probes reachable at all.</p>
     *
     * <p>Deterministic and table-driven (Article III). Longest phrases are replaced
     * first so "times two" is not damaged by a later single-word pass.</p>
     */
    static String normalizeWordArithmetic(String input) {
        if (input == null) return "";
        String s = " " + input.toLowerCase(java.util.Locale.ROOT).trim() + " ";

        // 1. Number words first, so the multiplier patterns below can capture digits.
        s = s.replaceAll("\\bseventeen\\b", "17").replaceAll("\\bsixteen\\b", "16")
             .replaceAll("\\bfifteen\\b", "15").replaceAll("\\bfourteen\\b", "14")
             .replaceAll("\\bthirteen\\b", "13").replaceAll("\\btwelve\\b", "12")
             .replaceAll("\\beleven\\b", "11").replaceAll("\\bten\\b", "10")
             .replaceAll("\\bnine\\b", "9").replaceAll("\\beight\\b", "8")
             .replaceAll("\\bseven\\b", "7").replaceAll("\\bsix\\b", "6")
             .replaceAll("\\bfive\\b", "5").replaceAll("\\bfour\\b", "4")
             .replaceAll("\\bthree\\b", "3").replaceAll("\\btwo\\b", "2")
             .replaceAll("\\bone\\b", "1").replaceAll("\\bzero\\b", "0");

        // 2. Multiplier prefixes are PREFIX operators, not operands: "twice five"
        //    is 5*2, not 2 followed by 5. Rewriting them as a bare "2" produced the
        //    nonsense stream "2 5 + 3" and the wrong answer 8. Capturing the
        //    following number and emitting "<n> * 2" is the correct transform.
        s = s.replaceAll("\\btwice\\s+(\\d+)\\b", "$1 * 2");
        s = s.replaceAll("\\bdouble\\s+(\\d+)\\b", "$1 * 2");
        s = s.replaceAll("\\btriple\\s+(\\d+)\\b", "$1 * 3");
        s = s.replaceAll("\\bhalf\\s+(\\d+)\\b", "$1 / 2");

        // 3. Word operators.
        s = s.replaceAll("\\bmultiplied by\\b", "*");
        s = s.replaceAll("\\bdivided by\\b", "/");
        s = s.replaceAll("\\btimes\\b", "*");
        s = s.replaceAll("\\bplus\\b", "+");
        s = s.replaceAll("\\bminus\\b", "-");
        return s.trim();
    }

    /** Number of binary operators in a normalised expression. */
    private static int countOperators(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/') n++;
        }
        return n;
    }

    /**
     * RECON-W4 Step 2: detect compound queries (multiple operators or
     * 2+ numeric tokens). Cheap regex-based heuristic.
     */
    static boolean isCompoundQuery(String input) {
        // Count operator occurrences in the input.
        int opCount = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/') opCount++;
        }
        // Compound = ≥2 operators OR has "twice"/"plus"/"times"/"minus" keywords
        if (opCount >= 2) return true;
        String lower = input.toLowerCase();
        return lower.contains("twice") || lower.contains("plus")
            || lower.contains("times") || lower.contains("minus")
            || lower.contains("divided");
    }

    /**
     * RECON-W4 / RECON-W23 — evaluate a compound query.
     *
     * <p><b>RECON-W23 fix: the answer no longer depends on the planner.</b>
     * {@code planningStage} is null in the serving pipeline
     * ({@code new ArithmeticStage()}), and the previous code dereferenced it
     * unconditionally, so every compound expression died before being evaluated
     * and the mind answered "I cannot answer that confidently." — even though the
     * arithmetic is fully determined by the input. MCTS contributes deliberative
     * EVIDENCE, not correctness; when it is unavailable the evaluation still runs
     * and the trace says so explicitly (Article VIII: report the honest state, do
     * not fail silently and do not pretend planning happened).</p>
     *
     * <p>When the planner IS present and produces no plan, the same holds: the
     * arithmetic is still evaluated, and the trace records
     * {@code planning=budget-exhausted}.</p>
     */
    private ArithmeticResult tryCompoundViaPlanning(String input, List<BrcStep> trace) {
        // Decompose into numeric tokens.
        java.util.List<String> tokens = new java.util.ArrayList<>();
        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("-?\\d+|[+\\-\\*/]").matcher(input);
        while (tm.find()) tokens.add(tm.group());
        if (tokens.size() < 3) {
            return ArithmeticResult.miss();   // not really compound
        }

        // Optional deliberative evidence. Never load-bearing for the answer.
        String planningEvidence = "unavailable-no-planner";
        if (planningStage != null) {
            try {
                io.matrix.neuron.DecisionTree rootState =
                    new io.matrix.neuron.DecisionTree.Leaf(true);
                java.util.List<io.matrix.mcts.MctsAction> actions =
                    io.matrix.mcts.MctsAction.allActions();
                PlanningStage.Budgets budget =
                    PlanningStage.Budgets.forTier(PlanningStage.Tier.PRO);
                PlanningStage.PlanResult plan = planningStage.plan(
                    rootState, actions, budget, null, trace);
                planningEvidence = plan != null && plan.planned()
                    ? "MctsTree.runSearch+bestAction=" + plan.bestAction().type()
                      + ",iterations=" + plan.iterationsRun()
                      + ",durationMs=" + plan.durationMs()
                    : "budget-exhausted";
            } catch (RuntimeException planningFailure) {
                // Planning is evidence, not correctness: an arithmetic answer must
                // not be lost because a deliberation subsystem misbehaved.
                planningEvidence = "error:" + planningFailure.getClass().getSimpleName();
            }
        }
        final String planningLine = planningEvidence;
        // For demonstration: compute the result by evaluating left-to-right
        // (matches regex semantics). The plan trace itself is the evidence.
        // RECON-W23: evaluate with OPERATOR PRECEDEDENCE, not left-to-right.
        //
        // The previous code documented "evaluate left-to-right (matches regex
        // semantics)" and that is arithmetically wrong for a mixed expression:
        //     2 + 3 * 4  left-to-right -> 20, correct -> 14
        //     6 / 2 * 3  left-to-right ->  9, correct ->  9 (accidental)
        // A two-pass shunting-yard style evaluation is used: * and / bind tighter
        // than + and -, and equal-precedence operators associate left to right.
        // Division is exact-integer; a non-exact or zero divisor aborts to a miss
        // rather than silently truncating (Article VIII: no fabricated answers).
        BigInteger acc = evaluateWithPrecedence(tokens);
        if (acc == null) {
            trace.add(BrcStep.of("ARITHMETIC", false, 0.20,
                List.of("reason=non-integral-or-zero-divisor")));
            return ArithmeticResult.miss();
        }

        String reply = input + " = " + acc.toString();
        trace.add(BrcStep.of("ARITHMETIC", true, 0.85, List.of(
            "engine=ArithmeticStage.evaluateWithPrecedence(args=" + tokens.size() + "tokens,out=" + acc + ")",
            "planning=" + planningLine,
            "tier=PRO",
            "precedence=mult-before-add (Article III: deterministic, no LLM)")));
        return ArithmeticResult.hit(reply, 0.85);
    }

    /**
     * RECON-W23 — precedence-aware evaluation over a flat token list.
     *
     * <p>Two passes: multiply/divide first (left to right), then add/subtract over
     * the reduced sequence. Returns {@code null} when the expression contains a
     * zero divisor or a division that is not exact — the caller then declines
     * rather than reporting a truncated integer as if it were the answer.</p>
     *
     * <p>Pure function of {@code tokens} (Article III).</p>
     */
    static BigInteger evaluateWithPrecedence(java.util.List<String> tokens) {
        java.util.List<BigInteger> nums = new java.util.ArrayList<>();
        java.util.List<Character> ops = new java.util.ArrayList<>();
        for (String t : tokens) {
            if (t.length() == 1 && "+-*/".indexOf(t.charAt(0)) >= 0) {
                ops.add(t.charAt(0));
            } else {
                try {
                    nums.add(new BigInteger(t));
                } catch (NumberFormatException nfe) {
                    return null;   // "1/2" from "half" is not a literal — decline
                }
            }
        }
        if (nums.size() != ops.size() + 1) return null;   // malformed

        // Pass 1: collapse * and /
        java.util.List<BigInteger> vals = new java.util.ArrayList<>();
        java.util.List<Character> rest = new java.util.ArrayList<>();
        vals.add(nums.get(0));
        for (int i = 0; i < ops.size(); i++) {
            char op = ops.get(i);
            BigInteger rhs = nums.get(i + 1);
            if (op == '*') {
                vals.set(vals.size() - 1, vals.get(vals.size() - 1).multiply(rhs));
            } else if (op == '/') {
                BigInteger lhs = vals.get(vals.size() - 1);
                if (rhs.signum() == 0) return null;
                try {
                    BigInteger[] qr = lhs.divideAndRemainder(rhs);
                    if (qr[1].signum() != 0) return null;   // non-exact: decline
                    vals.set(vals.size() - 1, qr[0]);
                } catch (ArithmeticException ae) {
                    return null;
                }
            } else {
                vals.add(rhs);
                rest.add(op);
            }
        }

        // Pass 2: left-to-right over + and -
        BigInteger acc = vals.get(0);
        for (int i = 0; i < rest.size(); i++) {
            BigInteger rhs = vals.get(i + 1);
            acc = rest.get(i) == '+' ? acc.add(rhs) : acc.subtract(rhs);
        }
        return acc;
    }
}
