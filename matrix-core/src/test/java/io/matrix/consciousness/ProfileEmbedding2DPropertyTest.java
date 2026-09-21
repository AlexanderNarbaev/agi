package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W216 — ProfileEmbedding2D property-based tests.
 */
class ProfileEmbedding2DPropertyTest {

    @Property(tries = 30)
    void propertyCorrectNumberOfPoints(@ForAll("anySeed") int seed,
                                          @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, seed);
        assertThat(points.length).isEqualTo(n);
    }

    @Property(tries = 30)
    void propertySameSeedDeterministic(@ForAll("anySeed") int seed,
                                          @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        ProfileEmbedding2D.Point2D[] p1 = ProfileEmbedding2D.project(profiles, seed);
        ProfileEmbedding2D.Point2D[] p2 = ProfileEmbedding2D.project(profiles, seed);
        for (int i = 0; i < p1.length; i++) {
            assertThat(p1[i].x()).isCloseTo(p2[i].x(), offset(1e-9));
            assertThat(p1[i].y()).isCloseTo(p2[i].y(), offset(1e-9));
        }
    }

    @Property(tries = 30)
    void propertyTrajectoryLengthNonNegative(@ForAll("anySeed") int seed,
                                               @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, seed);
        double length = ProfileEmbedding2D.trajectoryLength(points);
        if (n == 1) {
            assertThat(length).isEqualTo(0.0);
        } else {
            assertThat(length).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Property(tries = 30)
    void propertyBoundingBoxValid(@ForAll("anySeed") int seed,
                                    @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, seed);
        double[] box = ProfileEmbedding2D.boundingBox(points);
        assertThat(box.length).isEqualTo(4);
        if (n == 1) {
            // Single point: all bounds equal
            assertThat(box[0]).isEqualTo(box[2]);
            assertThat(box[1]).isEqualTo(box[3]);
        } else {
            assertThat(box[0]).isLessThanOrEqualTo(box[2]);
            assertThat(box[1]).isLessThanOrEqualTo(box[3]);
        }
    }

    @Property(tries = 30)
    void propertyEmptyProfilesEmpty(@ForAll("anySeed") int seed) {
        ProfileEmbedding2D.Point2D[] points =
            ProfileEmbedding2D.project(new ArrayList<>(), seed);
        assertThat(points).isEmpty();
    }

    @Property(tries = 30)
    void propertyPointsAreFinite(@ForAll("anySeed") int seed,
                                   @ForAll("profileCounts") int n) {
        if (n < 1) return;
        Random rng = new Random(seed);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < n; i++) profiles.add(randomProfile(rng));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, seed);
        for (ProfileEmbedding2D.Point2D p : points) {
            assertThat(Double.isFinite(p.x())).isTrue();
            assertThat(Double.isFinite(p.y())).isTrue();
        }
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> profileCounts() {
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

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
