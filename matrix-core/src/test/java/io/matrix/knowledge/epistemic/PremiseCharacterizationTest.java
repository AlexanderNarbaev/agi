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
 * P0.1 — the CHARACTERIZATION of the asking path on premise-bearing questions.
 *
 * <p><b>Why this class exists.</b> The repair changed what the production asking path does
 * with a premise, and a repair that changes a serving path without saying what it changed is
 * a repair nobody can review. This class pins the behaviour that was DELIBERATE before the
 * change and that the change had to keep:
 *
 * <ul>
 *   <li>a question whose premise the workspace DENIES is refused, and the refusal names the
 *       claim that denies it — the W42 benchmark's false-premise item;</li>
 *   <li>a question whose premise the workspace SUPPORTS is ANSWERED — a repair that refuses
 *       the supported premises would refuse everything, and refusing everything is not
 *       success;</li>
 *   <li>a question with no premise is answered exactly as before, with no note about a
 *       premise;</li>
 *   <li>a question the workspace cannot answer for a reason of its own is still abstained
 *       from, and the abstention says what the workspace does not know rather than blaming a
 *       premise.</li>
 * </ul>
 *
 * <p><b>What this class does not claim.</b> It does not measure the new dispositions — the
 * supported/refuted/unsupported/conflicting/ambiguous semantics are measured by
 * {@link PremiseSemanticsTest}, and the served response by
 * {@link PremiseReportResponseTest}. This class is the floor: the parts of the old behaviour
 * that had to survive.
 */
class PremiseCharacterizationTest {

    /** The workspace the bundled photosynthesis package is loaded into. */
    private static final String PHOTOSYNTHESIS = "photosynthesis";

    /** The workspace the bundled logic package is loaded into. */
    private static final String LOGIC = "logic";

    /** The false-premise item of the W42 benchmark: the premise the workspace denies. */
    private static final String FALSE_PREMISE =
            "Since photosynthesis produces oxygen from carbon dioxide, how much oxygen does a "
                    + "plant release in an hour?";

    /** A supported premise: the workspace states it. */
    private static final String SUPPORTED_PREMISE =
            "Since photosynthesis converts light energy into chemical energy, what is the role of "
                    + "RuBisCO?";

    /** A premise the workspace knows nothing about. */
    private static final String UNKNOWN_PREMISE =
            "Since the moon is made of green cheese, what is the role of RuBisCO?";

    /** A plain question with no premise at all. */
    private static final String NO_PREMISE = "What is photosynthesis?";

    /** The false-premise item of the logic package: the workspace denies the premise. */
    private static final String LOGIC_FALSE_PREMISE =
            "Since every valid argument has true premises, what are the premises of modus ponens?";

    @TempDir
    Path directory;

    @Test
    @DisplayName("a premise the workspace denies is refused, and the claim that denies it is named")
    void aDeniedPremiseIsRefusedAndNamesTheClaim() throws Exception {
        try (KnowledgeWorkbench bench = workbench()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, FALSE_PREMISE, AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered())
                    .as("a question asked under a premise the workspace denies is not answered")
                    .isFalse();
            assertThat(answer.abstentionReason())
                    .as("the refusal says that the premise is what is wrong")
                    .contains("premise")
                    .contains("contradicted");
            assertThat(answer.abstentionReason())
                    .as("the refusal names the claim that denies the premise")
                    .contains("oxygen");
            assertThat(answer.premise())
                    .as("the disposition is carried by the answer")
                    .isNotNull();
            assertThat(answer.premise().status())
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.premise().evidence())
                    .as("the claim that denies the premise is cited by its identifier")
                    .anySatisfy(reference -> {
                        assertThat(reference.relation()).isEqualTo("CONTRADICTS");
                        assertThat(reference.claimId()).isNotBlank();
                        assertThat(reference.sourceId()).isNotBlank();
                    });
        }
    }

    @Test
    @DisplayName("a premise the workspace supports is answered, and the answer is kept")
    void aSupportedPremiseIsAnswered() throws Exception {
        try (KnowledgeWorkbench bench = workbench()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, SUPPORTED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered())
                    .as("a supported premise must not cost the user the answer")
                    .isTrue();
            assertThat(answer.statement()).isNotBlank();
            assertThat(answer.premise().status())
                    .as("the workspace states the premise")
                    .isEqualTo(PremiseSemantics.Status.SUPPORTED);
            assertThat(answer.premise().refuses())
                    .as("a supported premise never stops the question")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a question with no premise is answered exactly as before")
    void aQuestionWithNoPremiseIsAnswered() throws Exception {
        try (KnowledgeWorkbench bench = workbench()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, NO_PREMISE, AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered()).isTrue();
            assertThat(answer.claims()).isNotEmpty();
            assertThat(answer.premise().status())
                    .as("the question asserts no premise, and the disposition says so")
                    .isEqualTo(PremiseSemantics.Status.NO_PREMISE);
            assertThat(answer.limitations())
                    .as("a question with no premise carries no note about a premise")
                    .noneSatisfy(limitation -> assertThat(limitation).contains("premise"));
        }
    }

    @Test
    @DisplayName("a premise the workspace knows nothing about is not a refusal of the premise")
    void anUnknownPremiseIsNotCalledFalse() throws Exception {
        try (KnowledgeWorkbench bench = workbench()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, UNKNOWN_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status())
                    .as("nothing in the workspace bears on the premise")
                    .isEqualTo(PremiseSemantics.Status.UNSUPPORTED);
            assertThat(answer.premise().status())
                    .as("an unsupported premise is never labelled false")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.abstentionReason())
                    .as("the abstention is about what the workspace does not cover, not about "
                            + "a false premise")
                    .doesNotContain("premise of the question is contradicted");
        }
    }

    @Test
    @DisplayName("the logic package's false premise is still refused")
    void theLogicFalsePremiseIsStillRefused() throws Exception {
        try (KnowledgeWorkbench bench = workbench()) {
            Answer answer = bench.ask(LOGIC, LOGIC_FALSE_PREMISE, AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered())
                    .as("the logic package's false premise is refused, as it was")
                    .isFalse();
            assertThat(answer.abstentionReason()).contains("premise");
            assertThat(answer.premise().status())
                    .isEqualTo(PremiseSemantics.Status.REFUTED);
        }
    }

    @Test
    @DisplayName("the disposition is deterministic: the same question gives the same disposition")
    void theDispositionIsDeterministic() throws Exception {
        try (KnowledgeWorkbench bench = workbench()) {
            Answer first = bench.ask(PHOTOSYNTHESIS, SUPPORTED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);
            Answer second = bench.ask(PHOTOSYNTHESIS, SUPPORTED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(second.premise().status()).isEqualTo(first.premise().status());
            assertThat(second.premise().premise()).isEqualTo(first.premise().premise());
            assertThat(second.premise().reason()).isEqualTo(first.premise().reason());
        }
    }

    /**
     * A workbench with the two bundled packages loaded.
     *
     * @return the workbench
     * @throws Exception when the store cannot be opened
     */
    private KnowledgeWorkbench workbench() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("workbench.sqlite")
                        .toString());
        bench.loadBundledPackage(PHOTOSYNTHESIS);
        bench.loadBundledPackage(LOGIC);
        return bench;
    }
}
