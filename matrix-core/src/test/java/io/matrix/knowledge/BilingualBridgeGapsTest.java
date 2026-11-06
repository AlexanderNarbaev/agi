package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the bilingual bridge and the words it covered.
 *
 * <p>A Russian question and an English claim share no token, so the
 * bilingual lexicon carries the counterpart into the query. The
 * counterpart is a SEARCH TERM: it does not make the claim true and it
 * does not make the two propositions equivalent.
 *
 * <p>Two defects lived here, and both were invisible in the English
 * measurements:
 *
 * <ul>
 *   <li>the raw words the user wrote were keyed by the ENGLISH fold,
 *       which is a no-op for a Cyrillic word — so a Russian question's
 *       words were never paired with their stems;</li>
 *   <li>the counterpart's credit removed the STEM from the uncovered
 *       list, while the uncovered list holds the RAW words — so the word
 *       the user actually wrote was reported as "not covered by the
 *       workspace" in an answer that had just covered it.</li>
 * </ul>
 */
class BilingualBridgeGapsTest {

    @Test
    @DisplayName("a Russian question answered through the bridge reports no uncovered word")
    void aRussianQuestionAnsweredThroughTheBridgeReportsNoUncoveredWord(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // "Что такое цикл Кальвина?" — "What is the Calvin cycle?". The
            // question's words carry the case IN THE WORD, so the Russian
            // fold strips the ending: "кальвина" folds to "кальвин", and the
            // lexicon bridges "кальвин" to "calvin".
            Answer answer = workbench.ask("photosynthesis", "Что такое цикл Кальвина?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            assertThat(answer.gaps()).isEmpty();
            assertThat(answer.concepts().stream().map(Concept::name))
                    .contains("The Calvin-Benson cycle");
            assertThat(answer.limitations().stream()
                    .anyMatch(note -> note.contains("Not covered by the workspace")))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a Russian question whose case ending is a stem still pairs the raw word")
    void aRussianQuestionWhoseCaseEndingIsAStemStillPairsTheRawWord(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // "Откуда растение берёт кислород?" — the words are in the
            // genitive case, so the raw words and the stems differ for every
            // one of them. "растение" (a plant) and "кислород" (oxygen)
            // reach the knowledge through the bridge, and the answer must
            // not report them as uncovered. "берёт" (takes) is a different
            // matter: the workspace's claims say "absorb" and "takes in",
            // and nothing bridges this verb, so it may remain a gap — the
            // gap is honest there.
            Answer answer = workbench.ask("photosynthesis", "Откуда растение берёт кислород?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            assertThat(answer.gaps()).doesNotContain("растение", "кислород");
        }
    }

    @Test
    @DisplayName("an English question that the workspace cannot support still reports the gap")
    void anEnglishQuestionThatTheWorkspaceCannotSupportStillReportsTheGap(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // The pairing must not swallow a word the workspace really does
            // not cover.
            Answer answer = workbench.ask("photosynthesis",
                    "What is the melting point of mercury?", AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isFalse();
            assertThat(answer.gaps()).isNotEmpty();
        }
    }
}
