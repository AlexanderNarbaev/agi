package io.matrix.research;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PatternGeneratorTest {

    @Test
    void periodicProducesSinusoid() {
        Random rng = new Random(1);
        float[] p = PatternGenerator.periodic(64, 1.0, 0.0, 1.0, rng);
        assertThat(p).hasSize(64);
        // First sample should be sin(0) = 0
        assertThat(p[0]).isCloseTo(0.0f, within(1e-6f));
    }

    @Test
    void periodicRespectsFrequencyAndPhase() {
        Random rng = new Random(1);
        // freq=1.0 (one full cycle in 64 samples), phase=0:
        // p[0]=0, p[16]=sin(π/2)=1, p[32]=sin(π)=0, p[48]=sin(3π/2)=-1
        float[] p = PatternGenerator.periodic(64, 1.0, 0.0, 1.0, rng);
        assertThat(p[16]).isCloseTo(1.0f, within(1e-6f));
        assertThat(p[48]).isCloseTo(-1.0f, within(1e-6f));
    }

    @Test
    void sparseHasApproximatelyCorrectDensity() {
        Random rng = new Random(1);
        float[] p = PatternGenerator.sparse(1000, 0.03, rng);
        int ones = 0;
        for (float v : p) if (v > 0) ones++;
        // 3% of 1000 = 30
        assertThat(ones).isBetween(20, 40);
    }

    @Test
    void sparseOnlyHasPlusOneAndMinusOne() {
        float[] p = PatternGenerator.sparse(100, 0.1, new Random(1));
        for (float v : p) {
            assertThat(v).isIn(-1.0f, 1.0f);
        }
    }

    @Test
    void recurrentStartsFromNull() {
        float[] p = PatternGenerator.recurrent(null, 0.1f, new Random(1));
        assertThat(p).isNotNull();
    }

    @Test
    void recurrentIsCloseToPrevious() {
        Random rng = new Random(1);
        float[] prev = new float[64];
        for (int i = 0; i < 64; i++) prev[i] = 0.5f;
        float[] next = PatternGenerator.recurrent(prev, 0.01f, rng);
        // Small delta → should be close to 0.5
        for (int i = 0; i < 64; i++) {
            assertThat(Math.abs(next[i] - 0.5f)).isLessThan(0.1f);
        }
    }

    @Test
    void hierarchicalHasClusters() {
        Random rng = new Random(1);
        float[] p = PatternGenerator.hierarchical(64, 4, 16, rng);
        assertThat(p).hasSize(64);
        // First 16 elements should all be close to first cluster center
        float avg1 = 0;
        for (int i = 0; i < 16; i++) avg1 += p[i];
        avg1 /= 16;
        float avg2 = 0;
        for (int i = 16; i < 32; i++) avg2 += p[i];
        avg2 /= 16;
        // First two clusters should have different means (different centers)
        assertThat((double) Math.abs(avg1 - avg2)).isGreaterThan(0.1);
    }

    @Test
    void gaussianHasApproximatelyCorrectStats() {
        Random rng = new Random(1);
        float[] p = PatternGenerator.gaussian(10000, 0.0, 1.0, rng);
        double sum = 0;
        for (float v : p) sum += v;
        double mean = sum / p.length;
        assertThat(Math.abs(mean)).isLessThan(0.1);
    }

    @Test
    void generateDispatchesToAllTypes() {
        Random rng = new Random(1);
        for (PatternGenerator.Type t : PatternGenerator.Type.values()) {
            float[] p = PatternGenerator.generate(t, 32, rng);
            assertThat(p).hasSize(32);
        }
    }

    @Test
    void generateRejectsNullType() {
        assertThatThrownBy(() -> PatternGenerator.generate(null, 32, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allGeneratorsRejectZeroDims() {
        Random rng = new Random(1);
        assertThatThrownBy(() -> PatternGenerator.periodic(0, 1, 0, 1, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatternGenerator.sparse(0, 0.1, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatternGenerator.hierarchical(0, 4, 8, rng))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PatternGenerator.gaussian(0, 0, 1, rng))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateTrajectoryHasRightLength() {
        Random rng = new Random(1);
        float[][] traj = PatternGenerator.generateTrajectory(
                PatternGenerator.Type.RECURRENT, 32, 10, 0.1f, rng);
        assertThat(traj.length).isEqualTo(10);
        for (float[] p : traj) {
            assertThat(p.length).isEqualTo(32);
        }
    }

    @Test
    void recurrentTrajectoryShowsSmallDrift() {
        Random rng = new Random(1);
        float[][] traj = PatternGenerator.generateTrajectory(
                PatternGenerator.Type.RECURRENT, 32, 5, 0.01f, rng);
        // First and last should be relatively close
        double dist = 0;
        for (int i = 0; i < 32; i++) {
            dist += Math.abs(traj[0][i] - traj[4][i]);
        }
        assertThat(dist).isLessThan(5.0); // accumulated drift
    }
}
