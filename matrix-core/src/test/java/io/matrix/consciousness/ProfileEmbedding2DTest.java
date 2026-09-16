package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileEmbedding2DTest {

    @Test
    void emptyProfilesReturnsEmpty() {
        assertThat(ProfileEmbedding2D.project(new ArrayList<>(), 1L)).isEmpty();
    }

    @Test
    void singleProfileReturnsSinglePoint() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, 1L);
        assertThat(points.length).isEqualTo(1);
    }

    @Test
    void correctNumberOfPoints() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, 1L);
        assertThat(points.length).isEqualTo(10);
    }

    @Test
    void trajectoryLengthNonNegative() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, 1L);
        double length = ProfileEmbedding2D.trajectoryLength(points);
        assertThat(length).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void singlePointTrajectoryZero() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, 1L);
        assertThat(ProfileEmbedding2D.trajectoryLength(points)).isEqualTo(0.0);
    }

    @Test
    void boundingBoxHasCorrectFormat() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        ProfileEmbedding2D.Point2D[] points = ProfileEmbedding2D.project(profiles, 1L);
        double[] box = ProfileEmbedding2D.boundingBox(points);
        assertThat(box.length).isEqualTo(4);
        // minX <= maxX
        assertThat(box[0]).isLessThanOrEqualTo(box[2]);
        assertThat(box[1]).isLessThanOrEqualTo(box[3]);
    }

    @Test
    void sameSeedDeterministic() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        ProfileEmbedding2D.Point2D[] p1 = ProfileEmbedding2D.project(profiles, 42L);
        ProfileEmbedding2D.Point2D[] p2 = ProfileEmbedding2D.project(profiles, 42L);
        for (int i = 0; i < p1.length; i++) {
            assertThat(p1[i].x()).isCloseTo(p2[i].x(), offset(1e-9));
            assertThat(p1[i].y()).isCloseTo(p2[i].y(), offset(1e-9));
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
