package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the procedure life cycle: promotion on held-out evidence, use inside the
 * applicability domain, and retirement when the evidence stops holding.
 */
class ProcedureStoreTest {

    private static final String FAMILY = AlgorithmFamilies.SHORTEST_PATH;
    private static final String REFERENCE = BellmanFordShortestPathMethod.ID;

    /** A candidate procedure with an applicability domain and a reference to fall back on. */
    private static Procedure candidate(String id, Set<String> requiredFacts) {
        return new Procedure(id, "a validated procedure", FAMILY, "1.0.0",
                "applies when the graph is a tree", requiredFacts, REFERENCE,
                0, 0.0, 0, 0, "2026-11-02", null, ValidationKind.INDEPENDENT);
    }

    /** {@code count} cases on which the candidate agrees with the reference. */
    private static List<ValidationCase> agreeingCases(int count) {
        List<ValidationCase> cases = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cases.add(new ValidationCase("case-" + i, "input-" + i, "answer-" + i, "answer-" + i));
        }
        return cases;
    }

    /** A small graph, so that a procedure and the reference method take the same payload. */
    private static ShortestPathProblem aSmallGraph() {
        return new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0);
    }

    @Test
    void aCandidateThatAgreesWithTheReferenceOnEveryHeldOutCaseIsPromoted() {
        ProcedureStore store = new ProcedureStore();
        int cases = ProcedureValidator.MINIMUM_VALIDATION_CASES + 3;

        ProcedureStore.PromotionOutcome outcome = store.promote(candidate("p1", Set.of("TREE_GRAPH")),
                agreeingCases(cases));

        assertThat(outcome.promoted()).isTrue();
        assertThat(outcome.reason()).contains("agreed with the reference");
        assertThat(store.size()).isOne();

        Procedure stored = store.byId("p1").orElseThrow();
        assertThat(stored.validationCases()).isEqualTo(cases);
        assertThat(stored.validationAgreement()).isEqualTo(1.0);
        assertThat(stored.active()).isTrue();
    }

    @Test
    void aCandidateValidatedOnTooFewCasesIsNotPromoted() {
        ProcedureStore store = new ProcedureStore();

        ProcedureStore.PromotionOutcome outcome = store.promote(candidate("p1", Set.of()),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES - 1));

        assertThat(outcome.promoted()).isFalse();
        assertThat(outcome.reason()).contains("at least " + ProcedureValidator.MINIMUM_VALIDATION_CASES);
        assertThat(store.size()).isZero();
    }

    @Test
    void aCandidateThatDisagreesAnywhereIsNotPromotedAndTheCaseIsNamed() {
        ProcedureStore store = new ProcedureStore();
        List<ValidationCase> cases = agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES);
        cases.set(2, new ValidationCase("the-diverging-case", "input", "the reference", "something else"));

        ProcedureStore.PromotionOutcome outcome = store.promote(candidate("p1", Set.of()), cases);

        assertThat(outcome.promoted()).isFalse();
        assertThat(outcome.reason()).contains("the-diverging-case");
        assertThat(store.size()).isZero();
    }

    @Test
    void aProcedureAppliesOnlyInsideItsApplicabilityDomain() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of("TREE_GRAPH", "CONNECTED_GRAPH")),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));

        assertThat(store.findApplicable(FAMILY, Set.of("TREE_GRAPH"))).isEmpty();
        assertThat(store.findApplicable(FAMILY, Set.of("TREE_GRAPH", "CONNECTED_GRAPH"))).isPresent();
        assertThat(store.findApplicable(FAMILY, Set.of("TREE_GRAPH", "CONNECTED_GRAPH", "EXTRA")))
                .isPresent();
        assertThat(store.findApplicable("another.family", Set.of("TREE_GRAPH", "CONNECTED_GRAPH")))
                .isEmpty();
    }

    @Test
    void aProcedureWhoseAgreementFallsIsRetiredAndTheReferenceTakesOverAgain() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of("TREE_GRAPH")),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));

        Procedure updated = store.recordUse("p1", true, ProcedureStore.MINIMUM_AGREEMENT - 0.01);

        assertThat(updated.active()).isFalse();
        assertThat(updated.invalidatedAt()).contains("below the threshold");
        assertThat(updated.useCount()).isOne();
        assertThat(store.findApplicable(FAMILY, Set.of("TREE_GRAPH"))).isEmpty();
        assertThat(store.active()).isEmpty();
    }

    @Test
    void aProcedureWhoseAgreementHoldsKeepsRunningAndCountsItsUses() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of()),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));

        store.recordUse("p1", true, 1.0);
        Procedure updated = store.recordUse("p1", false, 1.0);

        assertThat(updated.active()).isTrue();
        assertThat(updated.useCount()).isEqualTo(2);
        assertThat(updated.failureCount()).isOne();
    }

    @Test
    void aProcedureCanBeRetiredExplicitly() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of()),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));

        Procedure retired = store.invalidate("p1", "the domain moved");

        assertThat(retired.active()).isFalse();
        assertThat(retired.invalidatedAt()).isEqualTo("the domain moved");
    }

    @Test
    void anUnknownProcedureCannotBeUsedOrRetired() {
        ProcedureStore store = new ProcedureStore();

        assertThatThrownBy(() -> store.recordUse("nope", true, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.invalidate("nope", "why"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.byId("nope")).isEmpty();
    }

    @Test
    void aProcedureWithoutAReferenceIsRefusedAtConstruction() {
        assertThatThrownBy(() -> new Procedure("p1", "n", FAMILY, "1.0.0", "a", Set.of(), "  ",
                0, 0.0, 0, 0, "2026-11-02", null, ValidationKind.INDEPENDENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aProcedureMethodCarriesItsDomainAsItsPreconditions() {
        Procedure procedure = candidate("p1", Set.of("TREE_GRAPH"));
        ProcedureMethod<ShortestPathProblem> method =
                new ProcedureMethod<>(procedure, problem -> "the cheap answer");

        AlgorithmCapability capability = method.capability();

        assertThat(capability.id()).isEqualTo(ProcedureMethod.ID_PREFIX + "p1");
        assertThat(capability.requiredPreconditions()).containsExactly("TREE_GRAPH");
        assertThat(capability.fallbackAlgorithmId()).isEqualTo(REFERENCE);
        assertThat(method.procedureId()).isEqualTo("p1");
        assertThat(method.referenceAlgorithmId()).isEqualTo(REFERENCE);
        assertThat(method.procedure()).isEqualTo(procedure);
    }

    @Test
    void aProcedureMethodReplaysTheCheapComputationWhileItIsAlive() {
        Procedure procedure = candidate("p1", Set.of("TREE_GRAPH"));
        ProcedureMethod<ShortestPathProblem> method =
                new ProcedureMethod<>(procedure, problem -> "the cheap answer");
        ShortestPathProblem payload =
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0);

        assertThat(method.preconditionsHold(payload)).isTrue();
        assertThat(method.execute(payload, ComputeBudget.unbounded()).answer())
                .isEqualTo("the cheap answer");
        assertThat(method.execute(payload, ComputeBudget.unbounded()).procedureId()).isEqualTo("p1");
    }

    @Test
    void aRetiredProcedureIsNoLongerOfferedByItsMethod() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of()),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));
        Procedure retired = store.invalidate("p1", "retired");

        ProcedureMethod<ShortestPathProblem> method =
                new ProcedureMethod<>(retired, problem -> "the cheap answer");
        ShortestPathProblem payload =
                new ShortestPathProblem(3, List.of(new ShortestPathProblem.Edge(0, 1, 1.0)), 0);

        assertThat(method.preconditionsHold(payload)).isFalse();
        assertThatThrownBy(() -> method.execute(payload, ComputeBudget.unbounded()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSelectorReusesAProcedureAndSaysSoInTheStatus() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of("TREE_GRAPH")),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));
        Procedure promoted = store.byId("p1").orElseThrow();

        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new BellmanFordShortestPathMethod())
                .register(new ProcedureMethod<ShortestPathProblem>(promoted, problem -> "the cheap answer"));
        AlgorithmSelector selector = new AlgorithmSelector(registry, new CostLedger());

        ProblemRequest request = new ProblemRequest(
                FAMILY, aSmallGraph(), Set.of("TREE_GRAPH"), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.REUSED_PROCEDURE);
        assertThat(decision.selectedAlgorithmId()).isEqualTo(ProcedureMethod.ID_PREFIX + "p1");
        assertThat(decision.fallbackAlgorithmId()).isEqualTo(REFERENCE);
    }

    @Test
    void theSelectorFallsBackToTheReferenceWhenTheDomainDoesNotHold() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of("TREE_GRAPH")),
                agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));
        Procedure promoted = store.byId("p1").orElseThrow();

        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new BellmanFordShortestPathMethod())
                .register(new ProcedureMethod<ShortestPathProblem>(promoted, problem -> "the cheap answer"));
        AlgorithmSelector selector = new AlgorithmSelector(registry, new CostLedger());

        ProblemRequest request = new ProblemRequest(
                FAMILY, aSmallGraph(), Set.of(), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(BellmanFordShortestPathMethod.ID);
        assertThat(decision.status()).isEqualTo(AlgorithmDecision.Status.COMPLETED);
        AlgorithmDecision.Candidate blocked = decision.candidates().stream()
                .filter(c -> c.algorithmId().equals(ProcedureMethod.ID_PREFIX + "p1"))
                .findFirst()
                .orElseThrow();
        assertThat(blocked.selected()).isFalse();
        assertThat(blocked.verdict()).contains("TREE_GRAPH");
    }

    @Test
    void theValidatorReportsWhatItMeasured() {
        List<ValidationCase> cases = agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES + 1);

        ProcedureValidator.ValidationOutcome outcome =
                ProcedureValidator.validate(candidate("p1", Set.of()), cases);

        assertThat(outcome.totalCases()).isEqualTo(cases.size());
        assertThat(outcome.agreements()).isEqualTo(cases.size());
        assertThat(outcome.agreement()).isEqualTo(1.0);
        assertThat(outcome.accepted()).isTrue();
    }

    @Test
    void aValidationCaseReportsWhetherItAgrees() {
        ValidationCase agreeing = new ValidationCase("c", "in", "answer", "answer");
        ValidationCase disagreeing = new ValidationCase("c", "in", "answer", "other");

        assertThat(agreeing.agrees()).isTrue();
        assertThat(disagreeing.agrees()).isFalse();
        assertThatThrownBy(() -> new ValidationCase(" ", "in", "a", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aProcedureMethodWithoutAnExecutorIsRefused() {
        assertThatThrownBy(() -> new ProcedureMethod<ShortestPathProblem>(candidate("p1", Set.of()), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcedureMethod<ShortestPathProblem>(null, problem -> "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theStoreHoldsWhatItPromoted() {
        ProcedureStore store = new ProcedureStore();
        store.promote(candidate("p1", Set.of()), agreeingCases(ProcedureValidator.MINIMUM_VALIDATION_CASES));

        Optional<Procedure> found = store.byId("p1");

        assertThat(found).isPresent();
        assertThat(store.all()).hasSize(1);
        assertThat(store.size()).isOne();
    }
}
