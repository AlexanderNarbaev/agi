package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the metrics report the owner directive requires.
 *
 * <p>§51-52: a mandatory metrics dashboard carrying the dataset version, the
 * software version, the test procedure and the sample size; no single opaque
 * intelligence score; and nothing hidden about the metrics that were not
 * measured.
 */
class MetricsReportTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage("photosynthesis");
    }

    @Test
    @DisplayName("the report carries the dataset version, the software version and the procedure")
    void theReportCarriesItsProvenance() {
        MetricsReport report = workbench.metrics("photosynthesis");

        assertThat(report.datasetId()).isNotBlank();
        assertThat(report.datasetVersion()).isNotBlank();
        assertThat(report.softwareVersion()).isNotBlank();
        assertThat(report.softwareVersionSource()).isNotBlank();
        assertThat(report.testProcedure()).isNotBlank();
        assertThat(report.testProcedure().length()).isGreaterThan(80);
    }

    @Test
    @DisplayName("the unmeasured metrics are listed, not hidden")
    void theUnmeasuredMetricsAreListed() {
        MetricsReport report = workbench.metrics("photosynthesis");

        assertThat(report.unmeasured()).isNotEmpty();
        assertThat(report.unmeasured()).anyMatch(u -> u.contains("Transfer to a human learner"));
        assertThat(report.unmeasured()).anyMatch(u -> u.contains("retention"));
        assertThat(report.notes()).anyMatch(n -> n.contains("machine-to-machine"));
    }

    @Test
    @DisplayName("there is no single opaque score")
    void thereIsNoSingleOpaqueScore() {
        MetricsReport report = workbench.metrics("photosynthesis");

        // The report is a set of separate measurements, and the map it
        // publishes has no field that claims to be the intelligence of the
        // system.
        assertThat(report.asMap()).doesNotContainKey("intelligence");
        assertThat(report.asMap()).doesNotContainKey("score");
        assertThat(report.asMap()).doesNotContainKey("overall");
        assertThat(report.asMap().keySet())
                .contains("workspaceId", "softwareVersion", "datasetVersion", "testProcedure",
                        "dimensions", "coverage", "asking", "unmeasured");
    }

    @Test
    @DisplayName("a dimension with no outcome is not reported as zero")
    void anUnassessedDimensionIsNotReportedAsZero() {
        MetricsReport before = workbench.metrics("photosynthesis");
        assertThat(before.dimensions()).isEmpty();

        workbench.assess("photosynthesis", "t-recall-1",
                "Photosynthesis uses light, carbon dioxide and water to make sugars.", 0.8, "s1");

        MetricsReport after = workbench.metrics("photosynthesis");
        // The assessment produces two dimensions, not one: what was recalled
        // and how confident the learner was. Both are reported, and neither
        // is reported as zero.
        assertThat(after.dimensions()).hasSize(2);
        assertThat(after.dimensions()).anyMatch(d -> d.dimension().equals("RECALL"));
        assertThat(after.dimensions()).anyMatch(d -> d.dimension().equals("CALIBRATION"));
        assertThat(after.dimensions()).allMatch(d -> d.evidenceCount() == 1);
        assertThat(after.dimensions()).allMatch(MetricsReport.DimensionMetric::measured);
    }

    @Test
    @DisplayName("the coverage counts what is there and what backs it")
    void theCoverageCountsWhatIsThere() {
        MetricsReport report = workbench.metrics("photosynthesis");
        MetricsReport.CoverageMetric coverage = report.coverage();

        assertThat(coverage.concepts()).isEqualTo(8);
        assertThat(coverage.claims()).isEqualTo(17);
        assertThat(coverage.sources()).isEqualTo(4);
        assertThat(coverage.evidenceLinks()).isEqualTo(18);
        assertThat(coverage.evidenceBackedShare()).isGreaterThan(0.0);
        assertThat(coverage.evidenceBackedShare()).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("the refusal rate is counted, not guessed")
    void theRefusalRateIsCounted() {
        workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
        workbench.ask("photosynthesis", "What is the boiling point of mercury?",
                AbstractionLevel.INTRODUCTORY);

        MetricsReport report = workbench.metrics("photosynthesis");
        MetricsReport.AskingMetric asking = report.asking();

        assertThat(asking.questionsAsked()).isEqualTo(2);
        assertThat(asking.distinctQuestions()).isEqualTo(2);
        assertThat(asking.questionsAnswered()).isEqualTo(1);
        assertThat(asking.questionsRefused()).isEqualTo(1);
        assertThat(asking.refusalRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("the report survives a restart with the same numbers")
    void theReportSurvivesARestart() {
        workbench.assess("photosynthesis", "t-recall-1",
                "Photosynthesis uses light, carbon dioxide and water to make sugars.", 0.8, "s1");
        MetricsReport before = workbench.metrics("photosynthesis");
        workbench.close();

        KnowledgeWorkbench reopened = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        try {
            MetricsReport after = reopened.metrics("photosynthesis");
            assertThat(after.coverage().claims()).isEqualTo(before.coverage().claims());
            assertThat(after.dimensions()).hasSameSizeAs(before.dimensions());
            assertThat(after.dimensions().get(0).score()).isEqualTo(before.dimensions().get(0).score());
            assertThat(after.asking().questionsAsked()).isEqualTo(before.asking().questionsAsked());
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("the unmeasured list is the same for every workspace")
    void theUnmeasuredListIsTheSame() {
        assertThat(MetricsReport.unmeasuredMetrics()).isNotEmpty();
        assertThat(MetricsReport.unmeasuredMetrics()).isEqualTo(
                workbench.metrics("photosynthesis").unmeasured());
    }
}
