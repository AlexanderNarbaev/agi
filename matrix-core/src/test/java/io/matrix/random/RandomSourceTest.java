package io.matrix.random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 223 — RandomSource unit tests. */
class RandomSourceTest {

    @Test
    void realRngProvidesRandomness() {
        // Just verify it doesn't throw
        double a = RandomSource.REAL.nextDouble();
        assertThat(a).isBetween(0.0, 1.0);
    }

    @Test
    void seededRngIsDeterministic() {
        RandomSource.SeededRng a = new RandomSource.SeededRng(42L);
        RandomSource.SeededRng b = new RandomSource.SeededRng(42L);
        for (int i = 0; i < 100; i++) {
            assertThat(a.nextDouble()).isEqualTo(b.nextDouble());
        }
    }

    @Test
    void seededRngDifferentSeeds() {
        RandomSource.SeededRng a = new RandomSource.SeededRng(42L);
        RandomSource.SeededRng b = new RandomSource.SeededRng(43L);
        // At least one of 100 draws should differ
        boolean anyDiffer = false;
        for (int i = 0; i < 100; i++) {
            if (a.nextDouble() != b.nextDouble()) {
                anyDiffer = true;
                break;
            }
        }
        assertThat(anyDiffer).isTrue();
    }

    @Test
    void nextIntWithinBounds() {
        RandomSource.SeededRng rng = new RandomSource.SeededRng(0L);
        for (int i = 0; i < 100; i++) {
            int v = rng.nextInt(10);
            assertThat(v).isBetween(0, 9);
        }
    }

    @Test
    void currentSettingReplacesSource() {
        RandomSource.SeededRng test = new RandomSource.SeededRng(123L);
        RandomSource.setCurrent(test);
        try {
            double a = RandomSource.nextDouble();
            double b = RandomSource.nextDouble();
            // After 1 call, second call gets next seed value
            assertThat(a).isNotEqualTo(b);  // different draws
        } finally {
            RandomSource.setCurrent(RandomSource.REAL);
        }
    }
}
