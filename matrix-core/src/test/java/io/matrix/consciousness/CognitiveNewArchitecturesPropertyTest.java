package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W337 — Property tests for W319-W332 new architectures.
 */
class CognitiveNewArchitecturesPropertyTest {

    @Property(tries = 20)
    void propertyMLACompressionRatio(@ForAll("anyDim") int dim,
                                       @ForAll("anyLatentDim") int latentDim) {
        if (dim < 8 || latentDim < 1 || latentDim > dim) return;
        CognitiveLatentAttention mla = new CognitiveLatentAttention(dim, 8, latentDim, 42L);
        double ratio = mla.compressionRatio();
        // Ratio = 2 × numHeads × headDim / latentDim = 2 × 8 × (dim/8) / latentDim = 2*dim/latentDim
        assertThat(ratio).isGreaterThan(0.0);
    }

    @Property(tries = 20)
    void propertyYaRNTemperatureReduces(@ForAll("anyTemp") double temp,
                                           @ForAll("anyScale") double scale) {
        if (temp <= 0 || scale <= 0) return;
        double t1 = CognitiveYaRN.yarnTemperature(temp, scale);
        double t2 = CognitiveYaRN.yarnTemperature(temp, scale * 2);
        // Higher scale → lower temp
        if (scale > 0) assertThat(t2).isLessThanOrEqualTo(t1 + 1e-9);
    }

    @Property(tries = 20)
    void propertyQLoRAMemoryReductionBounded(@ForAll("anyTeacherDim") int td,
                                              @ForAll("anyStudentDim") int sd) {
        if (td < 4 || sd < 1 || sd > td) return;
        CognitiveQLoRA qlora = new CognitiveQLoRA(td, sd, 42L);
        double r = qlora.memoryReduction();
        // Memory reduction is always in [0, 1] (negative = more memory)
        assertThat(r).isLessThanOrEqualTo(1.0);
    }

    @Property(tries = 20)
    void propertyHyperNetParameterCount(@ForAll("anyDim") int dim,
                                           @ForAll("anyHidden") int hidden,
                                           @ForAll("anyOutput") int out) {
        if (dim < 1 || hidden < 1 || out < 1) return;
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(dim, hidden, out, 42L);
        long count = hn.parameterCount();
        assertThat(count).isEqualTo((long) dim * out * hidden);
    }

    @Property(tries = 20)
    void propertyMRAValidOutput(@ForAll("anyNumRes") int numRes,
                                   @ForAll("anyDim") int dim) {
        if (numRes < 1 || numRes > 8 || dim < 4) return;
        CognitiveMultiResolutionAttention mra =
            new CognitiveMultiResolutionAttention(dim, numRes, 42L);
        double[] query = new double[dim];
        List<double[]> keys = new ArrayList<>();
        List<double[]> values = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double[] k = new double[dim];
            double[] v = new double[dim];
            keys.add(k);
            values.add(v);
        }
        double[] result = mra.attendMultiResolution(query, keys, values);
        assertThat(result.length).isEqualTo(dim);
    }

    @Provide
    Arbitrary<Integer> anyDim() {
        return Arbitraries.integers().between(8, 128);
    }

    @Provide
    Arbitrary<Integer> anyLatentDim() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> anyTeacherDim() {
        return Arbitraries.integers().between(8, 128);
    }

    @Provide
    Arbitrary<Integer> anyStudentDim() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> anyHidden() {
        return Arbitraries.integers().between(4, 32);
    }

    @Provide
    Arbitrary<Integer> anyOutput() {
        return Arbitraries.integers().between(2, 16);
    }

    @Provide
    Arbitrary<Integer> anyNumRes() {
        return Arbitraries.integers().between(1, 6);
    }

    @Provide
    Arbitrary<Double> anyTemp() {
        return Arbitraries.doubles().between(0.1, 2.0);
    }

    @Provide
    Arbitrary<Double> anyScale() {
        return Arbitraries.doubles().between(0.1, 10.0);
    }
}
