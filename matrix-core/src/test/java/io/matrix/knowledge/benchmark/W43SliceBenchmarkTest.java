package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.benchmark.SliceBenchmark.Item;
import io.matrix.knowledge.benchmark.SliceBenchmark.Report;
import io.matrix.knowledge.benchmark.SliceBenchmark.Result;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W43.3 — the measurement of the counterexample slice.
 *
 * <p>The mission's §10 asks for a benchmark that measures understanding and
 * not activity. This instrument measures the slice's DECISIONS, and it says
 * what it does not measure:
 *
 * <ul>
 *   <li>it does not measure understanding — the slice's decisions are
 *       language-independent by construction, so the Russian subset measures
 *       that the text is carried through unchanged, not that the system
 *       understands Russian;</li>
 *   <li>it does not measure retrieval, answering or promotion — those are
 *       the W42 instruments, over different cases, and their numbers are
 *       not comparable with these.</li>
 * </ul>
 *
 * <p>The floors below are the MEASURED values of this wave. They are not
 * aspiration: a change that lowers them is a change that made the slice
 * worse, and the test says so.
 */
class W43SliceBenchmarkTest {

    @TempDir
    Path dir;

    private Report report() throws Exception {
        return SliceBenchmark.run(SliceBenchmark.items(), dir);
    }

    @Test
    @DisplayName("the slice benchmark is measured, with its denominators")
    void theSliceBenchmarkIsMeasuredWithItsDenominators() throws Exception {
        Report report = report();

        System.out.println("the slice benchmark: the cases " + report.total()
                + " | the standing " + report.standingCorrect() + "/" + report.total()
                + " | the reading " + report.readingCorrectAmongExpected() + "/"
                + report.readingExpected()
                + " | the proposal " + report.proposalCorrect() + "/" + report.total()
                + " | the quotation " + report.proposalContainsCorrect() + "/" + report.total());
        System.out.println("  by language: " + report.byLanguage());
        System.out.println("  by split: " + report.bySplit());
        System.out.println("  by category: " + report.byCategory());
        for (Result result : report.results()) {
            if (!result.standingCorrect() || !result.readingCorrect()
                    || !result.proposalCorrect()) {
                System.out.println("  the case that is wrong: " + result.id()
                        + " expected " + result.expectedStanding() + "/" + result.expectedReading()
                        + " got " + result.actualStanding() + "/" + result.actualReading());
            }
        }

        assertThat(report.total()).isEqualTo(SliceBenchmark.items().size());
        assertThat(report.standingCorrect()).isEqualTo(report.total());
        assertThat(report.readingCorrectAmongExpected()).isEqualTo(report.readingExpected());
        assertThat(report.proposalCorrect()).isEqualTo(report.total());
        assertThat(report.proposalContainsCorrect()).isEqualTo(report.total());
    }

    @Test
    @DisplayName("the dataset has a held-out split, and both languages are in it")
    void theDatasetHasAHeldOutSplitAndBothLanguagesAreInIt() throws Exception {
        List<Item> items = SliceBenchmark.items();

        // The size is written down, so that a change to the dataset is a
        // visible change to the test rather than a silent one.
        assertThat(items).hasSize(32);
        assertThat(items).extracting(Item::language).contains("en", "ru");
        assertThat(items).extracting(Item::split).contains("train", "validation", "test");

        // The held-out split is a third of the dataset, and it is measured
        // on its own.
        long test = items.stream().filter(i -> i.split().equals("test")).count();
        assertThat(test).isGreaterThanOrEqualTo(8L);

        Map<String, Map<String, Integer>> bySplit = report().bySplit();
        assertThat(bySplit).containsKeys("train", "validation", "test");
        assertThat(bySplit.get("test").get("cases")).isEqualTo((int) test);
    }

    @Test
    @DisplayName("the metrics do not fall below the measured floor")
    void theMetricsDoNotFallBelowTheMeasuredFloor() throws Exception {
        Report report = report();

        assertThat(report.standingCorrect())
                .as("the standing is decided correctly in every case")
                .isEqualTo(report.total());
        assertThat(report.readingCorrectAmongExpected())
                .as("the counterexample reading is decided correctly wherever a reading is "
                        + "expected")
                .isEqualTo(report.readingExpected());
        assertThat(report.readingCorrect())
                .as("no case produced a reading that was not expected, and no expected reading "
                        + "was withheld")
                .isEqualTo(report.total());
        assertThat(report.proposalCorrect())
                .as("a narrowing is proposed exactly when the evidence supports one")
                .isEqualTo(report.total());
        assertThat(report.proposalContainsCorrect())
                .as("the proposed narrowing quotes the recorded condition and invents nothing")
                .isEqualTo(report.total());
    }

    @Test
    @DisplayName("a case cannot see another case: every case runs in its own store")
    void aCaseCannotSeeAnotherCase() throws Exception {
        // The isolation is structural: the runner builds a store per case.
        // If it did not, the "no evidence" case would find the claims of the
        // cases before it and its standing would not be UNKNOWN.
        Report report = report();

        Map<String, Map<String, Integer>> byCategory = report.byCategory();
        assertThat(byCategory.get("no_evidence").get("standingCorrect"))
                .as("the two cases with no evidence find no evidence")
                .isEqualTo(2);
        assertThat(byCategory.get("counterexample_without_condition")
                .get("readingCorrect")).isEqualTo(4);
    }

    @Test
    @DisplayName("the measurement is the same when it is run twice")
    void theMeasurementIsTheSameWhenItIsRunTwice() throws Exception {
        Report first = report();
        Report second = report();

        assertThat(second.standingCorrect()).isEqualTo(first.standingCorrect());
        assertThat(second.readingCorrect()).isEqualTo(first.readingCorrect());
        assertThat(second.proposalCorrect()).isEqualTo(first.proposalCorrect());
        assertThat(second.byLanguage()).isEqualTo(first.byLanguage());
        assertThat(second.bySplit()).isEqualTo(first.bySplit());
        assertThat(second.byCategory()).isEqualTo(first.byCategory());
    }
}
