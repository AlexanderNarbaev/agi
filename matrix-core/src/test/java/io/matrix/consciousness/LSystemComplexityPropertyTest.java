package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W139 — L-System complexity property-based tests.
 */
class LSystemComplexityPropertyTest {

    @Property(tries = 50)
    void propertyGenerateAndMeasureReturnsValidK(@ForAll("axioms") String axiom,
                                                   @ForAll("iterations") int iter,
                                                   @ForAll("rules") Map<Character, String> rules) {
        double k = LSystemComplexity.generateAndMeasure(axiom, rules, iter);
        assertThat(k).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyMeasureIterationsLengthMatches(@ForAll("axioms") String axiom,
                                                 @ForAll("iterations") int iter,
                                                 @ForAll("rules") Map<Character, String> rules) {
        double[] ks = LSystemComplexity.measureIterations(axiom, rules, iter);
        assertThat(ks.length).isEqualTo(iter + 1);
    }

    @Property(tries = 50)
    void propertyAllKNonNegative(@ForAll("axioms") String axiom,
                                  @ForAll("iterations") int iter,
                                  @ForAll("rules") Map<Character, String> rules) {
        double[] ks = LSystemComplexity.measureIterations(axiom, rules, iter);
        for (double k : ks) {
            assertThat(k).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertyOptimalIterationCountBounded(@ForAll("axioms") String axiom,
                                                @ForAll("iterations") int iter,
                                                @ForAll("rules") Map<Character, String> rules) {
        int optimal = LSystemComplexity.optimalIterationCount(axiom, rules, iter);
        assertThat(optimal).isBetween(0, iter);
    }

    @Provide
    Arbitrary<String> axioms() {
        return Arbitraries.strings()
            .withCharRange('A', 'F')
            .ofMinLength(1)
            .ofMaxLength(6);
    }

    @Provide
    Arbitrary<Integer> iterations() {
        return Arbitraries.integers().between(0, 5);
    }

    @Provide
    Arbitrary<Map<Character, String>> rules() {
        return Arbitraries.maps(
            Arbitraries.of('A', 'B', 'C', 'D', 'E', 'F'),
            Arbitraries.strings().withCharRange('A', 'F').ofMinLength(1).ofMaxLength(2)
        ).ofMinSize(0).ofMaxSize(3);
    }
}
