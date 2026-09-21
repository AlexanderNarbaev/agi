package io.matrix.consciousness;

import net.jqwik.api.*;
import org.assertj.core.api.Assertions;
import java.util.ArrayList;
import java.util.List;

/**
 * W107 — Property-based tests (Wu Wenjun mechanization).
 *
 * <p>Wu Wenjun (吴文俊, 1919-2017) pioneered mechanized mathematics,
 * developing methods to mechanically verify mathematical theorems
 * via algebraic decomposition. His approach: identify the invariants
 * that MUST hold, then verify them across all inputs.
 *
 * <p>In modern terms: property-based testing (jqwik, QuickCheck).
 * Define properties (invariants) and let the framework generate
 * hundreds of test cases to find counter-examples.
 *
 * <p>H-091 hypothesis: properties/invariants established in property-based
 * tests hold for all brain states.
 *
 * <p>CONSTITUTION VI compliance: verification of measurement substrates.
 */
class PropertyBasedMetricsTest {

    @Property
    void phiBinaryIsNonNegative(@ForAll("binaryTrajectories") long[] traj) {
        double phi = IntegrationMetrics.phiBinary(traj, 2);
        Assertions.assertThat(phi).isGreaterThanOrEqualTo(-1e-9);
    }

    @Property
    void phiBinaryIsDeterministic(@ForAll("binaryTrajectories") long[] traj) {
        double phi1 = IntegrationMetrics.phiBinary(traj, 2);
        double phi2 = IntegrationMetrics.phiBinary(traj, 2);
        Assertions.assertThat(phi1).isEqualTo(phi2);
    }

    @Property
    void phiBinaryIsAtMostOne(@ForAll("binaryTrajectories") long[] traj) {
        double phi = IntegrationMetrics.phiBinary(traj, 2);
        Assertions.assertThat(phi).isLessThanOrEqualTo(1.0 + 1e-9);
    }

    @Property
    void phiFFromBitLinearIsNonNegative(@ForAll("bitActivations") int[][] acts) {
        double phi = IntegrationMetrics.phiFFromBitLinear(acts, 1);
        Assertions.assertThat(phi).isGreaterThanOrEqualTo(-1e-9);
    }

    @Property
    void phiFFromBitLinearIsDeterministic(@ForAll("bitActivations") int[][] acts) {
        double p1 = IntegrationMetrics.phiFFromBitLinear(acts, 1);
        double p2 = IntegrationMetrics.phiFFromBitLinear(acts, 1);
        Assertions.assertThat(p1).isEqualTo(p2);
    }

    @Property
    void phiRIsNonNegative(@ForAll("trajectories") long[] traj) {
        if (traj.length < 4) return;
        double phiR = IntegrationMetrics.phiR(traj, 2);
        Assertions.assertThat(phiR).isGreaterThanOrEqualTo(-1e-9);
    }

    @Property
    void kolmogorovEmptyIsZero() {
        long[] empty = new long[0];
        Assertions.assertThat(KolmogorovComplexity.estimate(empty)).isEqualTo(0.0);
    }

    @Property
    void kolmogorovSingleIs64(@ForAll("longValues") long value) {
        long[] single = {value};
        Assertions.assertThat(KolmogorovComplexity.estimate(single)).isEqualTo(64.0);
    }

    @Property
    void kolmogorovBinaryEmptyIsZero() {
        boolean[] empty = new boolean[0];
        Assertions.assertThat(KolmogorovComplexity.estimateBinary(empty)).isEqualTo(0.0);
    }

    @Property
    void kolmogorovBinaryConstantIsLow(@ForAll("bools") boolean value) {
        boolean[] seq = new boolean[10];
        for (int i = 0; i < 10; i++) seq[i] = value;
        double k = KolmogorovComplexity.estimateBinary(seq);
        Assertions.assertThat(k).isLessThanOrEqualTo(2.0);
    }

