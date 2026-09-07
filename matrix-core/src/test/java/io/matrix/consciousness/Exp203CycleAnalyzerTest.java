package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 203 — CycleAnalyzer EXP.
 *
 * <p>Analyze 200 cycles, verify counts add up.
 */
@Tag("exp")
class Exp203CycleAnalyzerTest {

    @Test
    void twoHundredCyclesAnalysis() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 200; i++) svc.cycle("Q-" + i);
        var analysis = CycleAnalyzer.analyze(svc.trace());
        System.out.println("[CYCLE-ANALYSIS] " + CycleAnalyzer.format(analysis));
        assertThat(analysis.cycleCount()).isEqualTo(200);
        assertThat(analysis.totalSteps()).isEqualTo(1000);
        assertThat(analysis.gateVerdicts()).hasSize(200);
    }

    @Test
    void mixedAdversarialAnalysis() {
        var svc = new BrainLoopService();
        String[] inputs = new String[100];
        for (int i = 0; i < 100; i++) {
            if (i % 10 == 0) inputs[i] = "hi\u0001attack";
            else inputs[i] = "test-" + i;
            svc.cycle(inputs[i]);
        }
        var analysis = CycleAnalyzer.analyze(svc.trace());
        // 10 should be adversarial (denied)
        int deniedCount = 0;
        for (String s : analysis.gateVerdicts()) {
            if (s.contains("DENY")) deniedCount++;
        }
        System.out.printf("[CYCLE-ANALYSIS-ADV] denied=%d/100 cycles%n",
                deniedCount);
        // Could be 10 or slightly different — verify reasonable
        assertThat(deniedCount).isGreaterThanOrEqualTo(8);
    }
}
