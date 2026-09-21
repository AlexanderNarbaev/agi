package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W167 — CognitiveHeatmap property-based tests.
 */
class CognitiveHeatmapPropertyTest {

    @Property(tries = 50)
    void propertyHeatmapDimensionsMatch(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        assertThat(heatmap.length).isEqualTo(profiles.size());
        if (!profiles.isEmpty()) {
            assertThat(heatmap[0].length).isEqualTo(CognitiveHeatmap.fieldCount());
        }
    }

    @Property(tries = 30)
    void propertyHeatmapValuesBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        for (double[] row : heatmap) {
            for (double v : row) {
                assertThat(v).isBetween(0.0, 1.0);
            }
        }
    }

    @Property(tries = 30)
    void propertyColumnMeansBounded(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        double[] means = CognitiveHeatmap.columnMeans(heatmap);
        for (double m : means) {
            assertThat(m).isBetween(0.0, 1.0);
        }
    }

    @Property(tries = 30)
    void propertyColumnVariancesNonNegative(@ForAll("profileLists") List<CognitiveGenesisProfile> profiles) {
        double[][] heatmap = CognitiveHeatmap.toHeatmap(profiles);
        double[] variances = CognitiveHeatmap.columnVariances(heatmap);
        for (double v : variances) {
            assertThat(v).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertyHeatmapDeterministicForSameProfiles(@ForAll("seeds") long seed,
                                                       @ForAll("counts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            profiles.add(makeProfile(rng));
        }
        double[][] h1 = CognitiveHeatmap.toHeatmap(profiles);
        double[][] h2 = CognitiveHeatmap.toHeatmap(profiles);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < CognitiveHeatmap.fieldCount(); j++) {
                assertThat(h1[i][j]).isEqualTo(h2[i][j]);
            }
        }
    }

    @Provide
    Arbitrary<List<CognitiveGenesisProfile>> profileLists() {
        return Arbitraries.integers().between(0, 16).flatMap(n ->
            Arbitraries.longs().between(0, Long.MAX_VALUE / 2).map(seed -> {
                List<CognitiveGenesisProfile> list = new ArrayList<>();
                Random rng = new Random(seed);
                for (int i = 0; i < n; i++) list.add(makeProfile(rng));
                return list;
            }));
    }

    @Provide
    Arbitrary<Long> seeds() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2);
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(1, 16);
    }

    private static CognitiveGenesisProfile makeProfile(Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
