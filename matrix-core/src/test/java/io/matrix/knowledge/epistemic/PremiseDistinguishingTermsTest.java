package io.matrix.knowledge.epistemic;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.PremiseSemantics;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W53 round 5 — the same-assertion rule, and the distinguishing terms that tell two
 * statements apart.
 *
 * <p><b>What this test is.</b> The photosynthesis package holds the misconception "The
 * oxygen a plant releases comes from the carbon dioxide it absorbs" as a REFUTED claim, and
 * a claim the workspace calls false may refute ONLY the proposition it repeats. The rule that
 * decided this used to compare the OVERLAP of the two texts: five of the premise's six terms
 * were covered by the claim, so the premise "Since the oxygen a plant releases comes from
 * the water it absorbs" was held to be the misconception, and a premise the corpus STATES
 * was refused. An overlap cannot tell two statements that differ in one word apart.</p>
 *
 * <p><b>What it proves.</b> The distinguishing terms have to match. The premise about the
 * water is not the misconception — it is the truth the corpus states, and it is answered. The
 * premise that repeats the misconception in other words is still the misconception and is
 * still refused. The same premise in another language is the same assertion and is refused
 * as well — the bilingual bridge has to reach it, and a premise that names a word the
 * lexicon has never seen must not be unreachable because of it.</p>
 *
 * <p><b>Where the questions come from.</b> The bundled photosynthesis package, loaded through
 * the production path. Nothing here is written down from the frozen held-out set: the
 * questions are the workspace's own.</p>
 */
class PremiseDistinguishingTermsTest {

    /** The workspace the bundled photosynthesis package is loaded into. */
    private static final String PHOTOSYNTHESIS = "photosynthesis";

    /** The premise the corpus STATES: the oxygen comes from the water. */
    private static final String THE_WATER_PREMISE =
            "Since the oxygen a plant releases comes from the water it absorbs, "
                    + "what does a plant release during the day?";

    /** The same assertion in other words: the misconception itself. */
    private static final String THE_SYNONYM_PREMISE =
            "Since the oxygen a plant releases comes from the carbon dioxide it takes in, "
                    + "what does a plant release during the day?";

    /** The misconception in the language the bilingual lexicon bridges. */
    private static final String THE_MISCONCEPTION_IN_RUSSIAN =
            "Поскольку кислород растения происходит из углекислого газа, "
                    + "который растение поглощает, что растение выделяет днём?";

    /** A premise about the same subject that states a DIFFERENT proposition. */
    private static final String THE_SAME_SUBJECT =
            "Since plants release oxygen during the day, what does a plant release during "
                    + "the day?";

    /** A premise that repeats the misconception word for word. */
    private static final String THE_MISCONCEPTION =
            "Since the oxygen a plant releases comes from the carbon dioxide it absorbs, "
                    + "what does a plant release during the day?";

    @TempDir
    Path directory;

    @Test
    @DisplayName("T1: a premise the corpus states is not the misconception the workspace holds false")
    void aPremiseTheCorpusStatesIsNotTheMisconception() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_WATER_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the premise differs from the misconception in the term that tells "
                            + "them apart ('water' against 'carbon dioxide')")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a premise the corpus does not deny is not a reason to refuse")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("T1: the misconception in other words is still the misconception")
    void theMisconceptionInOtherWordsIsStillTheMisconception() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_SYNONYM_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("'takes in' and 'absorbs' name one thing, so the premise states the "
                            + "claim the workspace holds false")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a refuted premise refuses the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("T1: the misconception in another language is the same assertion")
    void theMisconceptionInAnotherLanguageIsTheSameAssertion() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_MISCONCEPTION_IN_RUSSIAN,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the bilingual bridge carries the Russian premise to the same claim")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a refuted premise refuses the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("T1: the premise that repeats the misconception is refused")
    void thePremiseThatRepeatsTheMisconceptionIsRefused() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_MISCONCEPTION,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the premise states the proposition the workspace holds false")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a refuted premise refuses the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("T1: a premise about the same subject that states another proposition is not false")
    void aPremiseAboutTheSameSubjectIsNotCalledFalse() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_SAME_SUBJECT,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the misconception denies something else, and a claim the workspace "
                            + "calls false is no evidence about another proposition")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a premise the corpus does not deny is not a reason to refuse")
                    .isTrue();
        }
    }

    private KnowledgeWorkbench photosynthesis() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createTempDirectory("premise-distinguishing-").resolve("w.sqlite").toString());
        bench.loadBundledPackage("photosynthesis");
        return bench;
    }
}
