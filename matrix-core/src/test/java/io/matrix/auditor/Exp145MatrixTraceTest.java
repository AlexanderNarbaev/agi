package io.matrix.auditor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 145 — MatrixTrace EXP.
 *
 * <p>Real workload: simulate 100 cognitive cycles, each with
 * perception → deliberation → action steps. Verify hash chain
 * integrity.
 */
@Tag("exp")
class Exp145MatrixTraceTest {

    @Test
    void hundredCyclesChained() {
        MatrixTrace trace = new MatrixTrace();
        for (int cycle = 0; cycle < 100; cycle++) {
            try (var perception = trace.begin("perception")
                    .input("input-" + cycle)) {
                // simulate work
                perception.end("ok");
            }
            try (var attention = trace.begin("attention")) {
                attention.end("ok");
            }
            try (var deliberation = trace.begin("deliberation")) {
                deliberation.end("ok");
            }
            try (var action = trace.begin("action")
                    .output("output-" + cycle)) {
                action.end("ok");
            }
        }
        assertThat(trace.count()).isEqualTo(400);  // 4 steps × 100 cycles

        // Verify chain integrity
        MatrixTrace.TraceStep prev = null;
        int i = 0;
        for (var step : trace.steps()) {
            if (i > 0) {
                assertThat(step.prevHash).isEqualTo(prev.hash);
            }
            prev = step;
            i++;
        }
        System.out.printf("[TRACE-EXP] %d steps, lastHash=%s...%n",
                trace.count(),
                prev.hash.substring(0, 16));
    }

    @Test
    void deterministicHash() {
        String h1 = MatrixTrace.hashHex("p", "name", "in", "out", 100L);
        String h2 = MatrixTrace.hashHex("p", "name", "in", "out", 100L);
        assertThat(h1).isEqualTo(h2);
        // SHA-256 produces 64 hex chars
        assertThat(h1).hasSize(64);
        System.out.printf("[TRACE-DETERM] %s%n", h1);
    }
}
