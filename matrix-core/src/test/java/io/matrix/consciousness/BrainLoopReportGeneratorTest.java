package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 230 — BrainLoopReportGenerator unit tests. */
class BrainLoopReportGeneratorTest {

    @Test
    void emptyReportHasStructure() {
        var svc = new BrainLoopService();
        String report = BrainLoopReportGenerator.generate(svc);
        assertThat(report).contains("MATRIX Brain Loop Report");
        assertThat(report).contains("Arousal:");
    }

    @Test
    void reportIncludesTrace() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) svc.cycle("X-" + i);
        String report = BrainLoopReportGenerator.generate(svc);
        assertThat(report).contains("Trace steps: 25");  // 5 × 5
        assertThat(report).contains("Trace tail hash:");
    }

    @Test
    void reportIncludesSaturation() {
        var svc = new BrainLoopService();
        String report = BrainLoopReportGenerator.generate(svc);
        assertThat(report).contains("Saturation:");
        assertThat(report).contains("cycles=0");
    }

    @Test
    void reportIncludesHealth() {
        var svc = new BrainLoopService();
        String report = BrainLoopReportGenerator.generate(svc);
        assertThat(report).contains("Health:");
        assertThat(report).contains("status=");
    }

    @Test
    void saveCreatesFile(@TempDir Path tmp) throws Exception {
        var svc = new BrainLoopService();
        Path file = tmp.resolve("report.txt");
        BrainLoopReportGenerator.save(svc, file);
        assertThat(Files.exists(file)).isTrue();
        String content = Files.readString(file);
        assertThat(content).contains("Brain Loop Report");
    }
}
