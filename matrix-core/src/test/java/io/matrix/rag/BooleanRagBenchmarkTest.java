package io.matrix.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanRagBenchmarkTest {

    // --- Construction ---

    @Test
    void shouldBuildWithDefaults() {
        var benchmark = BooleanRagBenchmark.builder().build();
        assertThat(benchmark).isNotNull();
    }

    @Test
    void shouldBuildWithCustomConfig() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(128)
                .topK(10)
                .warmupIterations(5)
                .measureIterations(20)
                .build();
        assertThat(benchmark).isNotNull();
    }

    @Test
    void shouldRejectInvalidDimensions() {
        assertThatThrownBy(() -> BooleanRagBenchmark.builder().dimensions(33).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidTopK() {
        assertThatThrownBy(() -> BooleanRagBenchmark.builder().topK(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Benchmark Execution ---

    @Test
    void shouldRunBenchmarkForSmallSize() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(64)
                .topK(3)
                .warmupIterations(2)
                .measureIterations(5)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{50});

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.indexSize()).isEqualTo(50);
        assertThat(result.numQueries()).isGreaterThan(0);
        assertThat(result.basicLatencyUs()).isGreaterThan(0);
        assertThat(result.hybridLatencyUs()).isGreaterThan(0);
        assertThat(result.expandedLatencyUs()).isGreaterThan(0);
        assertThat(result.basicUniqueDocs()).isGreaterThan(0);
    }

    @Test
    void shouldRunBenchmarkForMultipleSizes() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(64)
                .topK(3)
                .warmupIterations(2)
                .measureIterations(5)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{50, 100});

        assertThat(results).hasSize(2);
        assertThat(results.get(0).indexSize()).isEqualTo(50);
        assertThat(results.get(1).indexSize()).isEqualTo(100);
    }

    @Test
    void shouldReportUniqueDocsForExpandedApproach() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(64)
                .topK(3)
                .warmupIterations(2)
                .measureIterations(5)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{100});

        var result = results.get(0);
        // Expanded should find at least as many unique docs as basic
        assertThat(result.expandedUniqueDocs())
                .isGreaterThanOrEqualTo(result.basicUniqueDocs());
    }

    @Test
    void shouldHavePositiveLatencies() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(64)
                .topK(3)
                .warmupIterations(2)
                .measureIterations(5)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{50});

        var result = results.get(0);
        assertThat(result.basicLatencyUs()).isGreaterThan(0);
        assertThat(result.hybridLatencyUs()).isGreaterThan(0);
        assertThat(result.expandedLatencyUs()).isGreaterThan(0);
    }

    @Test
    void shouldProduceReadableToString() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(64)
                .topK(3)
                .warmupIterations(2)
                .measureIterations(5)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{50});

        String str = results.get(0).toString();
        assertThat(str).contains("Size=50");
        assertThat(str).contains("Latency(us)");
        assertThat(str).contains("UniqueDocs");
    }

    // --- 128-bit vectors ---

    @Test
    void shouldRunBenchmarkWith128BitVectors() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(128)
                .topK(3)
                .warmupIterations(2)
                .measureIterations(5)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{50});

        assertThat(results).hasSize(1);
        assertThat(results.get(0).basicLatencyUs()).isGreaterThan(0);
    }

    // --- Edge Cases ---

    @Test
    void shouldHandleSingleElementIndex() {
        var benchmark = BooleanRagBenchmark.builder()
                .dimensions(64)
                .topK(3)
                .warmupIterations(1)
                .measureIterations(3)
                .build();

        List<BooleanRagBenchmark.SizeResult> results = benchmark.run(new int[]{1});

        assertThat(results).hasSize(1);
        assertThat(results.get(0).basicUniqueDocs()).isGreaterThan(0);
    }
}
