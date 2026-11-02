package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The version 3 dataset, run against the workbench.
 *
 * <p>The test is the measurement, and the measurement is printed: the
 * counts per category, per language and per split, the answer accuracy, the
 * abstention quality, and the BM25 overlap. The floors below are the
 * MEASURED values, and the tree they were measured on is named beside them.
 * They are the floor and not the target: the mission's §14 rows are
 * measurements and not promises (decision D-007).
 *
 * <p><b>The measurement was taken on the tree of 2026-11-02</b> — the
 * commit {@code 6ea07101} plus the landed W42.9 changes of the English
 * lemma, of the premise checker and of the Russian genitive ending. The
 * numbers of an earlier tree are not comparable with these, because the
 * text layer changed under the instrument.
 *
 * <p>The run order is the suite's own: the train split first, so a lesson
 * is taught before the delayed recall that tests it is asked, and the
 * recall itself with the test split, after every other item.
 */
class W42BenchmarkV3RunTest {

    private static final String RESOURCE = "benchmark/w42-benchmark-v3.json";

    /** The answer accuracy MEASURED on 2026-11-02 at 0.842 (85 of 101). */
    private static final double MEASURED_ACCURACY = 0.842;

    /** The floor of the answer accuracy: the measured value, rounded down. */
    private static final double ACCURACY_FLOOR = 0.80;

    /** The abstention quality MEASURED on 2026-11-02 at 0.861 (31 of 36). */
    private static final double MEASURED_ABSTENTION = 0.861;

    /** The floor of the abstention quality: the measured value, rounded down. */
    private static final double ABSTENTION_FLOOR = 0.80;

    /** The delayed-recall items answered correctly, MEASURED on 2026-11-02. */
    private static final int MEASURED_DELAYED_RECALL = 7;

    /** The number of the delayed-recall items. */
    private static final int DELAYED_RECALL_ITEMS = 8;

    /** The floor of the delayed recall: the measured value, less one. */
    private static final int DELAYED_RECALL_FLOOR = 6;

    /** The cross-lingual items answered correctly, MEASURED on 2026-11-02. */
    private static final int MEASURED_CROSS_LINGUAL = 13;

    /** The number of the cross-lingual items. */
    private static final int CROSS_LINGUAL_ITEMS = 14;

    /** The floor of the cross-lingual score: the measured value, less one. */
    private static final int CROSS_LINGUAL_FLOOR = 12;

    @Test
    @DisplayName("the whole version 3 set is measured, and the numbers are printed")
    void theWholeVersionThreeSetIsMeasured(@TempDir Path dir) {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);
        BenchmarkRunner.Report report = run(dir, suite);

        assertThat(report.results()).hasSize(suite.size());

        System.out.println("the v3 benchmark: the items " + report.results().size()
                + " | the accuracy " + String.format("%.3f", report.answerAccuracy())
                + " | the abstention " + String.format("%.3f", report.abstentionQuality())
                + " | the BM25 overlap " + String.format("%.3f", report.meanBm25Overlap()));
        print("the category", report.byCategory());
        print("the language", report.byLanguage());
        print("the split", bySplit(suite, report));

