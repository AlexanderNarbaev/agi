package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the synthetic typo benchmark, and the intent it measures.
 *
 * <p>The mission's §14 names a row this system had never measured: "≥ 95 %
 * intent preservation on a synthetic typo benchmark". The W42 benchmark's
 * MISSPELLED category holds four items — a demonstration — and no
 * benchmark said what the user MEANT.
 */
class TypoBenchmarkTest {

    @Test
    @DisplayName("the benchmark is synthetic, deterministic and labelled")
    void theBenchmarkIsSyntheticDeterministicAndLabelled(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            TypoBenchmarkGenerator.Benchmark first =
                    TypoBenchmarkGenerator.generate(workbench.workspace("photosynthesis"));
            TypoBenchmarkGenerator.Benchmark second =
                    TypoBenchmarkGenerator.generate(workbench.workspace("photosynthesis"));

            assertThat(first.cases()).isGreaterThanOrEqualTo(12);
            assertThat(first.seed()).isEqualTo(TypoBenchmarkGenerator.SEED);
            // The same seed and the same workspace give the same cases.
            assertThat(second.casesList()).isEqualTo(first.casesList());
            // Every case carries the question that was MEANT.
            assertThat(first.casesList()).allSatisfy(item -> {
                assertThat(item.intendedQuestion()).isNotBlank();
                assertThat(item.question()).isNotEqualTo(item.intendedQuestion());
                assertThat(item.keyTerms()).isNotEmpty();
            });
        }
    }

    @Test
    @DisplayName("the intent is preserved on at least 95 % of the cases")
    void theIntentIsPreservedOnAtLeastNinetyFivePercentOfTheCases(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            TypoBenchmarkGenerator.Benchmark benchmark =
                    TypoBenchmarkGenerator.generate(workbench.workspace("photosynthesis"));

            TypoBenchmarkGenerator.Result result =
                    TypoBenchmarkRunner.run(workbench, "photosynthesis", benchmark);

            // The mission's §14: at least 95 %.
            assertThat(result.share()).isGreaterThanOrEqualTo(0.95);
            assertThat(result.failures()).isEmpty();
        }
    }

    @Test
    @DisplayName("the measurement does not reward a lucky intersection")
    void theMeasurementDoesNotRewardALuckyIntersection() {
        TypoBenchmarkGenerator.Benchmark benchmark = new TypoBenchmarkGenerator.Benchmark(
                "test", "42", 42L, 1,
                java.util.List.of(new TypoBenchmarkGenerator.Case("typo-0", "ws",
                        "What is fotosynthesis?", "What is photosynthesis?", "Photosynthesis",
                        TypoBenchmarkGenerator.Typo.SUBSTITUTION,
                        java.util.List.of("photosynthesis", "light"))));

        // The answer mentions one of the two key terms: that is not enough.
        TypoBenchmarkGenerator.Result partial = TypoBenchmarkGenerator.measure(benchmark,
                java.util.Map.of("typo-0", "Photosynthesis converts light energy."),
                java.util.Map.of("typo-0", "Photosynthesis converts light energy."));
        assertThat(partial.preserved()).isEqualTo(1);

        TypoBenchmarkGenerator.Result wrong = TypoBenchmarkGenerator.measure(benchmark,
                java.util.Map.of("typo-0", "Photosynthesis converts light energy."),
                java.util.Map.of("typo-0", "Cellular respiration releases energy from glucose."));
        assertThat(wrong.preserved()).isEqualTo(0);
        assertThat(wrong.failures()).hasSize(1);
    }
}
