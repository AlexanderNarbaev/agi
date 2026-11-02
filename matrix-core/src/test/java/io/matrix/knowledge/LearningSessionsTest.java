package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the sessions of practice.
 *
 * <p>The mission's §9 names a Learning Sessions view. The workspace
 * recorded the outcomes but never grouped them, so a reader could not see
 * that the learner practised in sessions at all.
 */
class LearningSessionsTest {

    @Test
    @DisplayName("the outcomes of a session are grouped into one session")
    void theOutcomesOfASessionAreGroupedIntoOneSession(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            List<AssessmentItem> items = workbench.dataset("photosynthesis").items();

            workbench.assess("photosynthesis", items.get(0).id(), "a wrong answer", 0.3, "session-1");
            workbench.assess("photosynthesis", items.get(1).id(), "a wrong answer", 0.3, "session-1");
            workbench.assess("photosynthesis", items.get(2).id(), "another wrong answer", 0.3,
                    "session-2");

            List<KnowledgeWorkbench.LearningSession> sessions =
                    workbench.learningSessions("photosynthesis");

            assertThat(sessions).hasSize(2);
            KnowledgeWorkbench.LearningSession first = sessions.get(0);
            assertThat(first.sessionId()).isEqualTo("session-1");
            assertThat(first.items()).isEqualTo(2);
            assertThat(first.correct()).isEqualTo(0);
            assertThat(first.concepts()).isNotEmpty();
            assertThat(first.firstAnsweredAt()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("a workspace with no practice has no sessions")
    void aWorkspaceWithNoPracticeHasNoSessions(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            assertThat(workbench.learningSessions("photosynthesis")).isEmpty();
        }
    }

    @Test
    @DisplayName("the sessions are ordered by their first answer")
    void theSessionsAreOrderedByTheirFirstAnswer(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            List<AssessmentItem> items = workbench.dataset("photosynthesis").items();

            workbench.assess("photosynthesis", items.get(0).id(), "a wrong answer", 0.3, "later");
            workbench.assess("photosynthesis", items.get(1).id(), "a wrong answer", 0.3, "earlier");

            List<KnowledgeWorkbench.LearningSession> sessions =
                    workbench.learningSessions("photosynthesis");

            assertThat(sessions).hasSize(2);
            assertThat(sessions.get(0).firstAnsweredAt())
                    .isLessThanOrEqualTo(sessions.get(1).firstAnsweredAt());
        }
    }
}
