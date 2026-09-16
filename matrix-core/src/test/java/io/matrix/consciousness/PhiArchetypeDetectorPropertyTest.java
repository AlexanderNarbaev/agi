package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W195 — PhiArchetypeDetector property-based tests.
 */
class PhiArchetypeDetectorPropertyTest {

    @Property(tries = 50)
    void propertyBinInRange(@ForAll("values") double v, @ForAll("nBinsList") int nBins) {
        if (nBins < 1) return;
        int b = PhiArchetypeDetector.bin(v, nBins);
        assertThat(b).isBetween(0, nBins - 1);
    }

    @Property(tries = 50)
    void propertyBinHandlesOutOfRange(@ForAll("values") double v,
                                       @ForAll("nBinsList") int nBins) {
        if (nBins < 1) return;
        int b = PhiArchetypeDetector.bin(v, nBins);
        assertThat(b).isBetween(0, nBins - 1);
    }

    @Property(tries = 50)
    void propertySignatureLength13(@ForAll("profileFactories") CognitiveGenesisProfile p,
                                     @ForAll("nBinsList") int nBins) {
        int[] sig = PhiArchetypeDetector.signature(p, nBins);
        assertThat(sig.length).isEqualTo(13);
    }

    @Property(tries = 50)
    void propertySignatureValuesInRange(@ForAll("profileFactories") CognitiveGenesisProfile p,
                                          @ForAll("nBinsList") int nBins) {
        if (nBins < 1) return;
        int[] sig = PhiArchetypeDetector.signature(p, nBins);
        for (int v : sig) {
            assertThat(v).isBetween(0, nBins - 1);
        }
    }

    @Property(tries = 30)
    void propertyFindArchetypesTotalCountMatches(@ForAll("anySeed") int seed,
                                                    @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        List<PhiArchetypeDetector.Archetype> archetypes =
            PhiArchetypeDetector.findArchetypes(profiles, 4);
        int total = archetypes.stream().mapToInt(PhiArchetypeDetector.Archetype::count).sum();
        assertThat(total).isEqualTo(n);
    }

    @Property(tries = 30)
    void propertyFindArchetypesSortedByCount(@ForAll("anySeed") int seed,
                                                @ForAll("profileCounts") int n) {
        if (n < 2) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        List<PhiArchetypeDetector.Archetype> archetypes =
            PhiArchetypeDetector.findArchetypes(profiles, 4);
        // Should be sorted by count descending
        for (int i = 1; i < archetypes.size(); i++) {
            assertThat(archetypes.get(i).count()).isLessThanOrEqualTo(archetypes.get(i - 1).count());
        }
    }

    @Property(tries = 30)
    void propertyEmptyProfilesReturnsEmpty(@ForAll("nBinsList") int nBins) {
        assertThat(PhiArchetypeDetector.findArchetypes(new ArrayList<>(), nBins)).isEmpty();
    }

    @Property(tries = 30)
    void propertyTopArchetypesReturnsAtMostK(@ForAll("anySeed") int seed,
                                                @ForAll("profileCounts") int n,
                                                @ForAll("ks") int k) {
        if (n < 1 || k < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        List<PhiArchetypeDetector.Archetype> top =
            PhiArchetypeDetector.topArchetypes(profiles, 4, k);
        assertThat(top.size()).isLessThanOrEqualTo(k);
    }

    @Provide
    Arbitrary<Double> values() {
        return Arbitraries.doubles().between(-1.0, 2.0);  // includes out-of-range
    }

    @Provide
    Arbitrary<Integer> nBinsList() {
        return Arbitraries.integers().between(2, 16);
    }

    @Provide
    Arbitrary<Integer> ks() {
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<Integer> profileCounts() {
        return Arbitraries.integers().between(1, 16);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<CognitiveGenesisProfile> profileFactories() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
            Random rng = new Random(seed);
            return randomProfile(rng);
        });
    }

    private static CognitiveGenesisProfile randomProfile(Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble() * 100.0, rng.nextDouble(), rng.nextDouble(),
            rng.nextInt(8), rng.nextDouble(), rng.nextDouble() * 5.0
        );
    }
}
