package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W135 — Cognitive Genesis Profile property-based tests.
 *
 * <p>Property-based verification of W111 CognitiveGenesisProfile + W112 Builder.
 */
class CognitiveGenesisProfilePropertyTest {

    @Property(tries = 100)
    void propertyUnifiedScoreBounded(@ForAll("profiles") CognitiveGenesisProfile p) {
        double score = p.unifiedComplexityScore();
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Property(tries = 100)
    void propertyRegimeIsValidString(@ForAll("profiles") CognitiveGenesisProfile p) {
        String regime = p.regime();
        assertThat(regime).isIn("FROZEN", "EDGE_OF_CHAOS", "CHAOTIC");
    }

    @Property(tries = 50)
    void propertyHighPhiLowStabilityIsChaotic() {
        // All Φ high, stability low → CHAOTIC
        CognitiveGenesisProfile p = makeProfile(0.9, 0.9, 0.9, 0.9, 0.9, 0.1, 0.9);
        assertThat(p.regime()).isEqualTo("CHAOTIC");
    }

    @Property(tries = 50)
    void propertyLowPhiHighStabilityIsFrozen() {
        CognitiveGenesisProfile p = makeProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.9, 0.1);
        assertThat(p.regime()).isEqualTo("FROZEN");
    }

    @Property(tries = 50)
    void propertyBalancedIsEdgeOfChaos() {
        CognitiveGenesisProfile p = makeProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        assertThat(p.regime()).isEqualTo("EDGE_OF_CHAOS");
    }

    @Property(tries = 100)
    void propertyMemristorConductanceInZeroOne(@ForAll("profiles") CognitiveGenesisProfile p) {
        assertThat(p.memristorConductance()).isBetween(0.0, 1.0);
    }

    @Property(tries = 100)
    void propertyNkEdgeOfChaosKPositive(@ForAll("profiles") CognitiveGenesisProfile p) {
        assertThat(p.nkEdgeOfChaosK()).isGreaterThanOrEqualTo(0);
    }

    private static CognitiveGenesisProfile makeProfile(
            double phiB, double phiF, double phiR, double phiLG,
            double iap, double stab, double clp) {
        return new CognitiveGenesisProfile(
            phiB, phiF, phiR, phiLG, iap, stab, clp,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    @Provide
    Arbitrary<CognitiveGenesisProfile> profiles() {
        // Random values via fixed seed sequence — manual construction
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
            java.util.Random rng = new java.util.Random(seed);
            double phiB = rng.nextDouble();
            double phiF = rng.nextDouble();
            double phiR = rng.nextDouble();
            double phiLG = rng.nextDouble();
            double iap = rng.nextDouble();
            double stab = rng.nextDouble();
            double clp = rng.nextDouble();
            double k = rng.nextDouble() * 100.0;
            double anal = rng.nextDouble();
            double excl = rng.nextDouble();
            int nk = rng.nextInt(9);
            double mem = rng.nextDouble();
            double lsr = 0.5 + rng.nextDouble() * 4.5;
            return new CognitiveGenesisProfile(phiB, phiF, phiR, phiLG, iap, stab, clp,
                k, anal, excl, nk, mem, lsr);
        });
    }
}
