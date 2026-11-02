package io.matrix.cognitive.algorithms;

import java.math.BigInteger;

/**
 * W44.2 — exact integer arithmetic over an expression.
 *
 * <p><b>Why this exists in this package.</b> The portfolio needs a family whose answer is exact,
 * immediate and free of any dependency, so that the selector has something to choose against.
 * There was no general arithmetic evaluator in {@code matrix-core}: {@code io.matrix.neuron.BigArithmetic}
 * is a set of modular helpers ({@code modPow}, {@code gcd}, {@code modInverse}) and not an
 * expression evaluator, and the arithmetic stage that does evaluate expressions lives in
 * {@code matrix-brain-runtime}, which {@code matrix-core} cannot depend on — the dependency runs
 * the other way.</p>
 *
 * <p><b>Why {@link BigInteger} and not {@code long}.</b> The method is declared EXACT, so its
 * result must not depend on the width of the machine's word. {@code long} arithmetic silently
 * wraps; {@link BigInteger} does not. The cost is allocation, which is acceptable because the
 * family exists to be correct, not to be the fastest way to add two numbers.</p>
 *
 * <p><b>Why no floating point.</b> {@code /} and {@code %} are integer operations: {@code 7 / 2}
 * is {@code 3}, and a division by zero is an error rather than an infinity. A method that
 * returned a {@code double} would make "exact" untrue for large magnitudes, and the capability
 * would have to be qualified in a way that a reader could not check.</p>
 *
 * <p>The grammar is the usual one, with the usual precedence:</p>
 * <pre>
 *   expression := term (('+' | '-') term)*
 *   term       := unary (('*' | '/' | '%') unary)*
 *   unary      := ('+' | '-') unary | primary
 *   primary    := '(' expression ')' | NUMBER
 * </pre>
 *
 * <p>It is a recursive-descent parser over the expression's characters. No randomness, no clock,
 * no state: the same expression always produces the same value.</p>
 */
public final class ExactIntegerArithmetic {

    /**
     * The longest expression that will be parsed.
     *
     * <p>CONVENTION: a length bound is not a correctness property, it is a guard against a
     * pathological input occupying the parser. It is far above any expression a caller would
     * realistically submit.</p>
     */
    public static final int MAX_EXPRESSION_LENGTH = 4096;

    /**
     * The deepest parenthesis nesting that will be parsed.
     *
     * <p>CONVENTION: the parser recurses once per nesting level, so this bounds the stack. It is
     * far above any nesting a human would write.</p>
     */
    public static final int MAX_NESTING_DEPTH = 256;

    private ExactIntegerArithmetic() {
    }

    /**
     * Evaluates an expression to its exact integer value.
     *
     * @param expression the expression, in the grammar above
     * @return the exact value
     * @throws ExpressionException when the expression is malformed or divides by zero
     */
    public static BigInteger evaluate(String expression) {
        if (expression == null) {
            throw new ExpressionException("the expression is null");
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new ExpressionException("the expression is longer than " + MAX_EXPRESSION_LENGTH
                    + " characters");
        }
        Parser parser = new Parser(expression);
        BigInteger value = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("unexpected '" + parser.current() + "'");
        }
        return value;
    }

    /**
     * Whether an expression is well-formed and evaluable.
     *
     * <p>This is what the method's own precondition check uses, so that a caller who asserts the
     * fact wrongly is caught by the method and not only by the selector.</p>
     */
    public static boolean isWellFormed(String expression) {
        try {
            evaluate(expression);
            return true;
        } catch (ExpressionException e) {
            return false;
        }
    }

    /** A malformed expression, or a division by zero. */
    public static final class ExpressionException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        ExpressionException(String message) {
            super(message);
        }
    }

    /** The recursive-descent parser. */
    private static final class Parser {

        private final String source;
        private int position;
        private int depth;

        Parser(String source) {
            this.source = source;
        }

        BigInteger parseExpression() {
            BigInteger left = parseTerm();
            while (true) {
                skipWhitespace();
                if (atEnd()) {
                    return left;
                }
                char c = current();
                if (c == '+') {
                    position++;
                    left = left.add(parseTerm());
                } else if (c == '-') {
                    position++;
                    left = left.subtract(parseTerm());
                } else {
                    return left;
                }
            }
        }

        private BigInteger parseTerm() {
            BigInteger left = parseUnary();
            while (true) {
                skipWhitespace();
                if (atEnd()) {
                    return left;
                }
                char c = current();
                if (c == '*') {
                    position++;
                    left = left.multiply(parseUnary());
                } else if (c == '/') {
                    position++;
                    BigInteger divisor = parseUnary();
                    left = divide(left, divisor);
                } else if (c == '%') {
                    position++;
                    BigInteger divisor = parseUnary();
                    left = remainder(left, divisor);
                } else {
                    return left;
                }
            }
        }

        private BigInteger divide(BigInteger dividend, BigInteger divisor) {
            if (divisor.signum() == 0) {
                throw error("division by zero");
            }
            return dividend.divide(divisor);
        }

        private BigInteger remainder(BigInteger dividend, BigInteger divisor) {
            if (divisor.signum() == 0) {
                throw error("remainder by zero");
            }
            return dividend.remainder(divisor);
        }

        private BigInteger parseUnary() {
            skipWhitespace();
            if (!atEnd()) {
                char c = current();
                if (c == '-') {
                    position++;
                    return parseUnary().negate();
                }
                if (c == '+') {
                    position++;
                    return parseUnary();
                }
            }
            return parsePrimary();
        }

        private BigInteger parsePrimary() {
            skipWhitespace();
            if (atEnd()) {
                throw error("the expression ends where a value was expected");
            }
            char c = current();
            if (c == '(') {
                position++;
                depth++;
                if (depth > MAX_NESTING_DEPTH) {
                    throw error("the expression nests deeper than " + MAX_NESTING_DEPTH);
                }
                BigInteger value = parseExpression();
                skipWhitespace();
                if (atEnd() || current() != ')') {
                    throw error("a closing parenthesis was expected");
                }
                position++;
                depth--;
                return value;
            }
            if (Character.isDigit(c)) {
                int start = position;
                while (!atEnd() && Character.isDigit(current())) {
                    position++;
                }
                return new BigInteger(source.substring(start, position));
            }
            throw error("unexpected '" + c + "'");
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(current())) {
                position++;
            }
        }

        boolean atEnd() {
            return position >= source.length();
        }

        char current() {
            return source.charAt(position);
        }

        ExpressionException error(String message) {
            return new ExpressionException(message + " (at position " + position + ")");
        }
    }
}
