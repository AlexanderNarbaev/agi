package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveTestTimeComputeTest {

    @Test
    void nullInputReturnsEmpty() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 3);
        CognitiveTestTimeCompute.TestTimeResult r = ttc.solve(null, 5);
        assertThat(r.bestAttempt()).isNull();
        assertThat(r.allAttempts()).isEmpty();
    }

    @Test
    void zeroStepsReturnsEmpty() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 3);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 0);
        assertThat(r.allAttempts()).isEmpty();
    }

    @Test
    void maxAttemptsOneProducesOneAttempt() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 1);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 3);
        assertThat(r.allAttempts().size()).isEqualTo(1);
    }

    @Test
    void solveProducesMultipleAttempts() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 5);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 3);
        assertThat(r.allAttempts().size()).isEqualTo(5);
    }

    @Test
    void bestAttemptHasHighestScore() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 10);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 3);
        double bestScore = r.bestAttempt().score();
        for (CognitiveTestTimeCompute.ReasoningAttempt a : r.allAttempts()) {
            assertThat(bestScore).isGreaterThanOrEqualTo(a.score());
        }
    }

    @Test
    void invalidMaxAttemptsThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveTestTimeCompute(42L, 0)
        );
    }

    @Test
    void sampleReturnsKAttempts() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 10);
        var attempts = ttc.sample(makeProfile(0.5), 3, 5);
        assertThat(attempts.size()).isEqualTo(5);
    }

    @Test
    void totalComputeRecorded() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 5);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 3);
        // 5 attempts × 3 steps = 15 total compute
        assertThat(r.totalCompute()).isEqualTo(15);
    }

    @Test
    void aggregateScoreComputed() {
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 5);
        CognitiveTestTimeCompute.TestTimeResult r =
            ttc.solve(makeProfile(0.5), 3);
        if (!Double.isNaN(r.aggregateScore())) {
            assertThat(r.aggregateScore()).isBetween(0.0, 1.0);
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
