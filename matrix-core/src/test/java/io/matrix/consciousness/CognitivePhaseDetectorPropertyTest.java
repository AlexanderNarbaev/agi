package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W154 — CognitivePhaseDetector property-based tests.
 */
class CognitivePhaseDetectorPropertyTest {

    @Property(tries = 50)
    void propertyTransitionRateBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double rate = CognitivePhaseDetector.transitionRate(profiles);
        assertThat(rate).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyDominantRegimeValid(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        String dom = CognitivePhaseDetector.dominantRegime(profiles);
        assertThat(dom).isIn("FROZEN", "EDGE_OF_CHAOS", "CHAOTIC", "UNKNOWN");
    }

    @Property(tries = 30)
    void propertyNoTransitionsForAllSame(@ForAll("anySeed") int seed,
                                          @ForAll("lengths") int n) {
        if (n < 2) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        Random rng = new Random(seed);
        // All EDGE_OF_CHAOS
        for (int i = 0; i < n; i++) profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThat(CognitivePhaseDetector.detectTransitions(profiles)).isEmpty();
        assertThat(CognitivePhaseDetector.transitionRate(profiles)).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyAlternatingCyclesMaxTransitions(@ForAll("anySeed") int seed,
                                                   @ForAll("lengths") int n) {
        if (n < 2) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        // Alternating FROZEN and CHAOTIC → max transitions
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                profiles.add(makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1));  // FROZEN
            } else {
                profiles.add(makeProfile(0.9, 0.9, 0.9, 0.9, 0.9, 0.1, 0.9));  // CHAOTIC
            }
        }
        int nTransitions = CognitivePhaseDetector.detectTransitions(profiles).size();
        // n-1 transitions (every consecutive pair differs)
        assertThat(nTransitions).isEqualTo(n - 1);
        assertThat(CognitivePhaseDetector.transitionRate(profiles)).isEqualTo(1.0);
    }

    @Property(tries = 30)
    void propertyDominantOfAllEdgeIsEdge(@ForAll("anySeed") int seed,
                                          @ForAll("lengths") int n) {
        if (n < 1) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5));
        assertThat(CognitivePhaseDetector.dominantRegime(profiles)).isEqualTo("EDGE_OF_CHAOS");
    }

    @Provide
    Arbitrary<List<CognitiveGenesisProfile>> profileLists() {
        return Arbitraries.integers().between(0, 16).flatMap(n ->
            Arbitraries.longs().between(0, Long.MAX_VALUE / 2).array(long[].class).ofSize(n)
                .map(seeds -> {
                    List<CognitiveGenesisProfile> list = new ArrayList<>();
                    Random rng = new Random(seeds[0]);
                    for (int i = 0; i < seeds.length; i++) {
                        double phiB = rng.nextDouble();
                        double phiF = rng.nextDouble();
                        double phiR = rng.nextDouble();
                        double phiLG = rng.nextDouble();
                        double iap = rng.nextDouble();
                        double stab = rng.nextDouble();
                        double clp = rng.nextDouble();
                        list.add(new CognitiveGenesisProfile(phiB, phiF, phiR, phiLG,
                            iap, stab, clp, 50.0, 0.5, 0.5, 2, 0.5, 2.0));
                    }
                    return list;
                }));
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    private static CognitiveGenesisProfile makeProfile(
            double phiB, double phiF, double phiR, double phiLG,
            double iap, double stab, double clp) {
        return new CognitiveGenesisProfile(
            phiB, phiF, phiR, phiLG, iap, stab, clp,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
