package io.matrix.cognitive.algorithms;

import io.matrix.knowledge.Derivation;
import io.matrix.knowledge.DerivationStatus;
import io.matrix.knowledge.EntailmentChecker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the entailment method: it wraps the checker, and it agrees with it.
 *
 * <p>The corpus below is the measurement the capability declares. Every case asserts that the
 * wrapper's verdict equals the reference checker's verdict on the same theory — the wrapper adds
 * a capability and a precondition, and it must not change a single answer.</p>
 */
class EntailmentMethodTest {

    private final EntailmentMethod method = new EntailmentMethod();
    private final EntailmentChecker reference = new EntailmentChecker();

    private static Derivation rule(String id, Derivation.Connective connective,
                                   String conclusion, String... premises) {
        return new Derivation(id, "workspace", "rule-" + id, "rule " + id, connective, conclusion,
                List.of(premises), DerivationStatus.DERIVED_VALID, "2026-11-02");
    }

    /**
     * The validation corpus: a goal, the facts, the rules, and the verdict the reference checker
     * gives. Each entry is {@code {goal, facts, rules, expectedStatus}}.
     */
    private static final List<Object[]> CORPUS = List.of(
            new Object[]{"c", List.of("a", "b"),
                    List.of(rule("r1", Derivation.Connective.AND, "c", "a", "b")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"d", List.of("a", "b"),
                    List.of(rule("r1", Derivation.Connective.AND, "c", "a", "b")),
                    DerivationStatus.DERIVED_INVALID},
            new Object[]{"c", List.of(),
                    List.of(rule("r1", Derivation.Connective.AND, "c", "a", "b")),
                    DerivationStatus.DERIVED_INVALID},
            new Object[]{"c", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.IMPLIES, "c", "a")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"c", List.of(),
                    List.of(rule("r1", Derivation.Connective.IMPLIES, "c", "a")),
                    DerivationStatus.DERIVED_INVALID},
            new Object[]{"c", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.NOT, "c", "a")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"c", List.of(),
                    List.of(rule("r1", Derivation.Connective.NOT, "c", "a")),
                    DerivationStatus.DERIVED_INVALID},
            new Object[]{"a", List.of("a"), List.of(), DerivationStatus.DERIVED_VALID},
            new Object[]{"b", List.of("a"), List.of(), DerivationStatus.DERIVED_INVALID},
            new Object[]{"c", List.of("a", "b"),
                    List.of(rule("r1", Derivation.Connective.OR, "c", "a", "b")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"x", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.IMPLIES, "c", "a")),
                    DerivationStatus.DERIVED_INVALID},
            new Object[]{"c", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.IMPLIES, "c", "a"),
                            rule("r2", Derivation.Connective.IMPLIES, "c", "b")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"c", List.of("a", "b", "d"),
                    List.of(rule("r1", Derivation.Connective.AND, "c", "a", "b")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"z", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.IMPLIES, "c", "a")),
                    DerivationStatus.DERIVED_INVALID},
            new Object[]{"c", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.AND, "c", "a", "a")),
                    DerivationStatus.DERIVED_VALID},
            new Object[]{"c", List.of("a"),
                    List.of(rule("r1", Derivation.Connective.IMPLIES, "c", "a"),
                            rule("r2", Derivation.Connective.IMPLIES, "c", "c")),
                    DerivationStatus.DERIVED_VALID});

    @Test
    void theCorpusHasExactlyAsManyCasesAsTheCapabilityDeclares() {
        assertThat(CORPUS).hasSize(EntailmentMethod.VALIDATION_CASES);
        assertThat(method.capability().accuracyEvidence().denominator())
                .isEqualTo(EntailmentMethod.VALIDATION_CASES);
    }

    @Test
    void everyCorpusCaseAgreesWithTheReferenceChecker() {
        for (Object[] entry : CORPUS) {
            String goal = (String) entry[0];
            @SuppressWarnings("unchecked")
            List<String> facts = (List<String>) entry[1];
            @SuppressWarnings("unchecked")
            List<Derivation> rules = (List<Derivation>) entry[2];
            DerivationStatus expected = (DerivationStatus) entry[3];

            EntailmentMethod.EntailmentInput input = new EntailmentMethod.EntailmentInput(goal, facts, rules);

            assertThat(method.execute(input, ComputeBudget.unbounded()).status())
                    .as("the verdict for the goal %s", goal)
                    .isEqualTo(expected);
            assertThat(reference.entails(goal, facts, rules))
                    .as("the reference verdict for the goal %s", goal)
                    .isEqualTo(expected);
        }
    }

    @Test
    void theAtomCountMirrorsWhatTheCheckerCounts() {
        EntailmentMethod.EntailmentInput input = new EntailmentMethod.EntailmentInput(
                "c", List.of("a", "b"),
                List.of(rule("r1", Derivation.Connective.AND, "c", "a", "b")));

        // goal c, facts a and b, rule conclusion c, rule premises a and b.
        assertThat(EntailmentMethod.atomCount(input)).isEqualTo(3);
    }

    @Test
    void aTheoryPastTheAtomBudgetIsRefusedByThePrecondition() {
        List<String> facts = new ArrayList<>();
        for (int i = 0; i < EntailmentChecker.DEFAULT_ATOM_BUDGET + 4; i++) {
            facts.add("atom-" + i);
        }
        EntailmentMethod.EntailmentInput input =
                new EntailmentMethod.EntailmentInput("goal", facts, List.of());

        assertThat(EntailmentMethod.atomCount(input)).isGreaterThan(EntailmentChecker.DEFAULT_ATOM_BUDGET);
        assertThat(method.preconditionsHold(input)).isFalse();
        assertThatThrownBy(() -> method.execute(input, ComputeBudget.unbounded()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Preconditions.THEORY_WITHIN_ATOM_BUDGET);
    }

    @Test
    void aBlankGoalIsRefused() {
        EntailmentMethod.EntailmentInput input =
                new EntailmentMethod.EntailmentInput("  ", List.of(), List.of());

        assertThat(method.preconditionsHold(input)).isFalse();
    }

    @Test
    void aTheoryTooLargeForTheNodeBudgetIsReportedAsResourceLimited() {
        List<String> facts = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        EntailmentMethod.EntailmentInput input =
                new EntailmentMethod.EntailmentInput("k", facts, List.of());

        ComputeBudget tiny = new ComputeBudget(1_000L, 1_000L, 1_000L, 4);

        assertThatThrownBy(() -> method.execute(input, tiny))
                .isInstanceOf(ResourceLimitException.class)
                .hasMessageContaining("valuations");
    }

    @Test
    void theResourceLimitCarriesTheUndecidedStatus() {
        List<String> facts = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        EntailmentMethod.EntailmentInput input =
                new EntailmentMethod.EntailmentInput("k", facts, List.of());
        ComputeBudget tiny = new ComputeBudget(1_000L, 1_000L, 1_000L, 4);

        assertThatThrownBy(() -> method.execute(input, tiny))
                .isInstanceOf(ResourceLimitException.class)
                .satisfies(thrown -> {
                    ResourceLimitException limit = (ResourceLimitException) thrown;
                    EntailmentMethod.EntailmentOutput partial =
                            (EntailmentMethod.EntailmentOutput) limit.partialResult();
                    assertThat(partial.status()).isEqualTo(DerivationStatus.UNDECIDED);
                });
    }

    @Test
    void theOutputSaysWhetherTheConclusionWasEntailed() {
        EntailmentMethod.EntailmentOutput valid = new EntailmentMethod.EntailmentOutput(
                DerivationStatus.DERIVED_VALID, 2);
        EntailmentMethod.EntailmentOutput invalid = new EntailmentMethod.EntailmentOutput(
                DerivationStatus.DERIVED_INVALID, 2);

        assertThat(valid.entailed()).isTrue();
        assertThat(invalid.entailed()).isFalse();
    }

    @Test
    void anUnmeasuredAccuracyProfileSaysSo() {
        AlgorithmCapability.AccuracyProfile unmeasured = AlgorithmCapability.AccuracyProfile.unmeasured();

        assertThat(unmeasured.metric()).isEqualTo("unmeasured");
        assertThat(unmeasured.denominator()).isZero();
        assertThat(unmeasured.corpus()).isEqualTo("none");
        assertThat(unmeasured.measuredAt()).isEqualTo("unmeasured");
    }

    @Test
    void theCapabilityDeclaresTheFamilyAndTheAtomBudgetPrecondition() {
        AlgorithmCapability capability = method.capability();

        assertThat(capability.id()).isEqualTo(EntailmentMethod.ID);
        assertThat(capability.serves(AlgorithmFamilies.DEDUCTIVE_REASONING)).isTrue();
        assertThat(capability.requires(Preconditions.THEORY_WITHIN_ATOM_BUDGET)).isTrue();
        assertThat(capability.determinism().deterministic()).isTrue();
        assertThat(capability.accuracyEvidence().value()).isEqualTo(1.0);
    }

    @Test
    void theSelectorOffersTheMethodToTheReasoningFamily() {
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(method), new CostLedger());

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.DEDUCTIVE_REASONING,
                new EntailmentMethod.EntailmentInput("c", List.of("a"), List.of()),
                Set.of(Preconditions.THEORY_WITHIN_ATOM_BUDGET),
                ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.COMPLETED);
        assertThat(decision.selectedAlgorithmId()).isEqualTo(EntailmentMethod.ID);
    }
}
