package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the conversation survives a restart.
 *
 * <p>The mission's §6 asks for a bounded PERSISTED conversation history,
 * and names the case: "Let's return to the topic we studied yesterday" and
 * "Explain this in more detail". The context used to be held in memory
 * only, so after a restart the referent was gone.
 */
class PersistedDialogueTest {

    @Test
    @DisplayName("the referent survives a restart")
    void theReferentSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is the Calvin-Benson cycle?",
                    AbstractionLevel.INTRODUCTORY);
        }

        // A fresh workbench over the same database: the context is empty
        // until it is loaded from the store.
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            DialogueContext context = workbench.context("photosynthesis");
            assertThat(context.turns()).isNotEmpty();
            assertThat(context.turns().get(0).question()).isEqualTo("What is the Calvin-Benson cycle?");

            // And the reference resolves against the previous turn.
            Answer answer = workbench.ask("photosynthesis", "Explain this in more detail",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(answer.limitations()).anySatisfy(limitation ->
                    assertThat(limitation).contains("read as being about"));
        }
    }

    @Test
    @DisplayName("the persisted turn keeps what the reference needs")
    void thePersistedTurnKeepsWhatTheReferenceNeeds(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is the Calvin-Benson cycle?",
                    AbstractionLevel.INTRODUCTORY);

            DialogueContext.Turn turn = workbench.context("photosynthesis").turns()
                    .get(workbench.context("photosynthesis").turns().size() - 1);
            assertThat(turn.conceptNames()).isNotEmpty();
            assertThat(turn.conceptIds()).isNotEmpty();
            assertThat(turn.language()).isNotBlank();
            assertThat(turn.act()).isNotNull();
            assertThat(turn.id()).isNotBlank();
        }
    }

    @Test
    @DisplayName("the history is bounded")
    void theHistoryIsBounded(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            for (int i = 0; i < DialogueContext.MAXIMUM_TURNS + 10; i++) {
                workbench.ask("photosynthesis", "What is photosynthesis " + i + "?",
                        AbstractionLevel.INTRODUCTORY);
            }

            // The context keeps the last MAXIMUM_TURNS, and the store's
            // live turns are bounded the same way.
            assertThat(workbench.context("photosynthesis").size())
                    .isLessThanOrEqualTo(DialogueContext.MAXIMUM_TURNS);
            assertThat(workbench.store().dialogueTurns("photosynthesis",
                    DialogueContext.MAXIMUM_TURNS + 10).size())
                    .isLessThanOrEqualTo(DialogueContext.MAXIMUM_TURNS);
        }
    }

    @Test
    @DisplayName("the turns that fall out of the working memory are marked, not deleted")
    void theTurnsThatFallOutOfTheWorkingMemoryAreMarkedNotDeleted(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            for (int i = 0; i < DialogueContext.MAXIMUM_TURNS + 5; i++) {
                workbench.ask("photosynthesis", "What is photosynthesis " + i + "?",
                        AbstractionLevel.INTRODUCTORY);
            }

            // The table still holds every turn; only the live ones are
            // within the bound.
            assertThat(workbench.store().dialogueTurns("photosynthesis", 1000).size())
                    .isLessThanOrEqualTo(DialogueContext.MAXIMUM_TURNS);
            // And the store knows how many it marked.
            assertThat(workbench.store().forgottenAskedQuestions("photosynthesis")).isNotNull();
        }
    }

    @Test
    @DisplayName("the persisted conversation survives a restart")
    void thePersistedConversationSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "Что такое цикл Кальвина?",
                    AbstractionLevel.INTRODUCTORY);
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            List<DialogueContext.Turn> turns = workbench.context("photosynthesis").turns();
            assertThat(turns).hasSize(1);
            assertThat(turns.get(0).language()).isEqualTo("ru");
            assertThat(turns.get(0).question()).isEqualTo("Что такое цикл Кальвина?");
        }
    }
}
