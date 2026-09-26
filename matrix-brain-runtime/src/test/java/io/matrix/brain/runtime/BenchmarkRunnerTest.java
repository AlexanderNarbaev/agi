package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W13 — BenchmarkRunner tests (in-process, no gateway required).
 */
class BenchmarkRunnerTest {

    @Test
    void eval_battery_contains_all_categories(@TempDir Path tmp) {
        var probes = EvalBattery.standardBattery();
        assertThat(probes).isNotEmpty();
        Map<String, Integer> byCat = new java.util.HashMap<>();
        for (EvalBattery.Probe p : probes) {
            byCat.merge(p.category().name(), 1, Integer::sum);
        }
        // All 6 categories must be represented
        assertThat(byCat).containsKeys(
            "ARITHMETIC", "ANALOGY", "CONTRADICTION",
            "ETHICS", "RU", "TAUGHT_RETRIEVAL");
        // Each category has at least 3 probes
        for (var v : byCat.values()) assertThat(v).isGreaterThanOrEqualTo(3);
    }

    @Test
    void probe_expectations_are_realistic(@TempDir Path tmp) {
        for (EvalBattery.Probe p : EvalBattery.standardBattery()) {
            assertThat(p.id()).isNotBlank();
            assertThat(p.input()).isNotBlank();
            assertThat(p.expectedMatch()).isNotBlank();
            assertThat(p.minConfidence()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void runner_produces_csv_when_called(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("report.csv");
        // Use a minimal in-process probe set; no gateway needed for CSV output
        // verification
        var runner = new BenchmarkRunner();
        // Just check that toCsv writes valid CSV header for known input
        java.lang.reflect.Method m = BenchmarkRunner.class.getDeclaredMethod("writeCsv",
            Path.class, java.util.List.class);
        m.setAccessible(true);
        m.invoke(runner, csv, java.util.List.of());
        String content = java.nio.file.Files.readString(csv);
        assertThat(content).startsWith("probe_id,category,expected");
    }
}
