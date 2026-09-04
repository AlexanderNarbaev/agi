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
     */
    @Test
    void negativeSamplingAddsNegativeTokensToVocab() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // Train with 3 negatives per positive
        for (int u = 0; u < 50; u++) {
            head.update(fp, 42, 3);
        }
        // Vocabulary should contain the positive token AND the 3 sampled negatives
        assertThat(head.vocabularyCoverage()).isGreaterThanOrEqualTo(4);
    }

    /**
     * RUN 11 — with negative sampling, the positive token should still score
     * higher than an untrained token.
     */
    @Test
    void negativeSamplingPreservesPositiveSignal() {
        boolean[] fp = new boolean[100];
        for (int i = 0; i < 50; i++) fp[i] = true;
        // Train positive 42 with negatives
        for (int u = 0; u < 100; u++) {
            head.update(fp, 42, 5);
        }
        double posScore = head.score(fp, 42);
        // Untrained token should have score 0
        double untrained = head.score(fp, 9999);
        assertThat(posScore).isGreaterThan(untrained);
        assertThat(posScore).isGreaterThan(0.0);
    }
}
