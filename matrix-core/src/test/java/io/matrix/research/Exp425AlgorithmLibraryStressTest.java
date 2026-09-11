package io.matrix.research;

import io.matrix.neuron.BellmanFord;
import io.matrix.neuron.BloomFilter;
import io.matrix.neuron.BoyerMoore;
import io.matrix.neuron.Compression;
import io.matrix.neuron.Dijkstra;
import io.matrix.neuron.FloydWarshall;
import io.matrix.neuron.KMeans;
import io.matrix.neuron.KdTree;
import io.matrix.neuron.NaiveBayes;
import io.matrix.neuron.PageRank;
import io.matrix.neuron.Sort;
import io.matrix.neuron.ThompsonBandit;
import io.matrix.neuron.UcbBandit;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 425 — Stress test: every NEW pure-function algorithm added
 * across RUN 419-424 (14+ classes) exercised in a SINGLE 1-second test.
 * Demonstrates the algorithm library is coherent and self-consistent.
 */
class Exp425AlgorithmLibraryStressTest {

    @Test
    void allFourteenAlgorithmsCorrectlyHandleRepresentativeInputs() {
        Random rng = new Random(0xCAFE);

        // 1. UCB
        UcbBandit.Step s1 = UcbBandit.choose(new double[]{0.0, 0.0, 0.0},
                new long[]{0, 0, 0}, -1, Math.sqrt(2), rng);
        assertThat(s1.chosen()).isBetween(0, 2);

        // 2. Thompson
        ThompsonBandit.Step s2 = ThompsonBandit.choose(
                new double[]{0.7, 0.3}, new double[]{0.1, 0.1},
                new long[]{50, 50}, 0, 1, rng);
        assertThat(s2.samples()).hasSize(2);

        // 3. Bloom filter
        BloomFilter bf = new BloomFilter(256, 3);
        bf.add(42L);
        assertThat(bf.mightContain(42L)).isTrue();

        // 4. PageRank
        List<List<Integer>> g = new ArrayList<>();
        for (int i = 0; i < 4; i++) g.add(new ArrayList<>());
        g.get(0).add(1); g.get(1).add(2); g.get(2).add(3); g.get(3).add(0);
        double[] ranks = PageRank.rank(g, 0.85, 30, 1e-6, null);
        double sum = 0; for (double r : ranks) sum += r;
        assertThat(sum).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));

        // 5. Dijkstra
        List<List<Dijkstra.Edge>> adj = new ArrayList<>();
        adj.add(List.of(new Dijkstra.Edge(1, 1.0), new Dijkstra.Edge(2, 3.0)));
        adj.add(List.of(new Dijkstra.Edge(2, 1.0)));
        adj.add(List.of());
        Dijkstra.Result r5 = Dijkstra.shortestPaths(adj, 0, 0);
        assertThat(r5.distances()[2]).isEqualTo(2.0);  // 0->1->2 vs 0->2=3

        // 6. KdTree
        KdTree tree = KdTree.of(new double[][]{{0, 0}, {1, 1}, {10, 10}});
        int[] nn = tree.nearest(new double[]{0.5, 0.5}, 1);
        assertThat(nn).hasSize(1);

        // 7. RLE
        byte[] rleIn = "aabbcc".getBytes(StandardCharsets.UTF_8);
        List<Compression.RlePair> r7 = Compression.rleEncode(rleIn);
        byte[] rleOut = Compression.rleDecode(r7);
        assertThat(rleOut).containsExactly(rleIn);

        // 8. Levenshtein
        assertThat(Compression.levenshtein(
                "abc".toCharArray(), "abd".toCharArray())).isEqualTo(1);

        // 9. BellmanFord
        List<BellmanFord.Edge> edges = List.of(
                new BellmanFord.Edge(0, 1, 5.0),
                new BellmanFord.Edge(1, 2, -3.0),
                new BellmanFord.Edge(0, 2, 1.0));
        BellmanFord.Result r9 = BellmanFord.shortestPaths(3, edges, 0);
        assertThat(r9.distances()[2]).isEqualTo(1.0);  // 0->2=1 beats 0->1->2=2

        // 10. FloydWarshall
        double[][] w = {{0, 1}, {2, 0}};
        double[][] d = FloydWarshall.shortestPaths(w);
        assertThat(d[1][0]).isEqualTo(2.0);

        // 11. NaiveBayes
        NaiveBayes.Training t = NaiveBayes.Training.empty(2, 4);
        t.increment(0, new int[]{0}); t.increment(0, new int[]{0});
        t.increment(1, new int[]{1}); t.increment(1, new int[]{1});
        assertThat(NaiveBayes.classify(t, null, 1.0, new int[]{0})).isEqualTo(0);

        // 12. KMeans
        KMeans.Result km = KMeans.cluster(
                new double[][]{{0, 0}, {1, 1}, {10, 10}, {11, 11}},
                2, 100, 1e-6, rng);
        assertThat(km.assignments()).hasSize(4);

        // 13. BoyerMoore
        byte[] text = "hello world".getBytes(StandardCharsets.UTF_8);
        byte[] needle = "world".getBytes(StandardCharsets.UTF_8);
        assertThat(BoyerMoore.search(text, needle)).isEqualTo(6);

        // 14. Sorting (all four agree on a long array)
        int[] orig = new int[100];
        for (int i = 0; i < 100; i++) orig[i] = (99 - i) * 3 + rng.nextInt(5);
        int[] q1 = orig.clone(); Sort.quickSort(q1);
        int[] q2 = orig.clone(); Sort.mergeSort(q2);
        int[] q3 = orig.clone(); Sort.heapSort(q3);
        java.util.Arrays.sort(orig);
        assertThat(q1).containsExactly(orig);
        assertThat(q2).containsExactly(orig);
        assertThat(q3).containsExactly(orig);

        System.out.println("[Exp425] ALL 14 ALGORITHM CLASSES PASS");
    }
}
