package io.matrix.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W48-09 — the statistics of the GPU benchmark harness.
 *
 * <p>The harness is the instrument that produces the numbers the W48 report
 * quotes, so its arithmetic is itself under test: a median that is off by one
 * position, or a percentile that interpolates between two samples, would put a
 * wrong number into a document that a reviewer has no other way to check.
 *
 * <p>These tests are pure arithmetic and need no GPU, no model and no ONNX
 * Runtime session.
 */
class GpuBenchmarkHarnessTest {

    @Test
    void theMedianOfAnOddNumberOfSamplesIsTheMiddleOne() {
        GpuBenchmarkHarness.Stats stats = GpuBenchmarkHarness.stats(
                new double[]{5.0, 1.0, 3.0, 9.0, 7.0});

        assertThat(stats.count()).isEqualTo(5);
        assertThat(stats.min()).isEqualTo(1.0);
        assertThat(stats.median()).isEqualTo(5.0);
        assertThat(stats.max()).isEqualTo(9.0);
    }

    @Test
    void theMedianOfAnEvenNumberOfSamplesIsTheMeanOfTheTwoCentralOnes() {
        GpuBenchmarkHarness.Stats stats = GpuBenchmarkHarness.stats(
                new double[]{1.0, 2.0, 3.0, 4.0});

        assertThat(stats.median()).isEqualTo(2.5);
    }

    @Test
    void thePercentilesAreObservedSamplesAndNotInterpolations() {
        // Twenty samples: the nearest-rank 95th percentile is the 19th of them.
        double[] samples = new double[20];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i + 1;
        }

        GpuBenchmarkHarness.Stats stats = GpuBenchmarkHarness.stats(samples);

        assertThat(stats.p95()).isEqualTo(19.0);
        assertThat(stats.max()).isEqualTo(20.0);
        assertThat(stats.min()).isEqualTo(1.0);
    }

    @Test
    void aSingleSampleHasNoDispersion() {
        GpuBenchmarkHarness.Stats stats = GpuBenchmarkHarness.stats(new double[]{7.5});

        assertThat(stats.count()).isEqualTo(1);
        assertThat(stats.min()).isEqualTo(7.5);
        assertThat(stats.median()).isEqualTo(7.5);
        assertThat(stats.p95()).isEqualTo(7.5);
        assertThat(stats.max()).isEqualTo(7.5);
        assertThat(stats.mean()).isEqualTo(7.5);
        assertThat(stats.stddev()).isZero();
    }

    @Test
    void theStandardDeviationUsesTheSampleDenominator() {
        // 2, 4, 4, 4, 5, 5, 7, 9 — the population sd is 2.0 and the sample sd is
        // 2.13809... The sample denominator is the one that is reported.
        GpuBenchmarkHarness.Stats stats = GpuBenchmarkHarness.stats(
                new double[]{2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0});

        assertThat(stats.mean()).isEqualTo(5.0);
        assertThat(stats.stddev()).isCloseTo(2.138089935, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void anEmptySeriesIsRejectedRatherThanReportedAsZero() {
        assertThatThrownBy(() -> GpuBenchmarkHarness.stats(new double[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theArgumentsAreParsedAndTheProviderIsConstrained() {
        GpuBenchmarkHarness.Config config = GpuBenchmarkHarness.parseArgs(new String[]{
                "--label", "cuda",
                "--provider", "cuda",
                "--seq-len", "32",
                "--warmup", "5",
                "--iterations", "20",
                "--require-cuda"});

        assertThat(config.label()).isEqualTo("cuda");
        assertThat(config.provider()).isEqualTo("cuda");
        assertThat(config.seqLen()).isEqualTo(32);
        assertThat(config.warmup()).isEqualTo(5);
        assertThat(config.iterations()).isEqualTo(20);
        assertThat(config.requireCuda()).isTrue();
    }

    @Test
    void anUnknownProviderIsRejected() {
        assertThatThrownBy(() -> GpuBenchmarkHarness.parseArgs(
                new String[]{"--provider", "tpu"}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