        for (BenchmarkRunner.ItemResult result : report.results()) {
            if (!result.correct()) {
                System.out.println("  the item that is wrong: " + result.item().id()
                        + " | " + result.item().category()
                        + " | the split " + suite.splitOf(result.item().id())
                        + " | the answerable " + result.expectedToAnswer()
                        + " | the answered " + result.answered()
                        + " | " + result.note());
            }
        }
    }

    @Test
    @DisplayName("the answer accuracy does not fall below the measured floor")
    void theAnswerAccuracyDoesNotFallBelowTheMeasuredFloor(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir, BenchmarkSuite.load(RESOURCE));

        // MEASURED 2026-11-02 at 0.842 on the tree of 6ea07101 plus the
        // landed W42.9 changes.
        assertThat(report.answerAccuracy())
                .as("the answer accuracy, measured 2026-11-02 at " + MEASURED_ACCURACY)
                .isGreaterThanOrEqualTo(ACCURACY_FLOOR);
    }

    @Test
    @DisplayName("the abstention quality does not fall below the measured floor")
    void theAbstentionQualityDoesNotFallBelowTheMeasuredFloor(@TempDir Path dir) {
        BenchmarkRunner.Report report = run(dir, BenchmarkSuite.load(RESOURCE));

        // MEASURED 2026-11-02 at 0.861 on the tree of 6ea07101 plus the
        // landed W42.9 changes.
        assertThat(report.abstentionQuality())
                .as("the abstention quality, measured 2026-11-02 at " + MEASURED_ABSTENTION)
                .isGreaterThanOrEqualTo(ABSTENTION_FLOOR);
    }

    @Test
    @DisplayName("the run reports the same numbers twice over a fresh store")
    void theRunReportsTheSameNumbersTwice(@TempDir Path dir) {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        BenchmarkRunner.Report first = run(dir.resolve("one"), suite);
        BenchmarkRunner.Report second = run(dir.resolve("two"), suite);

        assertThat(second.answerableCount()).isEqualTo(first.answerableCount());
        assertThat(second.answeredCorrectly()).isEqualTo(first.answeredCorrectly());
        assertThat(second.unanswerableCount()).isEqualTo(first.unanswerableCount());
        assertThat(second.refusedCorrectly()).isEqualTo(first.refusedCorrectly());
        assertThat(second.meanBm25Overlap()).isEqualTo(first.meanBm25Overlap());
        assertThat(second.byCategory()).isEqualTo(first.byCategory());
        assertThat(second.byLanguage()).isEqualTo(first.byLanguage());
        assertThat(bySplit(suite, second)).isEqualTo(bySplit(suite, first));
    }

    @Test
    @DisplayName("the delayed recall is answered after the lesson, on a disjoint item set")
    void theDelayedRecallIsAnsweredAfterTheLesson(@TempDir Path dir) {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);
        BenchmarkRunner.Report report = run(dir, suite);

        List<String> recalls = List.copyOf(suite.recallItemIds());
        assertThat(recalls).hasSize(DELAYED_RECALL_ITEMS);
        long correct = report.results().stream()
                .filter(result -> recalls.contains(result.item().id()))
                .filter(BenchmarkRunner.ItemResult::correct)
                .count();

        // MEASURED 2026-11-02: the delayed recall is answered correctly in
        // 7 of the 8 items, on the tree of 6ea07101 plus the W42.9 changes.
        assertThat(correct)
                .as("the delayed-recall items answered correctly, measured 2026-11-02 at "
                        + MEASURED_DELAYED_RECALL + " of " + DELAYED_RECALL_ITEMS)
                .isGreaterThanOrEqualTo(DELAYED_RECALL_FLOOR);
    }

    @Test
    @DisplayName("the cross-lingual items stay answered in Russian")
    void theCrossLingualItemsStayAnsweredInRussian(@TempDir Path dir) {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);
        BenchmarkRunner.Report report = run(dir, suite);

        long correct = report.results().stream()
                .filter(result -> result.item().category().equals("CROSS_LINGUAL"))
                .filter(BenchmarkRunner.ItemResult::correct)
                .count();

        // MEASURED 2026-11-02: 13 of the 14 cross-lingual items, on the
        // tree of 6ea07101 plus the W42.9 changes. The one that fails is
        // the Russian word for validity, which the bilingual lexicon does
        // not carry.
        assertThat(correct)
                .as("the cross-lingual items answered correctly, measured 2026-11-02 at "
                        + MEASURED_CROSS_LINGUAL + " of " + CROSS_LINGUAL_ITEMS)
                .isGreaterThanOrEqualTo(CROSS_LINGUAL_FLOOR);
    }

    /** The benchmark, run over a fresh store, in the suite's run order. */
    private BenchmarkRunner.Report run(Path dir, BenchmarkSuite suite) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("benchmark.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.loadBundledPackage("logic");
            return new BenchmarkRunner(workbench).run(suite.runOrder());
        }
    }

    /** The results grouped by the split of their item. */
    private Map<String, BenchmarkRunner.Report.Group> bySplit(BenchmarkSuite suite,
                                                              BenchmarkRunner.Report report) {
        Map<String, int[]> buckets = new LinkedHashMap<>();
        for (String split : BenchmarkSuite.SPLITS) {
            buckets.put(split, new int[4]);
        }
        for (BenchmarkRunner.ItemResult result : report.results()) {
            String split = suite.splitOf(result.item().id());
            int[] bucket = buckets.get(split);
            bucket[0]++;
            if (result.expectedToAnswer()) {
                bucket[1]++;
                if (result.correct()) {
                    bucket[2]++;
                }
            } else if (result.correct()) {
                bucket[3]++;
            }
        }
        Map<String, BenchmarkRunner.Report.Group> groups = new LinkedHashMap<>();
        buckets.forEach((key, bucket) -> groups.put(key,
                new BenchmarkRunner.Report.Group(bucket[0], bucket[1], bucket[2], bucket[3])));
        return groups;
    }

    private void print(String what, Map<String, BenchmarkRunner.Report.Group> groups) {
        for (Map.Entry<String, BenchmarkRunner.Report.Group> entry : groups.entrySet()) {
            BenchmarkRunner.Report.Group group = entry.getValue();
            System.out.println("  " + what + " " + entry.getKey()
                    + ": the items " + group.items()
                    + " | the answerable " + group.answerable()
                    + " | the correct " + group.correct()
                    + " | the refused correctly " + group.refusedCorrectly());
        }
    }
}
