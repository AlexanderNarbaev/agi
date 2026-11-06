package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The dialogue benchmark is measured, with its denominators.
 *
 * <p>The floors in this test are the MEASURED values, not the wanted ones.
 * Where a metric is low, the test says so in the floor: a benchmark whose
 * floors are the wanted numbers measures nothing.
 */
class W43DialogueBenchmarkTest {

    @Test
    @DisplayName("the dialogue benchmark is measured, with its denominators")
    void theDialogueBenchmarkIsMeasuredWithItsDenominators(@TempDir Path dir) throws Exception {
        DialogueBenchmark.Report report = DialogueBenchmark.run(dir);

        assertThat(report.total()).isEqualTo(8);
        assertThat(report.answeredCorrect()).isEqualTo(6);
        assertThat(report.clarificationCorrect()).isEqualTo(8);

        // The denominators are reported, so a ratio cannot be quoted without
        // the count it is a ratio of.
        assertThat(report.clarificationRequired()).isEqualTo(2);
        assertThat(report.clarificationAskedWhenRequired()).isEqualTo(2);
        assertThat(report.clarificationNotRequired()).isEqualTo(6);
        assertThat(report.clarificationWithheldWhenNotRequired()).isEqualTo(6);
        assertThat(report.refusalRequired()).isEqualTo(6);
        assertThat(report.refusalGiven()).isEqualTo(4);

        // The false-premise cases are the ones the system gets wrong, and the
        // number is written down rather than hidden.
        assertThat(report.falsePremiseCases()).isEqualTo(2);
        assertThat(report.falsePremiseRefused()).isZero();

        System.out.println("the dialogue benchmark: the cases " + report.total()
                + " | the answer decision " + report.answeredCorrect() + "/" + report.total()
                + " | the clarification " + report.clarificationCorrect() + "/" + report.total());
        System.out.println("  by language: " + report.byLanguage());
        System.out.println("  by split: " + report.bySplit());
        System.out.println("  by category: " + report.byCategory());
        for (DialogueBenchmark.Result r : report.results()) {
            if (!r.answeredCorrect()) {
                System.out.println("  the case that is wrong: " + r.id() + " expected answered="
                        + r.expectedAnswered() + " got " + r.actualAnswered() + " | " + r.reason());
            }
        }
    }

    @Test
    @DisplayName("the dataset has a held-out split, and both languages are in it")
    void theDatasetHasAHeldOutSplitAndBothLanguagesAreInIt() throws Exception {
        List<DialogueBenchmark.Item> items = DialogueBenchmark.items();

        assertThat(items).isNotEmpty();
        assertThat(items.stream().map(DialogueBenchmark.Item::language).distinct())
                .containsExactlyInAnyOrder("en", "ru");
        assertThat(items.stream().map(DialogueBenchmark.Item::split).distinct())
                .contains("train", "validation", "test");
        assertThat(items.stream().filter(i -> "test".equals(i.split())).count()).isPositive();

        // Every category the mission's section 10 asks for is represented,
        // and the expectations are the truth: a question that must be
        // refused is expected NOT to be answered.
        Map<String, Long> byCategory = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(DialogueBenchmark.Item::category,
                        java.util.stream.Collectors.counting()));
        assertThat(byCategory).containsKeys("clarification_required", "answered",
                "abstention_by_coverage", "false_premise");
        for (DialogueBenchmark.Item item : items) {
            if ("clarification_required".equals(item.category())) {
                assertThat(item.expect().clarification()).isTrue();
                assertThat(item.expect().answered()).isFalse();
            }
            if ("false_premise".equals(item.category())) {
                assertThat(item.expect().answered()).isFalse();
            }
        }
    }
}
