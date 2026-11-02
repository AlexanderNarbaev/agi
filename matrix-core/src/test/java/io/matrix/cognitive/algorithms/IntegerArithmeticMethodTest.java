package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the exact-arithmetic method: its corpus, its refusals, and its declared accuracy.
 *
 * <p>The corpus below IS the measurement the capability declares. The test asserts that the
 * corpus has exactly {@link IntegerArithmeticMethod#VALIDATION_CASES} entries, so the declared
 * denominator cannot drift away from the number of cases that were actually checked.</p>
 */
class IntegerArithmeticMethodTest {

    /** The validation corpus: an expression and the exact value it must produce. */
    private static final List<Object[]> CORPUS = List.of(
            new Object[]{"1", "1"},
            new Object[]{"1+1", "2"},
            new Object[]{"2*3", "6"},
            new Object[]{"2+3*4", "14"},
            new Object[]{"(2+3)*4", "20"},
            new Object[]{"10-4", "6"},
            new Object[]{"10-4-3", "3"},
            new Object[]{"100/7", "14"},
            new Object[]{"100%7", "2"},
            new Object[]{"-5", "-5"},
            new Object[]{"-(2+3)", "-5"},
            new Object[]{"--5", "5"},
            new Object[]{"2*-3", "-6"},
            new Object[]{"+7", "7"},
            new Object[]{"((1+2)*(3+4))", "21"},
            new Object[]{"1000000000000*1000000000000", "1000000000000000000000000"},
            new Object[]{"0", "0"},
            new Object[]{"0-0", "0"},
            new Object[]{"1/2", "0"},
            new Object[]{"1%2", "1"},
            new Object[]{"2*3+4*5", "26"},
            new Object[]{"  7  +  8  ", "15"},
            new Object[]{"1+2+3+4+5+6+7+8+9+10", "55"},
            new Object[]{"((((1))))", "1"});

    private final IntegerArithmeticMethod method = new IntegerArithmeticMethod();

    @Test
    void theCorpusHasExactlyAsManyCasesAsTheCapabilityDeclares() {
        assertThat(CORPUS).hasSize(IntegerArithmeticMethod.VALIDATION_CASES);
        assertThat(method.capability().accuracyEvidence().denominator())
                .isEqualTo(IntegerArithmeticMethod.VALIDATION_CASES);
    }

    @Test
    void everyCorpusCaseEvaluatesToItsExactValue() {
        for (Object[] entry : CORPUS) {
            String expression = (String) entry[0];
            String expected = (String) entry[1];

            IntegerArithmeticMethod.Output output =
                    method.execute(new IntegerArithmeticMethod.Input(expression), ComputeBudget.unbounded());

            assertThat(output.value())
                    .as("the value of %s", expression)
                    .isEqualTo(new BigInteger(expected));
        }
    }

    @Test
    void everyCorpusCaseHoldsItsOwnPrecondition() {
        for (Object[] entry : CORPUS) {
            assertThat(method.preconditionsHold(new IntegerArithmeticMethod.Input((String) entry[0])))
                    .as("the precondition of %s", entry[0])
                    .isTrue();
        }
    }

    @Test
    void aMalformedExpressionIsRefusedRatherThanGuessed() {
        // "1++2" is NOT in this list: the grammar has a unary plus, so "1 + (+2)" is three.
        List<String> malformed = List.of("", "   ", "1+", "(1", "1)", "abc", "1 2", "*3");

        for (String expression : malformed) {
            assertThat(method.preconditionsHold(new IntegerArithmeticMethod.Input(expression)))
                    .as("the precondition of %s", expression)
                    .isFalse();
            assertThatThrownBy(() -> method.execute(
                    new IntegerArithmeticMethod.Input(expression), ComputeBudget.unbounded()))
                    .as("executing %s", expression)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void aDivisionByZeroIsRefusedRatherThanInfinite() {
        assertThatThrownBy(() -> ExactIntegerArithmetic.evaluate("1/0"))
                .isInstanceOf(ExactIntegerArithmetic.ExpressionException.class)
                .hasMessageContaining("division by zero");
        assertThatThrownBy(() -> ExactIntegerArithmetic.evaluate("1%0"))
                .isInstanceOf(ExactIntegerArithmetic.ExpressionException.class)
                .hasMessageContaining("remainder by zero");
    }

    @Test
    void anOverlongExpressionIsRefusedRatherThanParsed() {
        String overlong = "1+".repeat(ExactIntegerArithmetic.MAX_EXPRESSION_LENGTH) + "1";

        assertThatThrownBy(() -> ExactIntegerArithmetic.evaluate(overlong))
                .isInstanceOf(ExactIntegerArithmetic.ExpressionException.class);
    }

    @Test
    void theCapabilityDeclaresTheFamilyThePreconditionAndNoFallback() {
        AlgorithmCapability capability = method.capability();

        assertThat(capability.id()).isEqualTo(IntegerArithmeticMethod.ID);
        assertThat(capability.serves(AlgorithmFamilies.EXACT_ARITHMETIC)).isTrue();
        assertThat(capability.requires(Preconditions.WELL_FORMED_EXPRESSION)).isTrue();
        assertThat(capability.determinism().deterministic()).isTrue();
        assertThat(capability.accuracyEvidence().value()).isEqualTo(1.0);
        // The exact evaluator is the reference for its family, so there is nothing to fall back to.
        assertThat(capability.fallbackAlgorithmId()).isNull();
    }

    @Test
    void theSameExpressionAlwaysProducesTheSameValue() {
        IntegerArithmeticMethod.Input input = new IntegerArithmeticMethod.Input("(2+3)*4-1");

        BigInteger first = method.execute(input, ComputeBudget.unbounded()).value();
        BigInteger second = method.execute(input, ComputeBudget.unbounded()).value();

        assertThat(first).isEqualTo(second).isEqualTo(new BigInteger("19"));
    }

    @Test
    void theRegistryOffersTheMethodToItsOwnFamilyOnly() {
        AlgorithmRegistry registry = new AlgorithmRegistry().register(method);

        assertThat(registry.methodsFor(AlgorithmFamilies.EXACT_ARITHMETIC)).hasSize(1);
        assertThat(registry.methodsFor(AlgorithmFamilies.SHORTEST_PATH)).isEmpty();
        assertThat(registry.byId(IntegerArithmeticMethod.ID)).isNotNull();
    }

    @Test
    void aRequestForTheArithmeticFamilyIsAnsweredByTheArithmeticMethod() {
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(method), new CostLedger());

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.EXACT_ARITHMETIC,
                new IntegerArithmeticMethod.Input("6*7"),
                Set.of(Preconditions.WELL_FORMED_EXPRESSION),
                ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.COMPLETED);
        assertThat(decision.selectedAlgorithmId()).isEqualTo(IntegerArithmeticMethod.ID);
        assertThat(decision.candidates()).hasSize(1);
        assertThat(decision.candidates().get(0).selected()).isTrue();
    }
}
