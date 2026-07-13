package io.matrix.explainability;

import io.matrix.neuron.TruthTable;
import io.matrix.neuron.WeightVector;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExplanationGeneratorTest {

    private final ExplanationGenerator generator = new ExplanationGenerator();

    @Test
    void shouldGenerateFullExplanation() {
        // OR function: output = 1 if any input is 1
        // Truth table for k=2: 00→0, 01→1, 10→1, 11→1 => bits = 0110 (6)
        TruthTable tt = TruthTable.fromLong(2, 0b0110L);
        BitSet input = new BitSet(2);
        input.set(0); // input = 01

        DecisionProvenance provenance = generator.explain(tt, input, true);

        assertThat(provenance).isNotNull();
        assertThat(provenance.output()).isTrue();
        assertThat(provenance.inputK()).isEqualTo(2);
        assertThat(provenance.hasExplanations()).isTrue();
        assertThat(provenance.explanationPrimitives()).isNotEmpty();
        assertThat(provenance.explanationResults()).isNotEmpty();
    }

    @Test
    void shouldComputeAttribution() {
        // XOR function: k=2, output flips for any input change
        TruthTable tt = TruthTable.fromLong(2, 0b0110L);
        BitSet input = new BitSet(2);
        input.set(0);

        Map<Integer, Double> attr = generator.computeAttribution(tt, input);

        assertThat(attr).hasSize(2);
        // For XOR, both bits have 100% attribution (any flip changes output)
        assertThat(attr.get(0)).isEqualTo(1.0);
        assertThat(attr.get(1)).isEqualTo(1.0);
    }

    @Test
    void shouldComputeAttributionForConstantFunction() {
        // Constant true: output never changes regardless of input
        TruthTable tt = TruthTable.fromLong(2, 0b1111L);
        BitSet input = new BitSet(2);

        Map<Integer, Double> attr = generator.computeAttribution(tt, input);

        assertThat(attr.get(0)).isEqualTo(0.0);
        assertThat(attr.get(1)).isEqualTo(0.0);
    }

    @Test
    void shouldComputeWeightSensitivity() {
        WeightVector weights = new WeightVector(new int[]{3, 1});
        TruthTable tt = TruthTable.fromLong(2, 0b0110L, weights);
        BitSet input = new BitSet(2);
        input.set(0);

        double sensitivity = generator.computeWeightSensitivity(tt, input);

        assertThat(sensitivity).isBetween(0.0, 1.0);
    }

    @Test
    void shouldReturnZeroSensitivityForNoWeights() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L);
        BitSet input = new BitSet(2);

        double sensitivity = generator.computeWeightSensitivity(tt, input);

        assertThat(sensitivity).isEqualTo(0.0);
    }

    @Test
    void shouldComputeInputAblation() {
        // AND function: 00→0, 01→0, 10→0, 11→1
        TruthTable tt = TruthTable.fromLong(2, 0b1000L);
        BitSet input = new BitSet(2);
        input.set(0);
        input.set(1); // input = 11

        Map<Integer, Boolean> ablation = generator.computeInputAblation(tt, input, true);

        assertThat(ablation).hasSize(2);
        // For AND(11)=1, ablating either bit changes output to 0
        assertThat(ablation.get(0)).isTrue();
        assertThat(ablation.get(1)).isTrue();
    }

    @Test
    void shouldComputeBooleanGradient() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L); // XOR
        BitSet input = new BitSet(2);

        boolean[] gradient = generator.computeBooleanGradient(tt, input, 2);

        assertThat(gradient).hasSize(2);
        // For XOR at input 00, flipping any bit changes output
        assertThat(gradient[0]).isTrue();
        assertThat(gradient[1]).isTrue();
    }

    @Test
    void shouldExtractCommonPatterns() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L); // XOR

        Map<Integer, Integer> patterns = generator.extractCommonPatterns(tt, true, 2);

        // XOR produces true for inputs 01 (index 1) and 10 (index 2)
        assertThat(patterns).containsKeys(1, 2);
        assertThat(patterns.get(1)).isEqualTo(1);
        assertThat(patterns.get(2)).isEqualTo(1);
    }

    @Test
    void shouldComputeClusterCentroid() {
        // OR function: true for 01, 10, 11
        TruthTable tt = TruthTable.fromLong(2, 0b1110L);

        int[] centroid = generator.computeClusterCentroid(tt, true, 2);

        // Majority of true-output inputs have bit0=1 (01, 11) and bit1=1 (10, 11)
        assertThat(centroid[0]).isEqualTo(1);
        assertThat(centroid[1]).isEqualTo(1);
    }

    @Test
    void shouldFindDecisionBoundaryPrototype() {
        // AND function: 00→0, 01→0, 10→0, 11→1
        TruthTable tt = TruthTable.fromLong(2, 0b1000L);
        BitSet input = new BitSet(2);
        input.set(0);
        input.set(1); // input = 11

        int[] boundary = generator.findDecisionBoundaryPrototype(tt, input, 2);

        // Closest to 11 that produces 0 should be 01 or 10 (Hamming distance 1)
        int boundaryVal = boundary[0] | (boundary[1] << 1);
        assertThat(boundaryVal).isIn(1, 2); // 01 or 10
    }

    @Test
    void shouldComputeInputGrouping() {
        TruthTable tt = TruthTable.fromLong(3, 0b11110000L); // depends only on bit2

        List<List<Integer>> groups = generator.computeInputGrouping(tt, 3);

        assertThat(groups).isNotEmpty();
        // All input bits should appear in exactly one group
        int totalBits = groups.stream().mapToInt(List::size).sum();
        assertThat(totalBits).isEqualTo(3);
        // Each bit should be in some group
        boolean hasAllBits = groups.stream()
                .flatMap(List::stream)
                .distinct()
                .count() == 3;
        assertThat(hasAllBits).isTrue();
    }

    @Test
    void shouldClassifyFunctionalRoles() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L); // XOR
        BitSet input = new BitSet(2);

        Map<Integer, String> roles = generator.classifyFunctionalRoles(tt, input, 2);

        assertThat(roles).hasSize(2);
        // For XOR, both bits are modulatory (flipping can go either way)
        assertThat(roles.values()).allMatch(r ->
                r.equals("EXCITATORY") || r.equals("INHIBITORY") || r.equals("MODULATORY"));
    }

    @Test
    void shouldFindMinimalPerturbation() {
        // AND function: 11→1
        TruthTable tt = TruthTable.fromLong(2, 0b1000L);
        BitSet input = new BitSet(2);
        input.set(0);
        input.set(1);

        int[] perturbation = generator.findMinimalPerturbation(tt, input, true, 2);

        // Should find a single-bit flip that changes output
        int pertVal = perturbation[0] | (perturbation[1] << 1);
        assertThat(pertVal).isIn(1, 2); // 01 or 10
    }

    @Test
    void shouldComputeWhatIf() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L); // XOR
        BitSet input = new BitSet(2);
        input.set(0);

        Map<String, Boolean> whatIf = generator.computeWhatIf(tt, input, 2);

        assertThat(whatIf).containsKey("original");
        assertThat(whatIf).containsKey("bit_0_flipped");
        assertThat(whatIf).containsKey("bit_1_flipped");
        assertThat(whatIf.get("original")).isTrue(); // XOR(01)=1
    }

    @Test
    void shouldTraceDecisionPath() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L); // XOR
        BitSet input = new BitSet(2);
        input.set(0);

        String path = generator.traceDecisionPath(tt, input, 2);

        assertThat(path).contains("DecisionPath[");
        assertThat(path).contains("b0=1");
        assertThat(path).contains("b1=0");
        assertThat(path).contains("output=true");
    }

    @Test
    void shouldMinimizeTruthTable() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L); // XOR

        String minimized = generator.minimizeTruthTable(tt, 2);

        assertThat(minimized).isNotEqualTo("0");
        assertThat(minimized).isNotEqualTo("1");
        assertThat(minimized).contains("·"); // minterm separator
    }

    @Test
    void shouldMinimizeConstantTrue() {
        TruthTable tt = TruthTable.fromLong(2, 0b1111L);

        String minimized = generator.minimizeTruthTable(tt, 2);

        assertThat(minimized).isEqualTo("1");
    }

    @Test
    void shouldMinimizeConstantFalse() {
        TruthTable tt = TruthTable.fromLong(2, 0b0000L);

        String minimized = generator.minimizeTruthTable(tt, 2);

        assertThat(minimized).isEqualTo("0");
    }

    @Test
    void shouldSerializeAndDeserializeProvenance() {
        TruthTable tt = TruthTable.fromLong(2, 0b0110L);
        BitSet input = new BitSet(2);
        input.set(0);

        DecisionProvenance provenance = generator.explain(tt, input, true);
        String json = provenance.toJson();
        DecisionProvenance deserialized = DecisionProvenance.fromJson(json);

        assertThat(deserialized.decisionId()).isEqualTo(provenance.decisionId());
        assertThat(deserialized.output()).isEqualTo(provenance.output());
        assertThat(deserialized.inputK()).isEqualTo(provenance.inputK());
        assertThat(deserialized.explanationPrimitives())
                .hasSameElementsAs(provenance.explanationPrimitives());
    }

    @Test
    void shouldHandleHigherKValues() {
        TruthTable tt = TruthTable.fromLong(4, 0b1111111111111110L); // OR of 4 bits
        BitSet input = new BitSet(4);

        DecisionProvenance provenance = generator.explain(tt, input, false);

        assertThat(provenance.inputK()).isEqualTo(4);
        assertThat(provenance.hasExplanations()).isTrue();
    }

    @Test
    void shouldComputeWeightCounterfactual() {
        WeightVector weights = new WeightVector(new int[]{2, 3});
        TruthTable tt = TruthTable.fromLong(2, 0b0110L, weights);
        BitSet input = new BitSet(2);
        input.set(0);

        Map<String, Boolean> counterfactual = generator.computeWeightCounterfactual(tt, input, 2);

        assertThat(counterfactual).containsKey("original");
        assertThat(counterfactual.size()).isGreaterThan(1);
    }
}
