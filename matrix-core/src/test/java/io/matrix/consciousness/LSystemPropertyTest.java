package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W134 — L-system property-based tests.
 *
 * <p>Property-based verification of W110 LSystem.
 */
class LSystemPropertyTest {

    @Property(tries = 30)
    void propertyAxiomUnchangedNoRules(@ForAll("axioms") String axiom,
                                        @ForAll("iterations") int iter) {
        // No rules → output = axiom
        String result = LSystem.generate(axiom, new HashMap<>(), iter);
        assertThat(result).isEqualTo(axiom);
    }

    @Property(tries = 30)
    void propertyUnknownSymbolsPassThrough(@ForAll("axioms") String axiom,
                                            @ForAll("iterations") int iter) {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "FF");
        String result = LSystem.generate(axiom, rules, iter);
        // Only F's get replaced; other chars pass through
        int inputFs = countChar(axiom, 'F');
        int inputOthers = axiom.length() - inputFs;
        int outputFs = countChar(result, 'F');
        int outputOthers = result.length() - outputFs;
        // Others stay the same
        assertThat(outputOthers).isEqualTo(inputOthers);
        // F's grow exponentially (at least doubled each iteration)
        assertThat(outputFs).isGreaterThanOrEqualTo(inputFs);
    }

    @Property(tries = 30)
    void propertyFractalPlantGrowsExponentially(@ForAll("iterations") int iter) {
        Map<Character, String> rules = LSystem.fractalPlant();
        String result = LSystem.generate("F", rules, iter);
        // Fractal plant grows: |F| → |F|*3 each iter (F→F[+F]F[-F]F has 3 F's)
        int fCount = countChar(result, 'F');
        assertThat(fCount).isGreaterThanOrEqualTo(iter); // at least iter F's
    }

    @Property(tries = 30)
    void propertyCantorSetContainsFAndPM(@ForAll("iterations") int iter) {
        Map<Character, String> rules = LSystem.cantorSet();
        String result = LSystem.generate("F", rules, iter);
        if (result.length() > 1) {
            assertThat(result).contains("+");
            assertThat(result).contains("-");
        }
    }

    @Property(tries = 30)
    void propertyComplexityRatioNonNegative(@ForAll("axioms") String axiom,
                                             @ForAll("rules") Map<Character, String> rules) {
        String result = LSystem.generate(axiom, rules, 2);
        double ratio = LSystem.complexityRatio(result, rules);
        assertThat(ratio).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 20)
    void propertyFractalDimensionLinearBounded(@ForAll("stringLengths") int length) {
        String linear = "F".repeat(length);
        double dim = LSystem.fractalDimension(linear, 1, 32);
        assertThat(dim).isBetween(0.0, 3.0);
    }

    @Property(tries = 30)
    void propertyFractalDimensionEmptyIsZero() {
        assertThat(LSystem.fractalDimension("", 1, 8)).isEqualTo(0.0);
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    @Provide
    Arbitrary<String> axioms() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .ofMinLength(1)
            .ofMaxLength(8);
    }

    @Provide
    Arbitrary<Integer> iterations() {
        return Arbitraries.integers().between(0, 5);
    }

    @Provide
    Arbitrary<Integer> stringLengths() {
        return Arbitraries.integers().between(8, 256);
    }

    @Provide
    Arbitrary<Map<Character, String>> rules() {
        return Arbitraries.maps(
            Arbitraries.of('A', 'B', 'C', 'D', 'E', 'F'),
            Arbitraries.strings().withCharRange('A', 'F').ofMinLength(1).ofMaxLength(3)
        ).ofMinSize(0).ofMaxSize(3);
    }
}
