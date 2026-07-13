package io.matrix.consensus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsensusBenchmarkTest {

    @Test
    void shouldRunBenchmarkWithNoFaults() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.run(4, 0, 5);

        assertThat(result.totalNodes()).isEqualTo(4);
        assertThat(result.faultyNodes()).isEqualTo(0);
        assertThat(result.rounds()).isEqualTo(5);
        assertThat(result.committed()).isEqualTo(5);
        assertThat(result.rejected()).isEqualTo(0);
        assertThat(result.avgLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldRunWithTolerableFaults() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.run(7, 2, 5);

        assertThat(result.totalNodes()).isEqualTo(7);
        assertThat(result.faultyNodes()).isEqualTo(2);
        assertThat(result.committed()).isEqualTo(5);
    }

    @Test
    void shouldRejectWithTooManyFaults() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.run(4, 3, 5);

        assertThat(result.totalNodes()).isEqualTo(4);
        assertThat(result.faultyNodes()).isEqualTo(3);
        assertThat(result.rejected()).isGreaterThan(0);
    }

    @Test
    void shouldRunFaultToleranceSweep() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        List<ConsensusBenchmark.BenchmarkResult> results = benchmark.faultToleranceSweep(7, 3);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).faultyNodes()).isEqualTo(0);
        assertThat(results.get(0).committed()).isEqualTo(3);

        ConsensusBenchmark.BenchmarkResult lastResult = results.get(results.size() - 1);
        assertThat(lastResult.faultyNodes()).isGreaterThan(2);
    }

    @Test
    void shouldRunScalabilityBenchmark() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        List<ConsensusBenchmark.BenchmarkResult> results = benchmark.scalabilityBenchmark(
                List.of(4, 7, 10), 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).totalNodes()).isEqualTo(4);
        assertThat(results.get(1).totalNodes()).isEqualTo(7);
        assertThat(results.get(2).totalNodes()).isEqualTo(10);

        for (ConsensusBenchmark.BenchmarkResult result : results) {
            assertThat(result.committed()).isEqualTo(3);
        }
    }

    @Test
    void shouldCompareWithBaseline() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        long[] comparison = benchmark.compareWithBaseline(4, 5);

        assertThat(comparison).hasSize(2);
        assertThat(comparison[0]).isGreaterThanOrEqualTo(0);
        assertThat(comparison[1]).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldThrowForInvalidConfig() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        assertThatThrownBy(() -> benchmark.run(4, 4, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> benchmark.run(4, 5, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMeasureThroughput() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.run(4, 0, 10);

        assertThat(result.rounds()).isEqualTo(10);
        assertThat(result.committed() + result.rejected() + result.recovery()).isEqualTo(10);
    }

    @Test
    void shouldReportLatencyStatistics() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.run(4, 0, 10);

        assertThat(result.totalLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.minLatencyMs()).isLessThanOrEqualTo(result.maxLatencyMs());
        assertThat(result.avgLatencyMs()).isBetween(
                result.minLatencyMs(), result.maxLatencyMs());
    }

    @Test
    void shouldRunSimpleMajorityBenchmark() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.runSimpleMajority(5, 3);

        assertThat(result.totalNodes()).isEqualTo(5);
        assertThat(result.rounds()).isEqualTo(3);
        assertThat(result.committed() + result.rejected()).isEqualTo(3);
        assertThat(result.scenario()).contains("SIMPLE_MAJORITY");
    }

    @Test
    void shouldRunWeightedBenchmark() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.runWeighted(5, 3);

        assertThat(result.totalNodes()).isEqualTo(5);
        assertThat(result.rounds()).isEqualTo(3);
        assertThat(result.committed() + result.rejected()).isEqualTo(3);
        assertThat(result.scenario()).contains("WEIGHTED");
    }

    @Test
    void shouldRunDebateBenchmark() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        ConsensusBenchmark.BenchmarkResult result = benchmark.runDebate(5, 3);

        assertThat(result.totalNodes()).isEqualTo(5);
        assertThat(result.rounds()).isEqualTo(3);
        assertThat(result.committed() + result.rejected()).isEqualTo(3);
        assertThat(result.scenario()).contains("DEBATE");
    }

    @Test
    void shouldCompareAllStrategies() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        List<ConsensusBenchmark.BenchmarkResult> results = benchmark.compareStrategies(5, 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).scenario()).contains("SIMPLE_MAJORITY");
        assertThat(results.get(1).scenario()).contains("WEIGHTED");
        assertThat(results.get(2).scenario()).contains("DEBATE");

        for (ConsensusBenchmark.BenchmarkResult result : results) {
            assertThat(result.rounds()).isEqualTo(3);
            assertThat(result.committed() + result.rejected()).isEqualTo(3);
        }
    }

    @Test
    void shouldMeasureThroughputAcrossStrategies() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        List<ConsensusBenchmark.BenchmarkResult> results = benchmark.compareStrategies(4, 5);

        for (ConsensusBenchmark.BenchmarkResult result : results) {
            assertThat(result.throughputRps()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void shouldReportLatencyForEachStrategy() {
        ConsensusBenchmark benchmark = new ConsensusBenchmark();

        List<ConsensusBenchmark.BenchmarkResult> results = benchmark.compareStrategies(4, 5);

        for (ConsensusBenchmark.BenchmarkResult result : results) {
            assertThat(result.avgLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.minLatencyMs()).isLessThanOrEqualTo(result.maxLatencyMs());
        }
    }
}
