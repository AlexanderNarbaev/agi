package io.matrix.ktopo;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit and property tests for the SPEC-003 ktopo foundations:
 * {@link DriftFingerprint}, {@link FingerprintDistance}, {@link CurriculumOrderer}.
 */
class KtopoPropertiesTest {

    private static final OllivierRicciCalculator RICCI = new OllivierRicciCalculator();

    // --- Unit: fingerprint over a triangle ---

    @Test
    void triangleFingerprintNormalizedAndBinned() {
        double[][] adj = {
                {0, 1, 1},
                {1, 0, 1},
                {1, 1, 0}};
        double[] curvatures = RICCI.computeCurvatures(Graph.of(adj));
        double[] fingerprint = DriftFingerprint.of(curvatures);

        assertThat(fingerprint).hasSize(DriftFingerprint.BINS);
        assertThat(Arrays.stream(fingerprint).sum()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void emptyCurvaturesYieldUniformBaseline() {
        double[] fingerprint = DriftFingerprint.of(new double[0]);
        for (double bin : fingerprint) {
            assertThat(bin).isCloseTo(1.0 / DriftFingerprint.BINS, within(1e-12));
        }
    }

    // --- Unit: distance identities ---

    @Test
    void distanceToSelfIsZero() {
        double[] f = DriftFingerprint.of(new double[]{0.5, -0.25, 0.9});
        assertThat(FingerprintDistance.distance(f, f)).isCloseTo(0.0, within(1e-12));
    }

    @Test
    void distanceIsSymmetric() {
        double[] a = DriftFingerprint.of(new double[]{0.9, 0.9, 0.8});
        double[] b = DriftFingerprint.of(new double[]{-1.5, -1.0});
        double ab = FingerprintDistance.distance(a, b);
        double ba = FingerprintDistance.distance(b, a);
        assertThat(ab).isGreaterThanOrEqualTo(0.0);
        assertThat(ab).isCloseTo(ba, within(1e-12));
    }

    // --- Unit: dense-first ordering ---

    @Test
    void denserComponentComesFirstAndCoverageExact() {
        // Vertices 0..2 form a path (density 2/3); 3..4 an edge (density 1/1).
        Graph g = new Graph(5,
                new int[]{0, 0, 3},
                new int[]{1, 2, 4},
                new double[]{1, 1, 1});
        List<String> names = List.of("a", "b", "c", "d", "e");

        List<List<String>> ordered = CurriculumOrderer.order(g, names);

        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0)).containsExactly("d", "e");               // density 1.0
        assertThat(ordered.get(1)).containsExactlyInAnyOrder("a", "b", "c"); // density 2/3
        assertThat(ordered.stream().flatMap(List::stream))
                .containsExactlyInAnyOrderElementsOf(names);
    }

    // --- Properties: W1 distance ---

    @Property
    void distanceNonNegativeAndSymmetric(@ForAll("twoHistograms") double[][] pair) {
        double ab = FingerprintDistance.wasserstein1(pair[0], pair[1], 0.125);
        double ba = FingerprintDistance.wasserstein1(pair[1], pair[0], 0.125);
        assertThat(ab).isGreaterThanOrEqualTo(0.0);
        assertThat(ab).isCloseTo(ba, within(1e-12));
    }

    @Provide
    Arbitrary<double[][]> twoHistograms() {
        Arbitrary<double[]> histogram = Arbitraries.doubles().between(0.0, 10.0)
                .array(double[].class)
                .ofMinSize(1)
                .ofMaxSize(24);
        Arbitrary<Integer> sharedLength = Arbitraries.integers().between(1, 24);
        return net.jqwik.api.Combinators.combine(sharedLength, histogram, histogram)
                .as((len, a, b) -> new double[][]{
                        normalize(Arrays.copyOf(a, len)),
                        normalize(Arrays.copyOf(b, len))});
    }

    private static double[] normalize(double[] values) {
        double sum = Arrays.stream(values).sum();
        if (sum <= 0.0) {
            return java.util.Arrays.stream(values)
                    .map(v -> 1.0 / values.length)
                    .toArray();
        }
        return Arrays.stream(values).map(v -> v / sum).toArray();
    }

    // --- Properties: orderer coverage ---

    @Property
    void ordererCoversEveryVertexExactlyOnce(
            @ForAll("edgePairs") List<int[]> edges,
            @ForAll("vertexCount") int n) {
        List<int[]> clean = new ArrayList<>();
        for (int[] e : edges) {
            if (e[0] < n && e[1] < n && e[0] != e[1]) {
                clean.add(e);
            }
        }
        Graph g = new Graph(n,
                clean.stream().mapToInt(e -> e[0]).toArray(),
                clean.stream().mapToInt(e -> e[1]).toArray(),
                clean.stream().mapToDouble(e -> 1.0).toArray());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            names.add("v" + i);
        }

        List<List<String>> ordered = CurriculumOrderer.order(g, names);
        assertThat(ordered.stream().flatMap(List::stream))
                .containsExactlyInAnyOrderElementsOf(names);
    }

    @Provide
    Arbitrary<Integer> vertexCount() {
        return Arbitraries.integers().between(1, 6);
    }

    /** Edge as {u, v} packed from one integer in [0,36): u = i / 6, v = i % 6. */
    @Provide
    Arbitrary<List<int[]>> edgePairs() {
        return Arbitraries.integers().between(0, 35)
                .map(i -> new int[]{i / 6, i % 6})
                .list()
                .ofMaxSize(12);
    }
}
