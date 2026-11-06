package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The version 3 dataset is what the mission's §14.1 asks for: at least a
 * hundred reproducible multilingual cases, covering every category the
 * mission names, in Russian and English, with the training, validation and
 * test sets kept separate.
 *
 * <p>The dataset is the instrument, so the checks here are about the DATA:
 * how many items there are, which categories and languages they cover, and
 * whether the splits are really disjoint. Nothing here measures the
 * system; that is {@link W42BenchmarkV3RunTest}.
 */
class W42BenchmarkV3Test {

    private static final String RESOURCE = "benchmark/w42-benchmark-v3.json";

    /** The number of cases the mission's §14 asks for. */
    private static final int MISSION_MINIMUM_CASES = 100;

    /** The categories the mission's §14.1 names, plus the cross-lingual one. */
    private static final List<String> REQUIRED_CATEGORIES = List.of(
            "FACTUAL", "MULTI_HOP", "CONTRADICTORY", "AMBIGUOUS", "MISSPELLED",
            "PARAPHRASE", "CROSS_LINGUAL", "UNANSWERABLE", "FALSE_PREMISE",
            "CAUSAL", "TRANSFER", "DELAYED_RECALL", "ADVERSARIAL");

    @Test
    @DisplayName("the dataset holds at least a hundred items")
    void theDatasetHoldsAtLeastAHundredItems() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        assertThat(suite.size()).isGreaterThanOrEqualTo(MISSION_MINIMUM_CASES);
    }

    @Test
    @DisplayName("the dataset covers every category the mission's §14.1 names")
    void theDatasetCoversEveryCategoryTheMissionNames() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        assertThat(suite.countsByCategory().keySet()).containsAll(REQUIRED_CATEGORIES);
        for (String category : REQUIRED_CATEGORIES) {
            assertThat(suite.countsByCategory().get(category))
                    .as("the items of the category " + category)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("the dataset is multilingual, in both directions")
    void theDatasetIsMultilingualInBothDirections() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);
        Map<String, Integer> byLanguage = suite.countsByLanguage();

        assertThat(byLanguage.keySet()).containsExactlyInAnyOrder("en", "ru");
        assertThat(byLanguage.get("en")).as("the English items").isGreaterThanOrEqualTo(30);
        assertThat(byLanguage.get("ru")).as("the Russian items").isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("the paraphrases are in Russian AND in English")
    void theParaphrasesAreInRussianAndInEnglish() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        List<BenchmarkItem> paraphrases = suite.items().stream()
                .filter(item -> item.category().equals("PARAPHRASE"))
                .toList();
        assertThat(paraphrases).isNotEmpty();
        assertThat(paraphrases).filteredOn(item -> item.language().equals("en")).isNotEmpty();
        assertThat(paraphrases).filteredOn(item -> item.language().equals("ru")).isNotEmpty();
    }

    @Test
    @DisplayName("the splits partition the dataset, and no item is in two of them")
    void theSplitsPartitionTheDatasetAndNoItemIsInTwoOfThem() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        Set<String> train = ids(suite.itemsInSplit(BenchmarkSuite.SPLIT_TRAIN));
        Set<String> validation = ids(suite.itemsInSplit(BenchmarkSuite.SPLIT_VALIDATION));
        Set<String> test = ids(suite.itemsInSplit(BenchmarkSuite.SPLIT_TEST));

        assertThat(train).isNotEmpty();
        assertThat(validation).isNotEmpty();
        assertThat(test).isNotEmpty();
        assertThat(Set.copyOf(train)).doesNotContainAnyElementsOf(validation);
        assertThat(Set.copyOf(train)).doesNotContainAnyElementsOf(test);
        assertThat(Set.copyOf(validation)).doesNotContainAnyElementsOf(test);

        Set<String> all = new HashSet<>(train);
        all.addAll(validation);
        all.addAll(test);
        assertThat(all).hasSize(suite.size());
    }

    @Test
    @DisplayName("the delayed recall tests a lesson of the train split, and the two sets are disjoint")
    void theDelayedRecallTestsALessonOfTheTrainSplit() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        Set<String> lessons = suite.lessonItemIds();
        Set<String> recalls = suite.recallItemIds();

        assertThat(recalls).as("the delayed-recall items").isNotEmpty();
        assertThat(lessons).as("the lesson items").isNotEmpty();
        // The lesson set and the recall set are disjoint: an item that is
        // the lesson of a recall is never itself a recall.
        assertThat(lessons).doesNotContainAnyElementsOf(recalls);

        for (String recall : recalls) {
            String lesson = suite.lessonItemIdOf(recall);
            assertThat(lesson).as("the lesson of " + recall).isNotBlank();
            assertThat(suite.splitOf(lesson))
                    .as("the split of the lesson of " + recall)
                    .isEqualTo(BenchmarkSuite.SPLIT_TRAIN);
            assertThat(suite.splitOf(recall))
                    .as("the split of " + recall)
                    .isEqualTo(BenchmarkSuite.SPLIT_TEST);
            assertThat(recall).isNotEqualTo(lesson);
        }
    }

    @Test
    @DisplayName("every item states what it is and what the system should do with it")
    void everyItemStatesWhatItIsAndWhatTheSystemShouldDo() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        assertThat(suite.items()).allSatisfy(item -> {
            assertThat(item.id()).isNotBlank();
            assertThat(item.category()).isNotBlank();
            assertThat(item.language()).isIn("en", "ru");
            assertThat(item.workspace()).isIn("photosynthesis", "logic");
            assertThat(item.question()).isNotBlank();
            // An answerable item names the terms the answer must carry, and
            // an unanswerable item names the subject that is not there, so
            // that the reachability check can say something about it. The
            // ambiguous and the adversarial items name no subject on
            // purpose: the ambiguity is the point, and an injection is not
            // a question about the knowledge.
            boolean namesNoSubject = item.category().equals("AMBIGUOUS")
                    || item.category().equals("ADVERSARIAL");
            if (!namesNoSubject) {
                assertThat(item.keyTerms()).isNotEmpty();
            }
        });
        assertThat(suite.items().stream().map(BenchmarkItem::id).collect(Collectors.toSet()))
                .hasSize(suite.size());
    }

    @Test
    @DisplayName("the frozen baseline of version 1 is still the baseline, and it is untouched")
    void theFrozenBaselineOfVersionOneIsStillTheBaseline() {
        List<BenchmarkItem> baseline = BenchmarkItem.load("benchmark/w42-benchmark-v1.json");

        // Decision D-001: version 1 is the frozen baseline instrument. The
        // version 3 file sits beside it and does not change it.
        assertThat(baseline).hasSize(34);
        assertThat(baseline.stream().map(BenchmarkItem::category).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        "FACTUAL", "MULTI_HOP", "CONTRADICTORY", "AMBIGUOUS", "MISSPELLED",
                        "PARAPHRASE", "CROSS_LINGUAL", "UNANSWERABLE", "FALSE_PREMISE",
                        "CAUSAL", "TRANSFER", "ADVERSARIAL");
    }

    private static Set<String> ids(List<BenchmarkItem> items) {
        return items.stream().map(BenchmarkItem::id).collect(Collectors.toSet());
    }
}
