package io.matrix.cognitive.algorithms;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the catalogue: what it holds, and what it refuses to hold twice.
 */
class AlgorithmRegistryTest {

    @Test
    void anEmptyRegistryHasNoMethodsForAnyFamily() {
        AlgorithmRegistry registry = new AlgorithmRegistry();

        assertThat(registry.methodsFor(AlgorithmFamilies.SHORTEST_PATH)).isEmpty();
        assertThat(registry.ids()).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void aMethodIsOfferedToEveryFamilyItsCapabilityDeclares() {
        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new DijkstraShortestPathMethod());

        assertThat(registry.methodsFor(AlgorithmFamilies.SHORTEST_PATH))
                .containsExactly(registry.byId(DijkstraShortestPathMethod.ID));
        assertThat(registry.methodsFor(AlgorithmFamilies.EXACT_ARITHMETIC)).isEmpty();
    }

    @Test
    void aDuplicateIdIsRefused() {
        AlgorithmRegistry registry = new AlgorithmRegistry()
                .register(new DijkstraShortestPathMethod());

        assertThatThrownBy(() -> registry.register(new DijkstraShortestPathMethod()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DijkstraShortestPathMethod.ID);
        assertThat(registry.size()).isOne();
    }

    @Test
    void anUnknownIdYieldsNothing() {
        AlgorithmRegistry registry = new AlgorithmRegistry();

        assertThat(registry.byId("no.such.method")).isNull();
        assertThat(registry.byId(null)).isNull();
    }

    @Test
    void aNullMethodIsRefused() {
        AlgorithmRegistry registry = new AlgorithmRegistry();

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDefaultSelectorIsBuiltOverTheDefaultCatalogue() {
        CostLedger ledger = new CostLedger();

        AlgorithmSelector selector = Portfolio.defaultSelector(ledger);

        assertThat(selector.decide(new ProblemRequest(
                AlgorithmFamilies.EXACT_ARITHMETIC,
                new IntegerArithmeticMethod.Input("1+1"),
                Set.of(Preconditions.WELL_FORMED_EXPRESSION),
                ComputeBudget.unbounded())).selectedAlgorithmId())
                .isEqualTo(IntegerArithmeticMethod.ID);
    }

    @Test
    void theDecisionAndTheCapabilityDescribeThemselves() {
        AlgorithmDecision decision = new AlgorithmDecision(
                AlgorithmFamilies.EXACT_ARITHMETIC, IntegerArithmeticMethod.ID, "1.0.0",
                "a reason", List.of(), ComputeBudget.unbounded(),
                AlgorithmDecision.Status.COMPLETED, null, "an outcome");

        assertThat(decision.toString()).contains(AlgorithmFamilies.EXACT_ARITHMETIC);
        assertThat(decision.toString()).contains(IntegerArithmeticMethod.ID);
        assertThat(new IntegerArithmeticMethod().capability().toString())
                .contains(Preconditions.WELL_FORMED_EXPRESSION);
    }

    @Test
    void theDefaultPortfolioHoldsEveryFamilyItPromises() {
        AlgorithmRegistry registry = Portfolio.defaultRegistry();

        assertThat(registry.families()).contains(
                AlgorithmFamilies.EXACT_ARITHMETIC,
                AlgorithmFamilies.DEDUCTIVE_REASONING,
                AlgorithmFamilies.SHORTEST_PATH,
                AlgorithmFamilies.MEMBERSHIP_LOOKUP);
        assertThat(registry.size()).isEqualTo(8);
        assertThat(registry.ids()).contains(
                IntegerArithmeticMethod.ID,
                EntailmentMethod.ID,
                DijkstraShortestPathMethod.ID,
                BellmanFordShortestPathMethod.ID,
                BoundedUniformCostSearchMethod.ID,
                LinearScanMembershipMethod.ID,
                TrieMembershipMethod.ID,
                BloomFilterMembershipMethod.ID);
    }
}
