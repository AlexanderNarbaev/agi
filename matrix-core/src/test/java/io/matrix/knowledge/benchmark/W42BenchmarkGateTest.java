package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.8 — the benchmark runs with the suite, and it can go red.
 *
 * <p>The benchmark's data and its runner are in the repository, but the
 * probe that measured them lived in a temporary directory: nobody else
 * could re-run the mission's own measurement, and a regression in the
 * answer quality would not have turned anything red.
 *
 * <p>The floors below are the MEASURED values, recorded with their date
 * in {@code docs-v2/engineering/RECON-W42-BENCHMARKS.md}. They are the
 * floor and not the target: the mission's §14's acceptance targets are
 * measurements and not promises (D-007), and this gate only says that
 * the measurement must not silently fall.
 */
class W42BenchmarkGateTest {

    @Test
    @DisplayName("the answer accuracy does not fall below the measured floor")
    void theAnswerAccuracyDoesNotFallBelowTheMeasuredFloor(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir);

        // MEASURED 2026-11-02: the accuracy 1.000 (24 of 24).
        assertThat(report.answerAccuracy())
                .as("the answer accuracy, measured 2026-11-02 at 1.000")
                .isGreaterThanOrEqualTo(0.95);
    }

    @Test
    @DisplayName("the abstention quality does not fall below the measured floor")
    void theAbstentionQualityDoesNotFallBelowTheMeasuredFloor(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir);

        // MEASURED 2026-11-02: the abstention 0.900 (9 of 10 refused).
        assertThat(report.abstentionQuality())
                .as("the abstention quality, measured 2026-11-02 at 0.900")
                .isGreaterThanOrEqualTo(0.90);
    }

    @Test
    @DisplayName("the cross-lingual items stay answered in Russian")
    void theCrossLingualItemsStayAnsweredInRussian(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir);

        Map<String, BenchmarkRunner.Report.Group> byCategory = report.byCategory();
        BenchmarkRunner.Report.Group crossLingual = byCategory.get("CROSS_LINGUAL");
        assertThat(crossLingual).isNotNull();
        assertThat(crossLingual.items()).isEqualTo(4);
        // MEASURED 2026-11-02: 4 of 4.
        assertThat(crossLingual.correct())
                .as("the cross-lingual items, measured 2026-11-02 at 4 of 4")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("the transfer items stay answered")
    void theTransferItemsStayAnswered(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir);

        BenchmarkRunner.Report.Group transfer = report.byCategory().get("TRANSFER");
        assertThat(transfer).isNotNull();
        // MEASURED 2026-11-02: 2 of 2, after the rule application.
        assertThat(transfer.correct())
                .as("the transfer items, measured 2026-11-02 at 2 of 2")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("the benchmark's own numbers are printed, so a run is a record")
    void theBenchmarksOwnNumbersArePrinted(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir);
        System.out.println("the benchmark: the items " + report.results().size()
                + " | the accuracy " + String.format("%.3f", report.answerAccuracy())
                + " | the abstention " + String.format("%.3f", report.abstentionQuality())
                + " | the BM25 overlap " + String.format("%.3f", report.meanBm25Overlap()));
        for (BenchmarkRunner.ItemResult result : report.results()) {
            if (!result.correct()) {
                System.out.println("  the item that is wrong: " + result.item().id()
                        + " | " + result.item().category()
                        + " | the answerable " + result.expectedToAnswer()
                        + " | the answered " + result.answered());
            }
        }
        assertThat(report.results()).isNotEmpty();
    }

    /** The benchmark, run over a fresh database. */
    private BenchmarkRunner.Report run(Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("benchmark.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.loadBundledPackage("logic");
            List<BenchmarkItem> items = BenchmarkItem.load("benchmark/w42-benchmark-v1.json");
            assertThat(items).hasSizeGreaterThanOrEqualTo(30);
            return new BenchmarkRunner(workbench).run(items);
        }
    }
}
