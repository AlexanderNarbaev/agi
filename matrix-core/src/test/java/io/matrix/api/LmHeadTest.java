package io.matrix.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the RUN 10 LM head sparse Hebbian classifier.
 *
 * <p>Verifies:
 * - score() returns 0 for tokens with no weights
 * - update() then score() correctly increases the score for trained tokens
 * - Different fingerprints produce different scores
 * - Save/load round-trips weights
 */
class LmHeadTest {

    private LmHead head;

    @BeforeEach
    void setup() {
        head = new LmHead();
        head.setTotalNeurons(100);
    }

    @Test
    void emptyHeadReturnsZeroScore() {
        boolean[] fingerprint = new boolean[100];
        // All untrained tokens return 0
        assertThat(head.score(fingerprint, 42)).isEqualTo(0.0);
        assertThat(head.score(fingerprint, 100)).isEqualTo(0.0);
        assertThat(head.vocabularyCoverage()).isEqualTo(0);
    }

    @Test
    void updateIncreasesScoreForTrainedToken() {
        boolean[] fp = new boolean[100];
        // Set 30 neurons firing
        for (int i = 0; i < 30; i++) fp[i] = true;

        // Update 100 times with this fingerprint for token 42
        for (int u = 0; u < 100; u++) {
            head.update(fp, 42);
        }
        assertThat(head.vocabularyCoverage()).isEqualTo(1);

        // Score for token 42 with same fingerprint should be positive
        double score = head.score(fp, 42);
        assertThat(score).as("trained token should have positive score").isGreaterThan(0.0);

        // Score for untrained token 99 should be 0
        double untrained = head.score(fp, 99);
        assertThat(untrained).as("untrained token should have zero score").isEqualTo(0.0);
    }

    @Test
    void differentFingerprintsProduceDifferentScores() {
        boolean[] fpA = new boolean[100];
        boolean[] fpB = new boolean[100];
        for (int i = 0; i < 50; i++) fpA[i] = true;
        for (int i = 50; i < 100; i++) fpB[i] = true;

        for (int u = 0; u < 100; u++) {
            head.update(fpA, 42);
        }
        // Score for fpA and fpB should differ
        double scoreA = head.score(fpA, 42);
        double scoreB = head.score(fpB, 42);
        assertThat(scoreA).isNotEqualTo(scoreB);
    }

    @Test
    void saveLoadRoundTrip() throws Exception {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 25; i++) fp[i] = true;

        // Train
        for (int u = 0; u < 50; u++) {
            head.update(fp, 42);
            head.update(fp, 100);
        }

        // Save
        java.io.File tmp = java.io.File.createTempFile("lm-head-test", ".bin");
        tmp.deleteOnExit();
        head.save(tmp.getAbsolutePath());

        // Load into a fresh head
        LmHead head2 = new LmHead();
        head2.setTotalNeurons(100);
        head2.load(tmp.getAbsolutePath());

