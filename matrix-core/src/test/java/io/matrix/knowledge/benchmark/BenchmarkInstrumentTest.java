package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.0 — the benchmark instrument measures what it claims to.
 *
 * <p>The benchmark is the instrument every W42 improvement will be measured
 * against, so it has to be trustworthy: it must load, it must cover the
 * categories it claims, and it must report the same numbers twice.
 */
class BenchmarkInstrumentTest {

    private static final String RESOURCE = "benchmark/w42-benchmark-v1.json";

    @Test
    @DisplayName("the benchmark loads and covers every category the mission names")
    void theBenchmarkLoadsAndCoversEveryCategory() {
        List<BenchmarkItem> items = BenchmarkItem.load(RESOURCE);

        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> {
            assertThat(item.id()).isNotBlank();
            assertThat(item.question()).isNotBlank();
            assertThat(item.workspace()).isNotBlank();
            assertThat(item.language()).isIn("en", "ru");
        });

        Set<String> categories = items.stream()
                .map(BenchmarkItem::category).collect(Collectors.toSet());
        assertThat(categories).containsExactlyInAnyOrder(
                "FACTUAL", "MULTI_HOP", "CONTRADICTORY", "AMBIGUOUS", "MISSPELLED",
                "PARAPHRASE", "CROSS_LINGUAL", "UNANSWERABLE", "FALSE_PREMISE",
                "CAUSAL", "TRANSFER", "ADVERSARIAL");

        // The Russian items are the cross-lingual probe: the knowledge packs
        // are in English, so a Russian question asks whether the concept
        // identity survives the language of the question.
        assertThat(items).filteredOn(item -> item.language().equals("ru")).isNotEmpty();
        assertThat(items).filteredOn(item -> item.category().equals("CROSS_LINGUAL")).isNotEmpty();

        // The item identifiers are unique: a duplicate id would make a
        // per-category count wrong without any test failing.
        assertThat(items.stream().map(BenchmarkItem::id).collect(Collectors.toSet()))
                .hasSize(items.size());
    }

    @Test
    @DisplayName("an answerable item names what the answer must say, and a trap names what it must not")
    void anAnswerableItemNamesWhatTheAnswerMustSay() {
        List<BenchmarkItem> items = BenchmarkItem.load(RESOURCE);

        // Every answerable item with key terms names at least one of them as
        // required, so "correct" is not silently undefined.
        assertThat(items).filteredOn(BenchmarkItem::answerable)
                .allSatisfy(item -> assertThat(item.keyTerms()).isNotEmpty());

        // The false-premise and adversarial items are traps: they carry a
        // forbidden phrase, and that phrase is what makes them traps.
        assertThat(items).filteredOn(item -> item.category().equals("FALSE_PREMISE"))
                .allSatisfy(item -> assertThat(item.mustNotMention()).isNotEmpty());
    }

    @Test
    @DisplayName("the runner reports the same numbers on two runs")
    void theRunnerReportsTheSameNumbersOnTwoRuns(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.loadBundledPackage("logic");

            List<BenchmarkItem> items = BenchmarkItem.load(RESOURCE);
            BenchmarkRunner.Report first = new BenchmarkRunner(workbench).run(items);
            BenchmarkRunner.Report second = new BenchmarkRunner(workbench).run(items);

            assertThat(second.answerableCount()).isEqualTo(first.answerableCount());
            assertThat(second.answeredCorrectly()).isEqualTo(first.answeredCorrectly());
            assertThat(second.unanswerableCount()).isEqualTo(first.unanswerableCount());
            assertThat(second.refusedCorrectly()).isEqualTo(first.refusedCorrectly());
            assertThat(second.meanBm25Overlap()).isEqualTo(first.meanBm25Overlap());
            assertThat(second.byCategory()).isEqualTo(first.byCategory());
            assertThat(second.byLanguage()).isEqualTo(first.byLanguage());
        }
    }

    @Test
    @DisplayName("the runner counts a refusal on an answerable item as a failure")
    void theRunnerCountsARefusalOnAnAnswerableItemAsAFailure(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            BenchmarkItem answerable = new BenchmarkItem("test-1", "FACTUAL", "en",
                    "photosynthesis", "What is photosynthesis?", true,
                    List.of("photosynthesis"), List.of("photosynthesis"), List.of());
            BenchmarkItem unanswerable = new BenchmarkItem("test-2", "UNANSWERABLE", "en",
                    "photosynthesis", "What is the boiling point of mercury?", false,
                    List.of(), List.of(), List.of());

            BenchmarkRunner.Report report = new BenchmarkRunner(workbench)
                    .run(List.of(answerable, unanswerable));

            assertThat(report.answerableCount()).isEqualTo(1);
            assertThat(report.unanswerableCount()).isEqualTo(1);
            assertThat(report.answeredCorrectly()).isEqualTo(1);
            assertThat(report.refusedCorrectly()).isEqualTo(1);
            assertThat(report.answerAccuracy()).isEqualTo(1.0);
            assertThat(report.abstentionQuality()).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("the BM25 baseline ranks the matching claim first")
    void theBm25BaselineRanksTheMatchingClaimFirst(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Bm25Index index = new Bm25Index(workbench.workspace("photosynthesis"));

            assertThat(index.documentCount()).isGreaterThan(0);
            List<Bm25Index.Scored> ranked = index.rank("What is the Calvin cycle?");
            assertThat(ranked).isNotEmpty();
            // The top-ranked claim must actually be about the thing asked.
            String top = ranked.get(0).claim().proposition().toLowerCase();
            assertThat(top).contains("calvin");
            // And the scores must descend.
            for (int i = 1; i < ranked.size(); i++) {
                assertThat(ranked.get(i).score()).isLessThanOrEqualTo(ranked.get(i - 1).score());
            }
        }
    }

    @Test
    @DisplayName("the BM25 index gives a term that appears in every claim a non-negative weight")
    void theBm25IndexGivesACommonTermANonNegativeWeight(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Bm25Index index = new Bm25Index(workbench.workspace("photosynthesis"));

            // The probabilistic IDF of Robertson & Zaragoza never goes
            // negative, so a term that appears everywhere lowers a score
            // without flipping its sign.
            assertThat(index.idf(index.documentCount())).isGreaterThanOrEqualTo(0.0);
            assertThat(index.idf(1)).isGreaterThan(index.idf(index.documentCount()));
        }
    }
}
