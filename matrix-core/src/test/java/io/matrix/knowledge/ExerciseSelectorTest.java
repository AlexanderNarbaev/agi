package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.4 — the system selects the next exercise.
 *
 * <p>The mission's §10.1 asks for an exercise generator and a learner
 * practice loop, and for the system to suggest what to learn next,
 * considering the learner's current state and gaps. RECON-W41 had neither:
 * the items are a static file and the caller supplied the item id.
 */
class ExerciseSelectorTest {

    @Test
    @DisplayName("the next exercise comes from the learner's weakest dimension")
    void theNextExerciseComesFromTheLearnersWeakestDimension(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            ExerciseSelector.Selection selection = workbench.nextExercise("photosynthesis");

            assertThat(selection).isNotNull();
            assertThat(selection.item()).isNotNull();
            assertThat(selection.reason()).isNotBlank();
            assertThat(selection.conceptName()).isNotBlank();
        }
    }

    @Test
    @DisplayName("a wrongly answered item is asked again; a right one is not")
    void aWronglyAnsweredItemIsAskedAgainARightOneIsNot(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            ExerciseSelector.Selection first = workbench.nextExercise("photosynthesis");
            assertThat(first).isNotNull();
            // The learner answers it wrongly: the error is the gap, and the
            // gap is what practice is for.
            workbench.assess("photosynthesis", first.item().id(), "an answer", 0.5, "test");

            ExerciseSelector.Selection second = workbench.nextExercise("photosynthesis");
            assertThat(second).isNotNull();
            assertThat(second.item().id()).isEqualTo(first.item().id());
            assertThat(second.reason()).contains("wrongly");
        }
    }

    @Test
    @DisplayName("a rightly answered item is not asked again")
    void aRightlyAnsweredItemIsNotAskedAgain(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            ExerciseSelector.Selection first = workbench.nextExercise("photosynthesis");
            assertThat(first).isNotNull();
            // The learner answers it correctly, with the answer the item
            // holds.
            AssessmentItem item = first.item();
            workbench.assess("photosynthesis", item.id(),
                    String.join(" ", item.keyTerms()), 0.9, "test");

            ExerciseSelector.Selection second = workbench.nextExercise("photosynthesis");
            assertThat(second).isNotNull();
            assertThat(second.item().id()).isNotEqualTo(first.item().id());
        }
    }

    @Test
    @DisplayName("the exercise is an item the dataset holds, and it is never invented")
    void theExerciseIsAnItemTheDatasetHolds(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            List<String> itemIds = workbench.dataset("photosynthesis").items().stream()
                    .map(AssessmentItem::id).toList();

            for (ExerciseSelector.Selection selection : workbench.nextExercises("photosynthesis", 8)) {
                assertThat(itemIds).contains(selection.item().id());
                assertThat(selection.item().prompt()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("the session's exercises do not repeat one another")
    void theSessionsExercisesDoNotRepeatOneAnother(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            List<ExerciseSelector.Selection> selections = workbench.nextExercises("photosynthesis", 6);

            assertThat(selections).isNotEmpty();
            assertThat(selections.stream().map(s -> s.item().id()).distinct().count())
                    .isEqualTo(selections.size());
        }
    }

    @Test
    @DisplayName("the held-out item is the independent test, and it is chosen last")
    void theHeldOutItemIsTheIndependentTest(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // The learner attempts EVERY training item CORRECTLY, so the
            // only items left are the held-out ones - and the selection
            // must be one of them.
            for (AssessmentItem item : workbench.dataset("photosynthesis").items()) {
                if (!item.heldOut()) {
                    workbench.assess("photosynthesis", item.id(),
                    String.join(" ", item.keyTerms()), 0.9, "test");
                }
            }

            ExerciseSelector.Selection selection = workbench.nextExercise("photosynthesis");
            assertThat(selection).isNotNull();
            assertThat(selection.heldOut()).isTrue();
            assertThat(selection.item().heldOut()).isTrue();
        }
    }

    @Test
    @DisplayName("the selection says why it was made")
    void theSelectionSaysWhyItWasMade(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            ExerciseSelector.Selection selection = workbench.nextExercise("photosynthesis");

            assertThat(selection.reason()).isNotBlank();
            assertThat(selection.dimension()).isNotNull();
            // And the dimension is one the dataset holds.
            assertThat(workbench.dataset("photosynthesis").items())
                    .anySatisfy(item -> assertThat(item.dimension())
                            .isEqualTo(selection.dimension()));
        }
    }
}
