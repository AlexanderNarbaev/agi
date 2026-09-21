package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W271 — Additional property tests for LLM architecture (W238-W269).
 */
class CognitiveLLMArchitecturePropertyTest2 {

    @Property(tries = 30)
    void propertyDistillPreservesDim(@ForAll("anySeed") int seed,
                                       @ForAll("anyTeacherDim") int td,
                                       @ForAll("anyStudentDim") int sd) {
        if (td < 16 || sd < 1 || sd > td) return;
        CognitiveDistillation d = new CognitiveDistillation(td, sd, seed);
        double[] v = new double[td];
        Random rng = new Random(seed);
        for (int i = 0; i < td; i++) v[i] = rng.nextDouble();
        double[] result = d.distill(v);
        assertThat(result.length).isEqualTo(sd);
    }

    @Property(tries = 30)
    void propertyMoESelectedValidIndices(@ForAll("anySeed") int seed,
                                           @ForAll("anyNumExperts") int nExp,
                                           @ForAll("anyTopK") int k) {
        if (nExp < 2 || k < 1 || k > nExp) return;
        CognitiveMixtureOfExperts.MoEResult r =
            CognitiveMixtureOfExperts.route(makeProfile(0.5),
                makeExperts(nExp), k, seed);
        for (int idx : r.selectedExperts()) {
            assertThat(idx).isBetween(0, nExp - 1);
        }
    }

    @Property(tries = 30)
    void propertySparseAttentionOOfN(@ForAll("anySeed") int seed,
                                         @ForAll("anySeqLen") int n) {
        if (n < 1 || n > 100) return;
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, seed);
        int ops = sa.totalOps(n);
        // Should be O(N), not O(N²)
        assertThat(ops).isLessThanOrEqualTo(n * 10);
    }

    @Property(tries = 30)
    void propertyToolUseRegisterInvoke(@ForAll("anySeed") int seed) {
        CognitiveToolUse tu = new CognitiveToolUse();
        String toolName = "t_" + seed;
        tu.register(new CognitiveToolUse.Tool(toolName, "test",
            params -> "ok"));
        CognitiveToolUse.ToolResult r = tu.invoke(toolName,
            new java.util.HashMap<>());
        assertThat(r.success()).isTrue();
    }

    @Property(tries = 30)
    void propertyConstitutionalBoundedScore(@ForAll("anyPhi") double phi) {
        if (phi < 0 || phi > 1) return;
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(makeProfile(phi),
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.0);
        if (!Double.isNaN(r.averageScore())) {
            assertThat(r.averageScore()).isBetween(0.0, 1.0 + 1e-9);
        }
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anyTeacherDim() {
        return Arbitraries.integers().between(16, 256);
    }

    @Provide
    Arbitrary<Integer> anyStudentDim() {
        return Arbitraries.integers().between(1, 64);
    }

    @Provide
    Arbitrary<Integer> anyNumExperts() {
        return Arbitraries.integers().between(2, 16);
    }

    @Provide
    Arbitrary<Integer> anyTopK() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<Integer> anySeqLen() {
        return Arbitraries.integers().between(1, 50);
    }

    @Provide
    Arbitrary<Double> anyPhi() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static java.util.List<CognitiveMixtureOfExperts.Expert> makeExperts(int n) {
        java.util.List<CognitiveMixtureOfExperts.Expert> list = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) list.add(new CognitiveMixtureOfExperts.Expert("e" + i));
        return list;
    }
}
