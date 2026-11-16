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
 * The three repairs of this wave, as regressions.
 *
 * <p><b>T1 — the same assertion, not the same subject.</b> A claim the workspace calls false
 * refutes the proposition it REPEATS and no other: the photosynthesis package holds the
 * misconception "the oxygen a plant releases comes from the carbon dioxide it absorbs" as a
 * REFUTED claim, and the premise "plants release oxygen during the day" is about the same
 * subject and states a different proposition. The premise that repeats the misconception is
 * refused, in English and in Russian; the premise about the same subject is not.</p>
 *
 * <p><b>T2 — the ambiguity reaches the caller.</b> A corpus that holds accounts of the
 * subject that disagree with each other leaves the question ambiguous, and the disposition
 * says so in words: the reason names the two accounts that disagree, and the readings carry
 * them.</p>
 *
 * <p><b>Where the corpora come from.</b> The bundled photosynthesis package and the licensed
 * research pool, imported through the production acquisition path. Nothing here is written
 * down from the frozen held-out set: the questions are the workspace's own.</p>
 */
class PremiseSameAssertionAndAmbiguityTest {

    /** The workspace the bundled photosynthesis package is loaded into. */
    private static final String PHOTOSYNTHESIS = "photosynthesis";

    /** The workspace the licensed research pool is imported into. */
    private static final String POOL = "premise-pool";

    /** The premise that repeats the claim the workspace calls false. */
    private static final String REPEATS_THE_MISCONCEPTION =
            "Since the oxygen a plant releases comes from the carbon dioxide it absorbs, "
                    + "how much oxygen does a plant release in an hour?";

    /** The same premise in Russian: the same proposition in another language. */
    private static final String REPEATS_THE_MISCONCEPTION_IN_RUSSIAN =
            "Поскольку кислород растения берётся из диоксида углерода, сколько кислорода "
                    + "растение даёт за час?";

    /** A premise about the same subject that states a DIFFERENT proposition. */
    private static final String THE_SAME_SUBJECT =
            "Since plants release oxygen during the day, what does a plant release during the "
                    + "day?";

    /** A question about the drivers of the plate motion, which the corpus does not settle. */
    private static final String THE_DRIVERS =
            "What forces drive the motion of the plates?";

    @TempDir
    Path directory;

    @Test
    @DisplayName("T1: the premise that repeats the claim the workspace calls false is refused")
    void thePremiseThatRepeatsTheMisconceptionIsRefused() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer english = bench.ask(PHOTOSYNTHESIS, REPEATS_THE_MISCONCEPTION,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(english.premise().status())
                    .as("the premise repeats the proposition the workspace calls false")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(english.answered())
                    .as("a refuted premise refuses the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("T1: the same premise in Russian is refused as well")
    void theSamePremiseInRussianIsRefused() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer russian = bench.ask(PHOTOSYNTHESIS, REPEATS_THE_MISCONCEPTION_IN_RUSSIAN,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(russian.premise().status())
                    .as("the bilingual bridge carries the premise to the same claim")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
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

    @Test
    @DisplayName("T2: accounts of the subject that disagree make the question ambiguous")
    void accountsThatDisagreeMakeTheQuestionAmbiguous() throws Exception {
        try (KnowledgeWorkbench bench = pool()) {
            Answer answer = bench.ask(POOL, THE_DRIVERS, AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the corpus holds accounts of the drivers that cannot both hold")
                    .isEqualTo(PremiseSemantics.Status.AMBIGUOUS);
            assertThat(answer.premise().reason().toLowerCase(java.util.Locale.ROOT))
                    .as("the reason says that the question is ambiguous")
                    .contains("ambig");
            assertThat(answer.premise().reason())
                    .as("the reason names the accounts that disagree")
                    .contains("claim clm-");
            assertThat(answer.premise().readings())
                    .as("the readings carry the accounts that disagree")
                    .hasSizeGreaterThanOrEqualTo(2);
            assertThat(answer.answered())
                    .as("an ambiguity does not refuse the question")
                    .isTrue();
        }
    }

    private KnowledgeWorkbench photosynthesis() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createTempDirectory("premise-photosynthesis-").resolve("w.sqlite").toString());
        bench.loadBundledPackage("photosynthesis");
        return bench;
    }

    private KnowledgeWorkbench pool() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createTempDirectory("premise-pool-").resolve("w.sqlite").toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            bench.importDocument(POOL, entry.path(), entry.file(), entry.language());
        }
        return bench;
    }
}
