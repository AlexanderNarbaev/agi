package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W267 — Property tests for W260-W266 LLM reasoning classes.
 */
class CognitiveReasoningPropertyTest {

    @Property(tries = 30)
    void propertyCoTDefaultStepIncreasesPhi(@ForAll("anyPhi") double phi) {
        if (phi < 0 || phi > 1) return;
        CognitiveGenesisProfile p = makeProfile(phi);
        CognitiveChainOfThought.CoTResult r =
            CognitiveChainOfThought.reason(p, 1, null);
        // Default step should increase phi
        assertThat(r.finalOutput().phiBinary()).isGreaterThanOrEqualTo(phi - 1e-9);
    }

    @Property(tries = 20)
    void propertyReActRespectsMaxIterations(@ForAll("anySeed") int seed,
                                                @ForAll("anyMaxIter") int maxIter) {
        if (maxIter < 1 || maxIter > 10) return;
        CognitiveGenesisProfile p = makeProfile(0.5);
        CognitiveReAct.ReActResult r =
            CognitiveReAct.run(p, maxIter, null, null, null);
        assertThat(r.iterations()).isLessThanOrEqualTo(maxIter);
    }

    @Property(tries = 20)
    void propertyReflexionScoreBounded(@ForAll("anyPhi") double phi) {
        if (phi < 0 || phi > 1) return;
        CognitiveGenesisProfile p = makeProfile(phi);
        CognitiveReflexion.ReflexionResult r =
            CognitiveReflexion.reflect(p, 3, null);
        if (!Double.isNaN(r.finalScore())) {
            assertThat(r.finalScore()).isBetween(0.0, 1.0 + 1e-9);
        }
    }

    @Property(tries = 20)
    void propertyConstitutionalAllPrinciplesEvaluated(@ForAll("anyPhi") double phi) {
        if (phi < 0 || phi > 1) return;
        CognitiveGenesisProfile p = makeProfile(phi);
        CognitiveConstitutionalAI.ConstitutionalResult r =
            CognitiveConstitutionalAI.evaluate(p,
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        assertThat(r.critiques().size()).isEqualTo(4);
    }

    @Property(tries = 20)
    void propertyRLHFRewardBounded(@ForAll("anyPhi") double phi) {
        if (phi < 0 || phi > 1) return;
        CognitiveRLHF rlhf = new CognitiveRLHF(42L);
        CognitiveRLHF.Reward r = rlhf.reward(makeProfile(phi));
        assertThat(r.value()).isBetween(0.0, 1.0 + 1e-9);
    }

    @Provide
    Arbitrary<Double> anyPhi() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> anyMaxIter() {
        return Arbitraries.integers().between(1, 10);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
