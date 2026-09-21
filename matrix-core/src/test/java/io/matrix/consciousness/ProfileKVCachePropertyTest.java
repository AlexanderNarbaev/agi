package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W208 — ProfileKVCache property-based tests.
 */
class ProfileKVCachePropertyTest {

    @Property(tries = 30)
    void propertyAppendSizeMatches(@ForAll("anySeed") int seed,
                                      @ForAll("appendCounts") int n) {
        if (n < 1) return;
        ProfileKVCache cache = new ProfileKVCache(4, 16, seed);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) cache.append(randomProfile(rng));
        assertThat(cache.size()).isLessThanOrEqualTo(n);
        assertThat(cache.size()).isLessThanOrEqualTo(cache.capacity());
    }

    @Property(tries = 30)
    void propertyNeverExceedsCapacity(@ForAll("anySeed") int seed,
                                         @ForAll("appendCounts") int n,
                                         @ForAll("pageSizes") int pageSize,
                                         @ForAll("numPagess") int numPages) {
        if (n < 1 || pageSize < 1 || numPages < 1) return;
        ProfileKVCache cache = new ProfileKVCache(pageSize, numPages, seed);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) cache.append(randomProfile(rng));
        assertThat(cache.size()).isLessThanOrEqualTo(cache.capacity());
    }

    @Property(tries = 30)
    void propertyAllContainsProfiles(@ForAll("anySeed") int seed,
                                        @ForAll("appendCounts") int n) {
        if (n < 1) return;
        ProfileKVCache cache = new ProfileKVCache(4, 32, seed);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) cache.append(randomProfile(rng));
        List<CognitiveGenesisProfile> all = cache.all();
        assertThat(all.size()).isEqualTo(cache.size());
    }

    @Property(tries = 30)
    void propertyUtilizationInRange(@ForAll("anySeed") int seed,
                                       @ForAll("appendCounts") int n,
                                       @ForAll("pageSizes") int pageSize,
                                       @ForAll("numPagess") int numPages) {
        if (n < 1 || pageSize < 1 || numPages < 1) return;
        ProfileKVCache cache = new ProfileKVCache(pageSize, numPages, seed);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) cache.append(randomProfile(rng));
        double util = cache.utilization();
        assertThat(util).isBetween(0.0, 1.0 + 1e-9);
    }

    @Property(tries = 30)
    void propertyGetAfterAppend(@ForAll("anySeed") int seed,
                                   @ForAll("appendCounts") int n) {
        if (n < 1) return;
        ProfileKVCache cache = new ProfileKVCache(2, 32, seed);
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> original = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            CognitiveGenesisProfile p = randomProfile(rng);
            original.add(p);
            cache.append(p);
        }
        for (int i = 0; i < cache.size(); i++) {
            assertThat(cache.get(i)).isEqualTo(original.get(i));
        }
    }

    @Property(tries = 30)
    void propertyPageCountBounded(@ForAll("anySeed") int seed,
                                     @ForAll("appendCounts") int n,
                                     @ForAll("pageSizes") int pageSize,
                                     @ForAll("numPagess") int numPages) {
        if (n < 1 || pageSize < 1 || numPages < 1) return;
        ProfileKVCache cache = new ProfileKVCache(pageSize, numPages, seed);
        Random rng = new Random(seed);
        for (int i = 0; i < n; i++) cache.append(randomProfile(rng));
        assertThat(cache.pageCount()).isLessThanOrEqualTo(numPages);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> appendCounts() {
        return Arbitraries.integers().between(1, 50);
    }

    @Provide
    Arbitrary<Integer> pageSizes() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Integer> numPagess() {
        return Arbitraries.integers().between(1, 16);
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
