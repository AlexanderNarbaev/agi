package io.matrix.research;

import io.matrix.neuron.BoyerMoore;
import io.matrix.neuron.KMeans;
import io.matrix.neuron.NaiveBayes;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 423 — Coverage for NaiveBayes, K-means, Boyer-Moore.
 */
class Exp423BayesClusterSearchTest {

    @Test
    void naiveBayesPicksModeOnSimpleBinaryFeatures() {
        // Vocab size = 2 (small bag-of-words). 2 classes.
        NaiveBayes.Training t = NaiveBayes.Training.empty(2, 2);
        // Class 0: always sees token-0
        for (int i = 0; i < 100; i++) t.increment(0, new int[]{0});
        // Class 1: always sees token-1
        for (int i = 0; i < 100; i++) t.increment(1, new int[]{1});
        // Query: token 0 — should classify as class 0
        int cls = NaiveBayes.classify(t, null, 1.0, new int[]{0});
        assertThat(cls).isEqualTo(0);
        cls = NaiveBayes.classify(t, null, 1.0, new int[]{1});
        assertThat(cls).isEqualTo(1);
    }

    @Test
    void naiveBayesHandlesLaplaceSmoothingOnUnknownTokens() {
        // Vocabulary not seen during training — Laplace smoothing saves it
        NaiveBayes.Training t = NaiveBayes.Training.empty(2, 10);
        for (int i = 0; i < 50; i++) t.increment(0, new int[]{0, 1});
        for (int i = 0; i < 50; i++) t.increment(1, new int[]{1, 2});
        // Query: token 9 (never seen) — should still produce some answer
        int cls = NaiveBayes.classify(t, null, 1.0, new int[]{9});
        assertThat(cls).isBetween(0, 1);  // doesn't crash
    }

    @Test
    void kMeansClustersIntoTwoClusters() {
        // Two clear clusters around (0,0) and (10,10)
        double[][] pts = new double[40][2];
        Random rng = new Random(0xCAFE);
        for (int i = 0; i < 40; i++) {
            int cluster = i < 20 ? 0 : 1;
            pts[i][0] = cluster * 10 + rng.nextGaussian() * 0.5;
            pts[i][1] = cluster * 10 + rng.nextGaussian() * 0.5;
        }
        KMeans.Result r = KMeans.cluster(pts, 2, 100, 1e-6, rng);
        // The two clusters should be assigned consistently
        Set<Integer> distinctClusters = new HashSet<>();
        for (int a : r.assignments()) distinctClusters.add(a);
        assertThat(distinctClusters).hasSize(2);
    }

    @Test
    void kMeansHandlesKGreaterThanN() {
        double[][] pts = {{0, 0}, {1, 1}};
        KMeans.Result r = KMeans.cluster(pts, 5, 100, 1e-6, new Random(0xCAFE));
        assertThat(r.centroids().length).isEqualTo(5);
    }

    @Test
    void boyerMooreFindsFirstMatch() {
        byte[] text = "the quick brown fox jumps over the lazy dog"
                .getBytes(StandardCharsets.UTF_8);
        byte[] pattern = "fox".getBytes(StandardCharsets.UTF_8);
        int idx = BoyerMoore.search(text, pattern);
        assertThat(idx).isEqualTo(16);
    }

    @Test
    void boyerMooreReturnsMinus1WhenNotFound() {
        byte[] text = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        byte[] pattern = "cat".getBytes(StandardCharsets.UTF_8);
        assertThat(BoyerMoore.search(text, pattern)).isEqualTo(-1);
    }

    @Test
    void boyerMooreCountsAllOccurrences() {
        byte[] text = "ababababababab".getBytes(StandardCharsets.UTF_8);
        byte[] pattern = "ab".getBytes(StandardCharsets.UTF_8);
        assertThat(BoyerMoore.countAll(text, pattern)).isEqualTo(7);
    }
}
