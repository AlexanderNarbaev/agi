package io.matrix.evolution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParetoFitnessTest {

    @Test
    void shouldDominateWhenBetterQuality() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.8, 0.5, 0.3, 0.2);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.2);

        assertThat(v1.dominates(v2)).isTrue();
        assertThat(v2.dominates(v1)).isFalse();
    }

    @Test
    void shouldDominateWhenBetterRobustness() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.5, 0.9, 0.3, 0.2);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.5, 0.4, 0.3, 0.2);

        assertThat(v1.dominates(v2)).isTrue();
    }

    @Test
    void shouldDominateWhenLowerLatencyPenalty() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.1, 0.2);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.6, 0.2);

        assertThat(v1.dominates(v2)).isTrue();
    }

    @Test
    void shouldDominateWhenLowerComplexityPenalty() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.1);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.7);

        assertThat(v1.dominates(v2)).isTrue();
    }

    @Test
    void shouldNotDominateWhenBetterQualityButWorseLatency() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.9, 0.5, 0.8, 0.3);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.3, 0.5, 0.2, 0.3);

        assertThat(v1.dominates(v2)).isFalse();
        assertThat(v2.dominates(v1)).isFalse();
    }

    @Test
    void shouldNotDominateWhenBetterQualityButWorseComplexity() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.9, 0.5, 0.3, 0.9);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.3, 0.5, 0.3, 0.2);

        assertThat(v1.dominates(v2)).isFalse();
        assertThat(v2.dominates(v1)).isFalse();
    }

    @Test
    void shouldNotDominateIdenticalVectors() {
        ParetoFitness.FitnessVector v = new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.2);

        assertThat(v.dominates(v)).isFalse();
    }

    @Test
    void shouldNotDominateWhenEqualOnAllAxes() {
        ParetoFitness.FitnessVector v1 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.2);
        ParetoFitness.FitnessVector v2 = new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.2);

        assertThat(v1.dominates(v2)).isFalse();
    }

    @Test
    void shouldExtractParetoFrontFromTenCandidates() {
        List<ParetoFitness.FitnessVector> vectors = List.of(
                new ParetoFitness.FitnessVector(0.9, 0.5, 0.2, 0.3), // v0 — dominated by v8
                new ParetoFitness.FitnessVector(0.2, 0.9, 0.2, 0.3), // v1 — non-dominated
                new ParetoFitness.FitnessVector(0.5, 0.3, 0.9, 0.3), // v2 — dominated by v8
                new ParetoFitness.FitnessVector(0.5, 0.3, 0.2, 0.9), // v3 — dominated by v8
                new ParetoFitness.FitnessVector(0.2, 0.3, 0.2, 0.3), // v4 — dominated by v1
                new ParetoFitness.FitnessVector(0.1, 0.2, 0.3, 0.4), // v5 — dominated by v4
                new ParetoFitness.FitnessVector(0.8, 0.8, 0.2, 0.3), // v6 — dominated by v8
                new ParetoFitness.FitnessVector(0.3, 0.7, 0.3, 0.4), // v7 — dominated by v8
                new ParetoFitness.FitnessVector(0.9, 0.8, 0.2, 0.3), // v8 — non-dominated
                new ParetoFitness.FitnessVector(0.5, 0.8, 0.1, 0.1)  // v9 — non-dominated
        );

        List<ParetoFitness.FitnessVector> front = ParetoFitness.paretoFront(vectors);

        assertThat(front).hasSize(3);
        // v1: (0.2, 0.9, 0.2, 0.3)
        assertThat(front).anyMatch(v -> v.quality() == 0.2 && v.robustness() == 0.9);
        // v8: (0.9, 0.8, 0.2, 0.3)
        assertThat(front).anyMatch(v -> v.quality() == 0.9 && v.robustness() == 0.8);
        // v9: (0.5, 0.8, 0.1, 0.1)
        assertThat(front).anyMatch(v -> v.quality() == 0.5 && v.robustness() == 0.8
                && v.latencyPenalty() == 0.1 && v.complexityPenalty() == 0.1);
    }

    @Test
    void compositeScoreShouldBeInRangeZeroToOne() {
        ParetoFitness.FitnessVector maxVector = new ParetoFitness.FitnessVector(1.0, 1.0, 0.0, 0.0);
        ParetoFitness.FitnessVector minVector = new ParetoFitness.FitnessVector(0.0, 0.0, 1.0, 1.0);
        ParetoFitness.FitnessVector midVector = new ParetoFitness.FitnessVector(0.5, 0.5, 0.5, 0.5);

        assertThat(maxVector.compositeScore()).isEqualTo(1.0);
        assertThat(minVector.compositeScore()).isEqualTo(0.0);
        assertThat(midVector.compositeScore()).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void compositeScoreFormulaShouldBeCorrect() {
        ParetoFitness.FitnessVector v = new ParetoFitness.FitnessVector(0.5, 0.6, 0.3, 0.4);

        double expected = 0.4 * 0.5 + 0.25 * 0.6 + 0.2 * (1.0 - 0.3) + 0.15 * (1.0 - 0.4);
        assertThat(v.compositeScore()).isEqualTo(expected);
    }

    @Test
    void shouldHandleEmptyListInParetoFront() {
        List<ParetoFitness.FitnessVector> front = ParetoFitness.paretoFront(List.of());
        assertThat(front).isEmpty();
    }

    @Test
    void shouldHandleEmptyListInParetoSelect() {
        List<String> candidates = List.of();
        List<ParetoFitness.EvaluatedCandidate<String>> result =
                ParetoFitness.paretoSelect(candidates, c ->
                        new ParetoFitness.FitnessVector(1.0, 1.0, 0.0, 0.0));

        assertThat(result).isEmpty();
    }

    @Test
    void singleCandidateShouldBeParetoOptimal() {
        List<String> candidates = List.of("only");
        List<ParetoFitness.EvaluatedCandidate<String>> result =
                ParetoFitness.paretoSelect(candidates, c ->
                        new ParetoFitness.FitnessVector(0.5, 0.5, 0.3, 0.2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).candidate()).isEqualTo("only");
        assertThat(result.get(0).isParetoOptimal()).isTrue();
        assertThat(result.get(0).fitness().quality()).isEqualTo(0.5);
    }

    @Test
    void paretoSelectShouldMarkParetoOptimalAndDominated() {
        List<String> candidates = List.of("best", "ok", "worst");
        List<ParetoFitness.EvaluatedCandidate<String>> result =
                ParetoFitness.paretoSelect(candidates, c -> switch (c) {
                    case "best" -> new ParetoFitness.FitnessVector(1.0, 1.0, 0.0, 0.0);
                    case "ok" -> new ParetoFitness.FitnessVector(0.5, 0.5, 0.5, 0.5);
                    case "worst" -> new ParetoFitness.FitnessVector(0.2, 0.2, 0.8, 0.8);
                    default -> new ParetoFitness.FitnessVector(0.0, 0.0, 1.0, 1.0);
                });

        assertThat(result).hasSize(3);
        assertThat(result.stream().filter(ParetoFitness.EvaluatedCandidate::isParetoOptimal))
                .hasSize(1);
        assertThat(result.stream().filter(c -> c.candidate().equals("best")).findFirst().orElseThrow()
                .isParetoOptimal()).isTrue();
        assertThat(result.stream().filter(c -> c.candidate().equals("ok")).findFirst().orElseThrow()
                .isParetoOptimal()).isFalse();
        assertThat(result.stream().filter(c -> c.candidate().equals("worst")).findFirst().orElseThrow()
                .isParetoOptimal()).isFalse();
    }

    @Test
    void factoryOfShouldNormalizeLatencyAndComplexity() {
        ParetoFitness.FitnessVector v = ParetoFitness.FitnessVector.of(0.7, 0.8, 250.0, 10);

        assertThat(v.quality()).isEqualTo(0.7);
        assertThat(v.robustness()).isEqualTo(0.8);
        assertThat(v.latencyPenalty()).isEqualTo(250.0 / 500.0);
        assertThat(v.complexityPenalty()).isEqualTo(10.0 / 20.0);
    }

    @Test
    void factoryOfShouldCapPenaltiesAtOne() {
        ParetoFitness.FitnessVector v = ParetoFitness.FitnessVector.of(0.5, 0.5, 2000.0, 50);

        assertThat(v.latencyPenalty()).isEqualTo(1.0);
        assertThat(v.complexityPenalty()).isEqualTo(1.0);
    }

    @Test
    void constructorShouldBeRejected() {
        try {
            var ctor = ParetoFitness.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void multipleCandidatesAllParetoOptimalWhenNoDominance() {
        List<String> candidates = List.of("a", "b");
        List<ParetoFitness.EvaluatedCandidate<String>> result =
                ParetoFitness.paretoSelect(candidates, c -> switch (c) {
                    case "a" -> new ParetoFitness.FitnessVector(0.9, 0.1, 0.5, 0.5);
                    case "b" -> new ParetoFitness.FitnessVector(0.1, 0.9, 0.5, 0.5);
                    default -> new ParetoFitness.FitnessVector(0.0, 0.0, 1.0, 1.0);
                });

        assertThat(result).allMatch(ParetoFitness.EvaluatedCandidate::isParetoOptimal);
    }
}
