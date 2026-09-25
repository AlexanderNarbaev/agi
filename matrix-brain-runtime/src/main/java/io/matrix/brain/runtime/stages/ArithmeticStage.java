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

    /** Pattern: optional whitespace, integer, op, integer, optional whitespace. */
    private static final Pattern BINARY = Pattern.compile(
        "\\s*(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)\\s*"
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
        Matcher m = BINARY.matcher(input);
        if (!m.matches()) {
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
        trace.add(BrcStep.of("ARITHMETIC", true, 0.99,
            List.of("a=" + a, "op=" + op, "b=" + b, "result=" + result)));
        return ArithmeticResult.hit(reply, 0.99);
    }
}