        assertThat(head2.vocabularyCoverage()).isEqualTo(2);
        assertThat(head2.score(fp, 42)).isGreaterThan(0.0);
        assertThat(head2.score(fp, 100)).isGreaterThan(0.0);
    }

    @Test
    void scoreIsBounded() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // Train heavily
        for (int u = 0; u < 1000; u++) {
            head.update(fp, 42);
        }
        double score = head.score(fp, 42);
        // Should be a finite, reasonable value (not NaN, not Infinity)
        assertThat(Double.isFinite(score)).isTrue();
        assertThat(Math.abs(score)).isLessThan(1000.0);  // Sanity bound
    }

    /**
     * RUN 11 — negative sampling should add negative-example tokens to vocab.
     * Without negative sampling, only positive tokens get weights and the
     * LM head collapses on the most common token.
     *
     * <p>Determinism: seed is derived from targetToken only (no wall-clock),
     * so this test produces a fixed number of unique negative tokens.
     */
    @Test
    void negativeSamplingAddsNegativeTokensToVocab() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // Train with 3 negatives per positive (deterministic seed per token)
        for (int u = 0; u < 50; u++) {
            head.update(fp, 42, 3);
        }
        // Token 42 is positive; with 3 deterministic negative samples drawn
        // from the full vocab range (200000), we expect up to 4 unique tokens.
        // The seed for negative sampling is derived only from targetToken=42
        // and the loop index, so this is fully deterministic.
        assertThat(head.vocabularyCoverage())
                .as("RUN 11: negative sampling seeds deterministically per token")
                .isBetween(2, 4);
    }

    /**
     * RUN 11 — with negative sampling, the positive token should still score
     * higher than an untrained token.
     */
    @Test
    void negativeSamplingPreservesPositiveSignal() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // Train positive 42 with 5 negatives per update
        for (int u = 0; u < 100; u++) {
            head.update(fp, 42, 5);
        }
        double posScore = head.score(fp, 42);
        // Untrained token should have score 0
        double untrained = head.score(fp, 9999);
        assertThat(posScore).isGreaterThan(untrained);
        assertThat(posScore).isGreaterThan(0.0);
        // Score should be a finite, reasonable value
        assertThat(Double.isFinite(posScore)).isTrue();
    }

    // ─── RUN 22 — signed update API ───

    @Test
    void applyUpdatePositiveIncreasesScore() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // Apply 50 positive updates with delta = +0.1
        for (int u = 0; u < 50; u++) {
            head.applyUpdate(fp, 7, +0.1);
        }
        double score = head.score(fp, 7);
        assertThat(score).as("positive signed update increases score").isGreaterThan(0.0);
        assertThat(head.positiveUpdateCount()).isEqualTo(50);
        assertThat(head.negativeUpdateCount()).isZero();
    }

    @Test
    void applyUpdateNegativeDecreasesScore() {
        // Train token 7 positively first.
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        for (int u = 0; u < 50; u++) {
            head.applyUpdate(fp, 7, +0.1);
        }
        double scoreAfterPositive = head.score(fp, 7);
        assertThat(scoreAfterPositive).isGreaterThan(0.0);

        // Now apply 100 negative updates with delta = -0.1.
        for (int u = 0; u < 100; u++) {
            head.applyUpdate(fp, 7, -0.1);
        }
        double scoreAfterNegative = head.score(fp, 7);

        // The negative delta should drive the score DOWN significantly.
        assertThat(scoreAfterNegative)
                .as("negative signed update reduces score (positive=%.4f, negative=%.4f)",
                        scoreAfterPositive, scoreAfterNegative)
                .isLessThan(scoreAfterPositive);
        assertThat(head.positiveUpdateCount()).isEqualTo(50);
        assertThat(head.negativeUpdateCount()).isEqualTo(100);
    }

    @Test
    void applyUpdateRejectsInvalidArgs() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 30; i++) fp[i] = true;
        // null chain output → false
        assertThat(head.applyUpdate(null, 42, 0.1)).isFalse();
        // negative token → false
        assertThat(head.applyUpdate(fp, -1, 0.1)).isFalse();
        // valid call → true
        assertThat(head.applyUpdate(fp, 42, 0.1)).isTrue();
        assertThat(head.positiveUpdateCount()).isEqualTo(1);
    }

    @Test
    void applyUpdateZeroDeltaDoesNotIncrementCounters() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 30; i++) fp[i] = true;
        // delta = 0 should not be counted as positive or negative
        head.applyUpdate(fp, 99, 0.0);
        assertThat(head.positiveUpdateCount()).isZero();
        assertThat(head.negativeUpdateCount()).isZero();
        // updateCount still increments (since the path was traversed)
        assertThat(head.updateCount()).isEqualTo(1);
    }

    @Test
    void updateWithNegativesCountsAsNegativeUpdates() {
        // RUN 22: when update(features, token, nNegatives) is called, each
        // negative sample now goes through applyUpdate(features, negToken, -0.01),
        // which IS counted in negativeUpdateCount.
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // 10 positives with 3 negatives each = 30 negative updates applied
        for (int u = 0; u < 10; u++) {
            head.update(fp, 42, 3);
        }
        assertThat(head.positiveUpdateCount()).isEqualTo(10);
        assertThat(head.negativeUpdateCount()).isEqualTo(30);
    }
}
