package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.5 — a question's premise is checked against the workspace.
 *
 * <p>The mission's §14 asks the system to refuse a question it cannot
 * support, and the benchmark holds two questions whose premise is false.
 *
 * <p>The first version of this check was a SUPPORT test: the premise had to
 * be covered by a supported claim. It was measured and rejected - it
 * refused a legitimate multi-hop question and could not tell the two cases
 * apart. This version is a REFUTATION test, against the claims the
 * workspace already holds: the photosynthesis package holds the
 * misconception as a REFUTED claim, and the logic package holds a verified
 * claim that denies the second premise.
 */
class PremiseCheckerTest {

    @Test
    @DisplayName("the premise of a question is found")
    void thePremiseOfAQuestionIsFound() {
        assertThat(PremiseChecker.premiseOf(
                "Since photosynthesis produces oxygen from carbon dioxide, how much oxygen "
                        + "does a plant release in an hour?"))
                .isEqualTo("photosynthesis produces oxygen from carbon dioxide");
        assertThat(PremiseChecker.premiseOf(
                "Since every valid argument has true premises, what are the premises "
                        + "of modus ponens?"))
                .isEqualTo("every valid argument has true premises");
    }

    @Test
    @DisplayName("a question with no premise has none")
    void aQuestionWithNoPremiseHasNone() {
        assertThat(PremiseChecker.premiseOf("What is photosynthesis?")).isNull();
        assertThat(PremiseChecker.premiseOf("Что такое фотосинтез?")).isNull();
        assertThat(PremiseChecker.premiseOf(null)).isNull();
    }

    @Test
    @DisplayName("a conditional question poses the conditional; it does not assert a premise")
    void aConditionalQuestionPosesTheConditional() {
        // This was measured: with "if " among the premise markers, three
        // legitimate MULTI_HOP items were refused and the answer accuracy
        // fell from 0.792 to 0.708.
        assertThat(PremiseChecker.premiseOf("If p implies q and p is true, what follows?"))
                .isNull();
        assertThat(PremiseChecker.premiseOf(
                "If light reactions split water and release oxygen, what does the Calvin cycle do?"))
                .isNull();
    }

    @Test
    @DisplayName("the misconception the workspace holds as refuted refutes the premise")
    void theMisconceptionTheWorkspaceHoldsAsRefutedRefutesThePremise(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            String premise = "photosynthesis produces oxygen from carbon dioxide";
            Claim refutation = PremiseChecker.refutation(premise,
                    workbench.workspace("photosynthesis"));

            assertThat(refutation).isNotNull();
            assertThat(refutation.status()).isEqualTo(ClaimStatus.REFUTED);
            assertThat(refutation.proposition()).contains("carbon dioxide");
        }
    }

    @Test
    @DisplayName("a claim that denies the premise refutes it")
    void aClaimThatDeniesThePremiseRefutesIt(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("logic");

            String premise = "every valid argument has true premises";
            Claim refutation = PremiseChecker.refutation(premise, workbench.workspace("logic"));

            assertThat(refutation).isNotNull();
            assertThat(refutation.proposition()).contains("validity says nothing");
        }
    }

    @Test
    @DisplayName("a premise the workspace holds is not refuted")
    void aPremiseTheWorkspaceHoldsIsNotRefuted(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            assertThat(PremiseChecker.refutation(
                    "photosynthesis converts light energy into chemical energy",
                    workbench.workspace("photosynthesis"))).isNull();
            assertThat(PremiseChecker.refutation(
                    "the light-dependent reactions split water",
                    workbench.workspace("photosynthesis"))).isNull();
        }
    }

    @Test
    @DisplayName("the question with a false premise is refused, and the refusal names it")
    void theQuestionWithAFalsePremiseIsRefusedAndTheRefusalNamesIt(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    "Since photosynthesis produces oxygen from carbon dioxide, how much oxygen "
                            + "does a plant release in an hour?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isFalse();
            assertThat(answer.abstentionReason()).contains("premise");
            assertThat(answer.limitations()).anySatisfy(limitation ->
                    assertThat(limitation).contains("carbon dioxide"));
        }
    }

    @Test
    @DisplayName("the question with a supported premise is still answered")
    void theQuestionWithASupportedPremiseIsStillAnswered(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            Answer answer = workbench.ask("photosynthesis",
                    "Since photosynthesis converts light energy into chemical energy, "
                            + "where does the released oxygen come from?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
        }
    }
}
