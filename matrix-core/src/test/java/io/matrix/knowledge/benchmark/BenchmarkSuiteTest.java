package io.matrix.knowledge.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The suite loads the version 3 dataset and keeps the parts the version 1
 * loader never had: the splits, the provenance and the delayed-recall
 * linkage.
 *
 * <p>Every new class has to be covered by a test that can fail, so the
 * loader's REFUSALS are tested too — a dataset whose item declares no
 * split, or an unknown split, is refused at load rather than silently
 * counted into a split it does not belong to.
 */
class BenchmarkSuiteTest {

    private static final String RESOURCE = "benchmark/w42-benchmark-v3.json";

    /** The number of cases the mission's §14 asks for. */
    private static final int MISSION_MINIMUM_CASES = 100;

    @Test
    @DisplayName("the suite loads the dataset and reports its own counts")
    void theSuiteLoadsTheDatasetAndReportsItsOwnCounts() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        assertThat(suite.id()).isEqualTo("w42-benchmark-v3");
        assertThat(suite.version()).isEqualTo(3);
        assertThat(suite.size()).isEqualTo(suite.items().size());
        assertThat(suite.size()).isGreaterThanOrEqualTo(MISSION_MINIMUM_CASES);
        assertThat(suite.categories()).hasSizeGreaterThanOrEqualTo(13);
    }

    @Test
    @DisplayName("the provenance of the dataset is recorded")
    void theProvenanceOfTheDatasetIsRecorded() {
        BenchmarkSuite.Provenance provenance = BenchmarkSuite.load(RESOURCE).provenance();

        assertThat(provenance.id()).isEqualTo("w42-benchmark-v3");
        assertThat(provenance.version()).isEqualTo(3);
        assertThat(provenance.seed()).isEqualTo(42L);
        assertThat(provenance.generator()).isNotBlank();
        assertThat(provenance.parameters()).containsKeys(
                "seed", "workspaces", "categories", "languages", "splits",
                "typoKinds", "etalonRule", "pointerRule", "unanswerableRule", "delayedRecallRule");
    }

    @Test
    @DisplayName("the splits partition the items and the run order asks them in order")
    void theSplitsPartitionTheItemsAndTheRunOrderAsksThemInOrder() {
        BenchmarkSuite suite = BenchmarkSuite.load(RESOURCE);

        Map<String, Integer> counts = suite.countsBySplit();
        assertThat(counts).containsOnlyKeys(BenchmarkSuite.SPLITS);
        assertThat(counts.get(BenchmarkSuite.SPLIT_TRAIN)).isPositive();
        assertThat(counts.get(BenchmarkSuite.SPLIT_VALIDATION)).isPositive();
        assertThat(counts.get(BenchmarkSuite.SPLIT_TEST)).isPositive();
        assertThat(counts.get(BenchmarkSuite.SPLIT_TRAIN)
                + counts.get(BenchmarkSuite.SPLIT_VALIDATION)
                + counts.get(BenchmarkSuite.SPLIT_TEST)).isEqualTo(suite.size());

        List<BenchmarkItem> order = suite.runOrder();
        assertThat(order).hasSize(suite.size());
        // The train split is asked first, so a lesson is taught before the
        // recall that tests it is asked.
        String firstOfTest = order.stream()
                .filter(item -> BenchmarkSuite.SPLIT_TEST.equals(suite.splitOf(item.id())))
                .map(BenchmarkItem::id)
                .sorted()
                .findFirst()
                .orElseThrow();
        int lastOfTrain = -1;
        for (int index = 0; index < order.size(); index++) {
            if (BenchmarkSuite.SPLIT_TRAIN.equals(suite.splitOf(order.get(index).id()))) {
                lastOfTrain = index;
            }
        }
        int firstOfTestIndex = -1;
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id().equals(firstOfTest)) {
                firstOfTestIndex = index;
            }
        }
        assertThat(lastOfTrain).isLessThan(firstOfTestIndex);
    }

    @Test
    @DisplayName("the loader refuses an item that declares no split")
    void theLoaderRefusesAnItemThatDeclaresNoSplit() {
        String json = """
                {"benchmark": {"id": "x", "version": 1},
                 "items": [{"id": "a", "category": "FACTUAL", "language": "en",
                            "workspace": "logic", "question": "What is validity?"}]}
                """;

        assertThatThrownBy(() -> BenchmarkSuite.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declares no split");
    }

    @Test
    @DisplayName("the loader refuses an item that declares an unknown split")
    void theLoaderRefusesAnItemThatDeclaresAnUnknownSplit() {
        String json = """
                {"benchmark": {"id": "x", "version": 1},
                 "items": [{"id": "a", "category": "FACTUAL", "language": "en",
                            "workspace": "logic", "question": "What is validity?",
                            "split": "holdout"}]}
                """;

        assertThatThrownBy(() -> BenchmarkSuite.parse(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown split");
    }

    @Test
    @DisplayName("the loader refuses a file that is not JSON")
    void theLoaderRefusesAFileThatIsNotJson() {
        assertThatThrownBy(() -> BenchmarkSuite.parse("this is not JSON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
    }
}