    @Property
    void analogicalBitSimilarityIsBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double sim = AnalogicalConsistency.bitSimilarity(pair[0], pair[1]);
        Assertions.assertThat(sim).isBetween(0.0, 1.0);
    }

    @Property
    void analogicalBitSimilarityIsSymmetric(@ForAll("equalLengthTrajectories") long[][] pair) {
        double simAB = AnalogicalConsistency.bitSimilarity(pair[0], pair[1]);
        double simBA = AnalogicalConsistency.bitSimilarity(pair[1], pair[0]);
        Assertions.assertThat(simAB).isCloseTo(simBA, Assertions.within(1e-12));
    }

    @Property
    void analogicalBitSimilarityIsReflexive(@ForAll("trajectories") long[] a) {
        double sim = AnalogicalConsistency.bitSimilarity(a, a);
        Assertions.assertThat(sim).isEqualTo(1.0);
    }

    @Property
    void analogicalStructuralSimilarityIsBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double sim = AnalogicalConsistency.structuralSimilarity(pair[0], pair[1]);
        Assertions.assertThat(sim).isBetween(0.0, 1.0);
    }

    @Property
    void conceptualExclusionIsBounded(@ForAll("equalLengthTrajectories") long[][] pair) {
        double excl = ConceptualExclusion.compositeExclusion(pair[0], pair[1]);
        Assertions.assertThat(excl).isBetween(0.0, 1.0);
    }

    @Property
    void conceptualExclusionIdenticalIsLow(@ForAll("trajectories") long[] a) {
        double excl = ConceptualExclusion.compositeExclusion(a, a);
        Assertions.assertThat(excl).isLessThan(0.1);
    }

    @Property
    void stabilityPhiVarianceIsNonNegative(@ForAll("trajectories") long[] traj) {
        if (traj.length < 2) return;
        List<Double> vals = new ArrayList<>();
        for (long v : traj) vals.add((double) v);
        double var = StabilityPhi.variance(vals, Math.min(2, traj.length / 2 + 1));
        Assertions.assertThat(var).isGreaterThanOrEqualTo(0.0);
    }

    @Property
    void crossLevelPhiIsFinite(@ForAll("trajectories") long[] traj) {
        if (traj.length < 4) return;
        double[][] states = new double[traj.length][2];
        for (int i = 0; i < traj.length; i++) {
            states[i][0] = (double) (traj[i] >> 32);
            states[i][1] = (double) (traj[i] & 0xFFFFFFFFL);
        }
        double[][] upper = new double[states.length][2]; for (int i = 0; i < states.length; i++) { upper[i][0] = states[i][1]; upper[i][1] = states[i][0]; } double phi = CrossLevelPhi.measure(states, upper);
        Assertions.assertThat(Double.isFinite(phi)).isTrue();
    }

    // ─── Arbitraries (input generators) ───

    @Provide
    Arbitrary<long[]> trajectories() {
        return Arbitraries.integers().between(4, 32).flatMap(t ->
            Arbitraries.longs().between(-1000, 1000).array(long[].class).ofSize(t));
    }

    @Provide
    Arbitrary<long[][]> equalLengthTrajectories() {
        return Arbitraries.integers().between(4, 32).flatMap(t ->
            Arbitraries.longs().between(-1000, 1000).array(long[].class).ofSize(t).flatMap(a ->
                Arbitraries.longs().between(-1000, 1000).array(long[].class).ofSize(t)
                    .map(b -> new long[][] { a, b })));
    }

    @Provide
    Arbitrary<long[]> binaryTrajectories() {
        return Arbitraries.integers().between(4, 16).flatMap(t ->
            Arbitraries.longs().between(0, 1).array(long[].class).ofSize(t));
    }

    @Provide
    Arbitrary<int[][]> bitActivations() {
        return Arbitraries.integers().between(4, 16).flatMap(t ->
            Arbitraries.integers().between(0, 1).array(int[].class).array(int[][].class)
                .ofMaxSize(t).map(arr -> {
                    int[][] result = new int[t][4];
                    java.util.Random rng = new java.util.Random(arr.length);
                    for (int i = 0; i < t; i++) {
                        for (int j = 0; j < 4; j++) {
                            if (i < arr.length && arr[i] != null && j < arr[i].length) {
                                result[i][j] = arr[i][j];
                            } else {
                                result[i][j] = rng.nextInt(2);
                            }
                        }
                    }
                    return result;
                }));
    }

    @Provide
    Arbitrary<Long> longValues() {
        return Arbitraries.longs().between(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2);
    }

    @Provide
    Arbitrary<Boolean> bools() {
        return Arbitraries.of(true, false);
    }
}
