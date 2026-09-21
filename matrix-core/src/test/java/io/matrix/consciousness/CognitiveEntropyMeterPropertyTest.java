package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W156 — CognitiveEntropyMeter property-based tests.
 */
class CognitiveEntropyMeterPropertyTest {

    @Property(tries = 50)
    void propertyRegimeEntropyNonNegative(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double h = CognitiveEntropyMeter.regimeEntropy(profiles);
        assertThat(h).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyRegimeEntropyBoundedByMax(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double h = CognitiveEntropyMeter.regimeEntropy(profiles);
        assertThat(h).isLessThanOrEqualTo(CognitiveEntropyMeter.maxEntropy() + 1e-9);
    }

    @Property(tries = 50)
    void propertyNormalizedEntropyBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double n = CognitiveEntropyMeter.normalizedRegimeEntropy(profiles);
        assertThat(n).isBetween(0.0, 1.0);
    }

    @Property(tries = 30)
    void propertySingleProfileZeroEntropy(@ForAll("profileFactories") CognitiveGenesisProfile p) {
        List<CognitiveGenesisProfile> list = new ArrayList<>();
        list.add(p);
        assertThat(CognitiveEntropyMeter.regimeEntropy(list)).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyProfileEntropyNonNegative(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double h = CognitiveEntropyMeter.profileEntropy(profiles);
        assertThat(h).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyAllSameRegimeZeroEntropy(@ForAll("anySeed") int seed,
                                            @ForAll("lengths") int n) {
        if (n < 1) return;
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // EDGE_OF_CHAOS
            profiles.add(new CognitiveGenesisProfile(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
                50.0, 0.5, 0.5, 2, 0.5, 2.0));
        }
        assertThat(CognitiveEntropyMeter.regimeEntropy(profiles)).isEqualTo(0.0);
    }

    @Provide
    Arbitrary<List<CognitiveGenesisProfile>> profileLists() {
        return Arbitraries.integers().between(0, 16).flatMap(n ->
            Arbitraries.longs().between(0, Long.MAX_VALUE / 2).array(long[].class).ofSize(Math.max(n, 1))
                .map(seeds -> {
                    List<CognitiveGenesisProfile> list = new ArrayList<>();
                    Random rng = new Random(seeds[0]);
                    for (int i = 0; i < n; i++) {
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
    Arbitrary<CognitiveGenesisProfile> profileFactories() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
            Random rng = new Random(seed);
            return new CognitiveGenesisProfile(rng.nextDouble(), rng.nextDouble(),
                rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
                rng.nextDouble(), 50.0, 0.5, 0.5, 2, 0.5, 2.0);
        });
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
