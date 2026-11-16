package io.matrix.knowledge.epistemic;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.PremiseSemantics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W53 — the denial at the subject level: a claim that denies the premise's subject refutes it
 * even when it does not carry the premise's distinguishing term.
 *
 * <p><b>What this test is.</b> The photosynthesis package holds the misconception "The oxygen
 * a plant releases comes from the carbon dioxide it absorbs" as a REFUTED claim, and it holds
 * the corpus's own correction of it in the SCOPE of the claim that carries the truth: "the
 * oxygen originates from water, not from carbon dioxide". A premise that repeats the
 * misconception in other words — naming a term the misconception claim does not carry — names
 * a distinguishing term the correction does not carry either, so the coverage of the premise's
 * terms falls below the floor of the refutation scan, the correction was never read, and the
 * premise the corpus DENIES was answered.</p>
 *
 * <p><b>What it proves.</b> The correction refutes the premise, and the refusal names the
 * corpus's own account. The negative controls are the two premises the corpus does not deny:
 * the one it STATES (the oxygen comes from the water) and the one about the same subject that
 * states a DIFFERENT proposition (plants release oxygen during the day). A denial at the
 * subject level is enough, and it is enough only about the premise's own subject.</p>
 *
 * <p><b>Where the questions come from.</b> The bundled photosynthesis package, loaded through
 * the production path. Nothing here is written down from the frozen held-out set: the
 * questions are the workspace's own.</p>
 */
class PremiseSubjectDenialTest {

    /** The workspace the bundled photosynthesis package is loaded into. */
    private static final String PHOTOSYNTHESIS = "photosynthesis";

    /**
     * The premise the corpus DENIES, in words the misconception claim does not carry.
     *
     * <p>The distinguishing term is the verb: the claim says the oxygen COMES FROM the carbon
     * dioxide, the premise says photosynthesis PRODUCES it from it. The two say one thing, and
     * the corpus's correction denies it.</p>
     */
    private static final String THE_MISCONCEPTION_IN_OTHER_WORDS =
            "Since photosynthesis produces the oxygen of a plant from the carbon dioxide it "
                    + "absorbs, what does a plant release during the day?";

    /** The same premise with two more words for the same two relations. */
    private static final String THE_MISCONCEPTION_IN_OTHER_WORDS_AGAIN =
            "Given that photosynthesis gives the plant its oxygen from the carbon dioxide it "
                    + "takes in, what does a plant release during the day?";

    /** The premise the corpus STATES: the oxygen comes from the water. */
    private static final String THE_WATER_PREMISE =
            "Since the oxygen a plant releases comes from the water it absorbs, "
                    + "what does a plant release during the day?";

    /** A premise about the same subject that states a DIFFERENT proposition. */
    private static final String THE_SAME_SUBJECT =
            "Since plants release oxygen during the day, what does a plant release during "
                    + "the day?";

    @TempDir
    Path directory;

    @Test
    @DisplayName("T1: a premise the corpus denies in other words is refused")
    void aPremiseTheCorpusDeniesInOtherWordsIsRefused() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_MISCONCEPTION_IN_OTHER_WORDS,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the corpus denies this proposition: its correction says the oxygen "
                            + "originates from the water, not from the carbon dioxide")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a refuted premise refuses the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("T1: the same rule with two more words for the same two relations")
    void theSameRuleWithOtherWords() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_MISCONCEPTION_IN_OTHER_WORDS_AGAIN,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the denial at the subject level does not depend on the wording")
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a refuted premise refuses the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("T1: the refusal names the corpus's own account")
    void theRefusalNamesTheCorpusAccount() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_MISCONCEPTION_IN_OTHER_WORDS,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().reason())
                    .as("the reader has to see WHICH account denies the premise")
                    .contains("oxygen")
                    .contains("water");
        }
    }

    @Test
    @DisplayName("T1: the premise the corpus states is not refused")
    void thePremiseTheCorpusStatesIsNotRefused() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_WATER_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the corpus's correction denies the carbon dioxide, and this premise "
                            + "does not say that")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a premise the corpus does not deny is not a reason to refuse")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("T1: a premise about the same subject that states another proposition is not refused")
    void aPremiseAboutTheSameSubjectIsNotRefused() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, THE_SAME_SUBJECT,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("the correction denies where the oxygen comes from, and this premise "
                            + "does not say where it comes from")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a premise the corpus does not deny is not a reason to refuse")
                    .isTrue();
        }
    }

    private KnowledgeWorkbench photosynthesis() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createTempDirectory("premise-subject-denial-").resolve("w.sqlite").toString());
        bench.loadBundledPackage(PHOTOSYNTHESIS);
        return bench;
    }
}
