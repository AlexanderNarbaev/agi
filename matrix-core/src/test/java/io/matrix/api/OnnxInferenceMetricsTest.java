package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 69 — OnnxInferenceMetrics unit tests. */
class OnnxInferenceMetricsTest {

    @Test
    void emptyMetrics() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        assertThat(m.inferenceCount()).isZero();
        assertThat(m.totalTokens()).isZero();
        assertThat(m.avgLatencyNanos()).isZero();
        assertThat(m.tokensPerSecond()).isZero();
        assertThat(m.gpuRatio()).isZero();
    }

    @Test
    void recordSingleGpuCall() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(5, 50_000_000L, true);  // 5 tokens, 50ms, GPU
        assertThat(m.inferenceCount()).isEqualTo(1);
        assertThat(m.totalTokens()).isEqualTo(5);
        assertThat(m.avgLatencyNanos()).isEqualTo(50_000_000L);
        assertThat(m.maxLatencyNanos()).isEqualTo(50_000_000L);
        assertThat(m.totalGpuCalls()).isEqualTo(1);
        assertThat(m.totalCpuCalls()).isZero();
        assertThat(m.gpuRatio()).isEqualTo(1.0);
    }

    @Test
    void recordMixedGpuAndCpu() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(3, 30_000_000L, true);
        m.record(2, 100_000_000L, false);
        m.record(4, 60_000_000L, true);
        assertThat(m.inferenceCount()).isEqualTo(3);
        assertThat(m.totalTokens()).isEqualTo(9);
        assertThat(m.totalGpuCalls()).isEqualTo(2);
        assertThat(m.totalCpuCalls()).isEqualTo(1);
        assertThat(m.gpuRatio()).isCloseTo(0.666, org.assertj.core.data.Offset.offset(0.01));
        assertThat(m.maxLatencyNanos()).isEqualTo(100_000_000L);
    }

    @Test
    void resetClearsAllCounters() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(5, 50_000_000L, true);
        m.reset();
        assertThat(m.inferenceCount()).isZero();
        assertThat(m.totalTokens()).isZero();
        assertThat(m.avgLatencyNanos()).isZero();
        assertThat(m.totalGpuCalls()).isZero();
    }

    @Test
    void jsonIncludesAllFields() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(2, 10_000_000L, true);
        String json = m.toJson();
        assertThat(json).contains("\"inferenceCount\":1");
        assertThat(json).contains("\"totalTokens\":2");
        assertThat(json).contains("\"totalGpuCalls\":1");
        assertThat(json).contains("\"totalCpuCalls\":0");
        assertThat(json).contains("\"gpuRatio\":1.0");
    }

    @Test
    void tokensPerSecondAfterSomeTime() throws Exception {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(100, 50_000_000L, true);
        Thread.sleep(50);  // 50ms passes
        m.record(100, 50_000_000L, true);
        double tps = m.tokensPerSecond();
        assertThat(tps).isGreaterThan(0.0);
        // 200 tokens in ~50ms+overhead = at least 2000 tokens/sec
        assertThat(tps).isGreaterThan(100.0);
    }

    @Test
    void maxLatencyTracked() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(1, 10_000_000L, true);
        m.record(1, 200_000_000L, true);   // max
        m.record(1, 50_000_000L, true);
        assertThat(m.maxLatencyNanos()).isEqualTo(200_000_000L);
    }

    @Test
    void avgLatencyIsCorrect() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.record(1, 30_000_000L, true);
        m.record(1, 60_000_000L, true);
        m.record(1, 90_000_000L, true);
        assertThat(m.avgLatencyNanos()).isEqualTo(60_000_000L);
    }

    @Test
    void recordArgmaxProbabilityUpdatesAverage() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.recordArgmaxProbability(0.5);
        m.recordArgmaxProbability(0.7);
        m.recordArgmaxProbability(0.9);
        assertThat(m.avgArgmaxProbability()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void emptyArgmaxAverage() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        assertThat(m.avgArgmaxProbability()).isZero();
    }

    @Test
    void resetClearsArgmax() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.recordArgmaxProbability(0.8);
        m.reset();
        assertThat(m.avgArgmaxProbability()).isZero();
    }

    @Test
    void jsonIncludesArgmaxProb() {
        OnnxInferenceMetrics m = new OnnxInferenceMetrics();
        m.recordArgmaxProbability(0.5);
        String json = m.toJson();
        assertThat(json).contains("\"avgArgmaxProb\":");
    }
}
