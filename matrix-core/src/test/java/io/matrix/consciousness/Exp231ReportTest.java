package io.matrix.consciousness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 231 — BrainLoopReport EXP.
 *
 * <p>Real workload: 100 cycles, save report, verify content.
 */
@Tag("exp")
class Exp231ReportTest {

    @Test
    void reportAfterActivity(@TempDir Path tmp) throws Exception {
        var svc = new BrainLoopService();
        for (int i = 0; i < 100; i++) svc.cycle("Q-" + i);
        Path file = tmp.resolve("report.txt");
        BrainLoopReportGenerator.save(svc, file);
        String text = Files.readString(file);
        System.out.println("[REPORT-EXP]\n" +
                text.lines().limit(10).reduce("", (a, b) -> a + "\n" + b));
        assertThat(text).contains("Trace steps: 500"); // 100 × 5
        assertThat(text).contains("Saturation:");
        assertThat(text).contains("Health:");
    }
}
