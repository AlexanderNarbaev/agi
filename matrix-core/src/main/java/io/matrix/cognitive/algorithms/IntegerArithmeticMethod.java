package io.matrix.cognitive.algorithms;

import java.math.BigInteger;
import java.util.Set;

/**
 * W44.2 — the portfolio's exact-arithmetic method.
 *
 * <p>The family exists so that the selector has a method whose answer is exact and immediate to
 * compare against the others: when the arithmetic is what the task needs, nothing should be
 * slower and nothing should be less certain.</p>
 *
 * <p>The capability declares {@link Preconditions#WELL_FORMED_EXPRESSION} as a REQUIRED
 * precondition, and the method checks it itself before it computes anything. The two checks are
 * not redundant: the selector's check uses the facts the caller asserted, and the method's own
 * check uses the expression. A caller that asserts the fact wrongly is caught by the second
 * check, which is why the method re-parses rather than trusting the fact.</p>
 *
 * <p>The measured accuracy is over {@link #VALIDATION_CASES} expressions whose values were
 * computed by hand and are asserted in the test. The denominator is attached because a rate
 * without one is not a measurement.</p>
 */
public final class IntegerArithmeticMethod
        implements AlgorithmMethod<IntegerArithmeticMethod.Input, IntegerArithmeticMethod.Output> {

    /** The capability id of this method. */
    public static final String ID = "arithmetic.exact-integer.bigint";

    /** The version of this method. */
    public static final String VERSION = "1.0.0";

    /**
     * The number of expressions the declared accuracy was measured over.
     *
     * <p>DERIVED from the validation corpus in the test, which asserts the corpus has exactly
     * this many cases so that the declared denominator cannot drift away from reality.</p>
     */
    public static final int VALIDATION_CASES = 24;

    /**
     * The measured cost of one evaluation, in milliseconds.
     *
     * <p>MEASURED on the W44.2 development machine over the validation corpus; used as the
     * declared cost hint so that the selector has a number rather than a big-O label.</p>
     */
    public static final long MEASURED_COST_MILLIS = 1L;

    /**
     * When the declared evidence was measured.
     *
     * <p>MEASURED: the date of the validation run the evidence comes from.</p>
     */
    public static final String MEASURED_AT = "2026-11-02";

    /** The arithmetic to perform. */
    public record Input(String expression) {
    }

    /** The exact value of the expression. */
    public record Output(BigInteger value) {
    }

    private final AlgorithmCapability capability;

    /** Builds the method with its declared capability. */
    public IntegerArithmeticMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.EXACT_ARITHMETIC),
                Set.of(Preconditions.WELL_FORMED_EXPRESSION),
                Set.of(Input.class.getName()),
                Set.of(Output.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(n) tokens", MEASURED_COST_MILLIS, 0),
                        "O(n) parser stack"),
                new AlgorithmCapability.ResourceProfile(
                        "O(n) heap for the parsed expression", true, false,
                        "the expression is malformed or divides by zero"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate", 1.0, VALIDATION_CASES,
                        "the W44.2 arithmetic validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the evaluator has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "a malformed expression is refused, never approximated", true,
                        "none: the method either produces an exact value or refuses"),
                new AlgorithmCapability.VerificationStrategy(
                        "re-evaluation of the same expression yields the same value",
                        IntegerArithmeticMethod.class.getName() + ".execute"),
                null);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(Input input) {
        return input != null
                && input.expression() != null
                && ExactIntegerArithmetic.isWellFormed(input.expression());
    }

    @Override
    public Output execute(Input input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException(
                    "precondition " + Preconditions.WELL_FORMED_EXPRESSION + " does not hold: "
                            + "the expression is not a well-formed integer arithmetic expression");
        }
        return new Output(ExactIntegerArithmetic.evaluate(input.expression()));
    }
}
