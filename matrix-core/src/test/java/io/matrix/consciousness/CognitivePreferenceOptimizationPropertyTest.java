package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W279 — Property tests for W273-W278 preference optimization classes.
 */
class CognitivePreferenceOptimizationPropertyTest {

    @Property(tries = 30)
    void propertyDPOLossNonNegative(@ForAll("anyLogProbChosen") double lpc,
                                       @ForAll("anyLogProbRejected") double lpr) {
        CognitiveDPO dpo = new CognitiveDPO(42L);
        double loss = dpo.loss(lpc, lpr, Math.log(0.5), Math.log(0.5));
        if (!Double.isNaN(loss)) {
            assertThat(loss).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertySimPOLossNonNegative(@ForAll("anyLogProbChosen") double lpc,
                                          @ForAll("anyLogProbRejected") double lpr) {
        CognitiveSimPO simpo = new CognitiveSimPO(42L);
        double loss = simpo.loss(lpc, lpr, 1.0);
        if (!Double.isNaN(loss)) {
            assertThat(loss).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertyGRPOAdvantagesMeanZero(@ForAll("anySeed") int seed,
                                            @ForAll("groupSizes") int n) {
        if (n < 2) return;
        java.util.Random rng = new java.util.Random(seed);
        List<Double> rewards = new ArrayList<>();
        for (int i = 0; i < n; i++) rewards.add(rng.nextDouble());
        CognitiveGRPO grpo = new CognitiveGRPO(42L);
        CognitiveGRPO.GRPOGroup r = grpo.computeGroup(rewards);
        double mean = 0;
        for (double a : r.advantages()) mean += a;
        mean /= r.advantages().size();
        assertThat(mean).isCloseTo(0.0, offset(1e-9));
    }

    @Property(tries = 30)
    void propertyIPOLossNonNegative(@ForAll("anyLogProbChosen") double lpc,
                                       @ForAll("anyLogProbRejected") double lpr) {
        CognitiveIPO ipo = new CognitiveIPO(0.1);
        double loss = ipo.loss(lpc, lpr, Math.log(0.5), Math.log(0.5));
        if (!Double.isNaN(loss)) {
            assertThat(loss).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertyKTOLossBoundedByLambda(@ForAll("anyLogProb") double lp,
                                           @ForAll("anyBeta") double beta,
                                           @ForAll("anyLambdaD") double lambdaD) {
        if (beta < 0 || lambdaD < 0 || Double.isNaN(beta) || Double.isNaN(lambdaD)) return;
        CognitiveKTO kto = new CognitiveKTO(beta, lambdaD, lambdaD, 0.0);
        double loss = kto.loss(lp, true);
        if (!Double.isNaN(loss)) {
            // sigmoid is bounded by [0, 1], so loss ≤ lambdaD
            assertThat(loss).isLessThanOrEqualTo(lambdaD + 1e-9);
        }
    }

    @Provide
    Arbitrary<Double> anyLogProbChosen() {
        return Arbitraries.doubles().between(-5.0, 0.0);
    }

    @Provide
    Arbitrary<Double> anyLogProbRejected() {
        return Arbitraries.doubles().between(-5.0, 0.0);
    }

    @Provide
    Arbitrary<Double> anyLogProb() {
        return Arbitraries.doubles().between(-5.0, 0.0);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> groupSizes() {
        return Arbitraries.integers().between(2, 10);
    }

    @Provide
    Arbitrary<Double> anyBeta() {
        return Arbitraries.doubles().between(0.01, 1.0);
    }

    @Provide
    Arbitrary<Double> anyLambdaD() {
        return Arbitraries.doubles().between(0.1, 5.0);
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
