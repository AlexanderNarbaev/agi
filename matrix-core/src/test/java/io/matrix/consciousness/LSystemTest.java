package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LSystemTest {

    @Test
    void axiomReturnsItselfWhenNoRules() {
        String result = LSystem.generate("ABC", new HashMap<>(), 5);
        assertThat(result).isEqualTo("ABC");
    }

    @Test
    void singleIterationAppliesRule() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "FF");
        String result = LSystem.generate("F", rules, 1);
        assertThat(result).isEqualTo("FF");
    }

    @Test
    void multipleIterationsApplyRepeatedly() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "FF");
        String result = LSystem.generate("F", rules, 5);
        // F → FF → FFFF → FFFFFFFF → ... → F^(2^5) = F^32
        assertThat(result.length()).isEqualTo(32);
    }

    @Test
    void unknownSymbolsPassThrough() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "FF");
        String result = LSystem.generate("FXY", rules, 2);
        // FX+Y → FF + XY = FFXXY
        assertThat(result).isEqualTo("FFXXY");
    }

    @Test
    void fractalPlantProducesTreeLikeOutput() {
        Map<Character, String> rules = LSystem.fractalPlant();
        String result = LSystem.generate("F", rules, 3);
        // Should have brackets and plus/minus signs (tree structure)
        assertThat(result).contains("[");
        assertThat(result).contains("+");
        assertThat(result.length()).isGreaterThan(10);
    }

    @Test
    void cantorSetOutputHasGaps() {
        Map<Character, String> rules = LSystem.cantorSet();
        String result = LSystem.generate("F", rules, 3);
        // Cantor set F+F-F-F+F has both + and - signs
        assertThat(result).contains("+");
        assertThat(result).contains("-");
    }

    @Test
    void dragonCurveOutputGrowsExponentially() {
        Map<Character, String> rules = LSystem.dragonCurve();
        String iter1 = LSystem.generate("FX", rules, 1);
        String iter5 = LSystem.generate("FX", rules, 5);
        assertThat(iter5.length()).isGreaterThan(iter1.length());
    }

    @Test
    void complexityRatioIsOneForSingleCharOutput() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('X', "Y");
        double ratio = LSystem.complexityRatio("Y", rules);
        assertThat(ratio).isEqualTo(1.0);
    }

    @Test
    void complexityRatioHigherForExpansion() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('F', "FFFF");
        String output = "FFFFFFFF"; // F → FFFF iterated
        double ratio = LSystem.complexityRatio(output, rules);
        // Output length 8, rule length 4 → ratio 2.0
        assertThat(ratio).isEqualTo(2.0);
    }

    @Test
    void fractalDimensionEmptyStringIsZero() {
        assertThat(LSystem.fractalDimension("", 1, 8)).isEqualTo(0.0);
    }

    @Test
    void fractalDimensionLinearStringIsOne() {
        // A long linear string has dimension ~1 (1D)
        String linear = "F".repeat(1024);
        double dim = LSystem.fractalDimension(linear, 1, 32);
        assertThat(dim).isBetween(0.8, 1.2);
    }

    @Test
    void fractalDimensionIsInReasonableRange() {
        Map<Character, String> rules = LSystem.fractalPlant();
        String result = LSystem.generate("F", rules, 4);
        double dim = LSystem.fractalDimension(result, 1, 16);
        assertThat(dim).isBetween(0.0, 3.0);
    }
}
