package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 202 — CycleAnalyzer unit tests. */
class CycleAnalyzerTest {

    @Test
    void emptyTraceHasZeroCycles() {
        var svc = new BrainLoopService();
        var analysis = CycleAnalyzer.analyze(svc.trace());
        assertThat(analysis.cycleCount()).isZero();
        assertThat(analysis.totalSteps()).isZero();
    }

    @Test
    void tenCyclesProducesTenAnalyses() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 10; i++) svc.cycle("Q-" + i);
        var analysis = CycleAnalyzer.analyze(svc.trace());
        assertThat(analysis.cycleCount()).isEqualTo(10);
        assertThat(analysis.totalSteps()).isEqualTo(50);
        assertThat(analysis.stepsPerCycle()).hasSize(10);
    }

    @Test
    void gateDecisionsExtracted() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) svc.cycle("Q-" + i);
        var analysis = CycleAnalyzer.analyze(svc.trace());
        // 5 cycles × 1 gate step each = 5 gate decisions
        assertThat(analysis.gateVerdicts()).hasSize(5);
    }

    @Test
    void analysisRecord() {
        var analysis = new CycleAnalyzer.CycleAnalysis(
                10, 50,
                java.util.List.of(5, 5, 5, 5, 5, 5, 5, 5, 5, 5),
                java.util.List.of("ok"));
        assertThat(analysis.cycleCount()).isEqualTo(10);
        assertThat(analysis.totalSteps()).isEqualTo(50);
        assertThat(analysis.stepsPerCycle()).hasSize(10);
        assertThat(analysis.gateVerdicts()).hasSize(1);
    }

    @Test
    void formatProducesReadableString() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 3; i++) svc.cycle("X-" + i);
        var analysis = CycleAnalyzer.analyze(svc.trace());
        String text = CycleAnalyzer.format(analysis);
        assertThat(text).contains("CycleAnalysis");
        assertThat(text).contains("cycles=3");
    }
}
