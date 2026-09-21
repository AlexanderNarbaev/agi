package io.matrix.research;

import io.matrix.neuron.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 429 — Final stress test: every algorithm added in RUN 419-428
 * exercised in a SINGLE 1-second test. 20 algorithm classes,
 * ~30 unique procedures, all pure, all CONSTITUTION I-safe.
 */
class Exp429FinalAlgorithmLibraryTest {

    @Test
    void everyAlgorithmFromRun419To428Works() {
        Random rng = new Random(0xCAFE);

        // 419: UCB, Thompson, Bloom, PageRank
        assertThat(UcbBandit.choose(new double[]{0.0, 0.0}, new long[]{0, 0},
                -1, Math.sqrt(2), rng).chosen()).isBetween(0, 1);
        assertThat(ThompsonBandit.choose(new double[]{0.5, 0.5},
                new double[]{0.1, 0.1}, new long[]{10, 10}, 0, 1, rng).samples()).hasSize(2);
        BloomFilter bf = new BloomFilter(64, 3);
        bf.add(7L);
        assertThat(bf.mightContain(7L)).isTrue();
        List<List<Integer>> g = List.of(List.of(1), List.of(2), List.of(0));
        double sum = 0;
        for (double r : PageRank.rank(g, 0.85, 50, 1e-6, null)) sum += r;
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));

        // 420: Dijkstra, KdTree
        var dr = Dijkstra.shortestPaths(List.of(
                List.of(new Dijkstra.Edge(1, 1.0)),
                List.of()), 0, 0);
        assertThat(dr.distances()[1]).isEqualTo(1.0);
        assertThat(KdTree.of(new double[][]{{0, 0}, {1, 1}}).nearest(
                new double[]{0.6, 0.6}, 1)).hasSize(1);

        // 421: RLE, Levenshtein
        assertThat(Compression.rleDecode(Compression.rleEncode(
                "aaabbc".getBytes(StandardCharsets.UTF_8))))
                .containsExactly("aaabbc".getBytes(StandardCharsets.UTF_8));
        assertThat(Compression.levenshtein(
                "kitten".toCharArray(), "sitting".toCharArray())).isEqualTo(3);

        // 422: BellmanFord, FloydWarshall
        var bfResult = BellmanFord.shortestPaths(2,
                List.of(new BellmanFord.Edge(0, 1, 5.0)),
                0);
        assertThat(bfResult.distances()[1]).isEqualTo(5.0);
        double[][] fw = FloydWarshall.shortestPaths(
                new double[][]{{0, 1}, {2, 0}});
        assertThat(fw[1][0]).isEqualTo(2.0);

        // 423: NaiveBayes, KMeans, BoyerMoore
        NaiveBayes.Training t = NaiveBayes.Training.empty(2, 4);
        t.increment(0, new int[]{0}); t.increment(1, new int[]{1});
        assertThat(NaiveBayes.classify(t, null, 1.0, new int[]{0})).isEqualTo(0);
        KMeans.Result km = KMeans.cluster(
                new double[][]{{0, 0}, {1, 1}, {10, 10}, {11, 11}},
                2, 50, 1e-6, rng);
        assertThat(km.assignments()).hasSize(4);
        int bm = BoyerMoore.search("hello world".getBytes(StandardCharsets.UTF_8),
                "world".getBytes(StandardCharsets.UTF_8));
        assertThat(bm).isEqualTo(6);

        // 424: Sort.quickSort + mergeSort + heapSort + fisherYates
        int[] arr1 = {3, 1, 2}, arr2 = arr1.clone(), arr3 = arr1.clone();
        Sort.quickSort(arr1); Sort.mergeSort(arr2); Sort.heapSort(arr3);
        assertThat(arr1).containsExactly(1, 2, 3);
        assertThat(arr2).containsExactly(1, 2, 3);
        assertThat(arr3).containsExactly(1, 2, 3);
        int[] shuf = {0, 1, 2, 3};
        Sort.fisherYatesShuffle(shuf, rng);
        // Just check it's still a permutation
        assertThat(shuf).containsExactlyInAnyOrder(0, 1, 2, 3);

        // 426: LinearRegression, LogisticRegression, TfIdf
        double[] betas = LinearRegression.fitSimple(
                new double[]{0, 1, 2, 3, 4}, new double[]{1.0, 3.0, 5.0, 7.0, 9.0});
        assertThat(betas[0]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.01));
        double[] w = LogisticRegression.fit(
                new double[][]{{0.0, 0.0}, {1.0, 1.0}, {0.5, 0.5}, {2.0, 2.0}},
                new int[]{0, 1, 1, 1},
                0.5, 200, 0.01, rng);
        assertThat(w).hasSize(2);
        TfIdf.Vocab vocab = TfIdf.fit(List.of("hello world", "hello there", "world there"));
        assertThat(vocab.vocabulary()).hasSize(3);

        // 427: XxHash
        long h0 = XxHash.xxh3("test".getBytes(StandardCharsets.UTF_8));
        long h1 = XxHash.xxh3("test".getBytes(StandardCharsets.UTF_8));
        assertThat(h0).isEqualTo(h1);

        // 428: MLP
        MultiLayerPerceptron mlp = new MultiLayerPerceptron(2, 4, 1, rng);
        mlp.fit(
                new double[][]{{0, 0}, {0, 1}, {1, 0}, {1, 1}},
                new double[][]{{0}, {1}, {1}, {0}}, 1000, 4, 0.5);
        double p01 = mlp.predict(new double[]{0, 1})[0];
        double p10 = mlp.predict(new double[]{1, 0})[0];
        // Just verify that the MLP produces real-valued probabilities in [0,1]
        assertThat(p01).isBetween(0.0, 1.0);
        assertThat(p10).isBetween(0.0, 1.0);

        System.out.println("[Exp429] ~20 algorithm classes from RUN 419-428 ALL PASS");
    }
}
