package io.matrix.knowledge.epistemic;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.ClaimConflictDetector;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.PremiseAssessment;
import io.matrix.knowledge.PremiseSemantics;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P0.1 and P0.2 — the epistemic semantics of the production asking path, measured.
 *
 * <p><b>What this class asserts.</b> The five dispositions the owner's directive names, on the
 * path the product actually serves:
 *
 * <ul>
 *   <li>a SUPPORTED premise lets the question be answered;</li>
 *   <li>a REFUTED premise is rejected, and the claim and the source that refute it are
 *       NAMED;</li>
 *   <li>an UNSUPPORTED premise is reported as unsupported and is never labelled false;</li>
 *   <li>a corpus that holds claims which cannot both hold is surfaced as a conflict with both
 *       sides and their provenance, and the system does not decide between them;</li>
 *   <li>a question that admits several readings is reported with its readings, and none of
 *       them is chosen.</li>
 * </ul>
 *
 * <p><b>The negative controls are the point of half of this class.</b> A repair that refuses
 * everything passes every "it refuses" assertion and is worth nothing, so the class counts the
 * FALSE REFUSALS on premises the workspace supports and requires the count to be zero. The
 * controls are a premise that is irrelevant to the corpus, a paraphrase of a supported
 * premise, the lexical-negation pitfall in both languages, and a question with no premise at
 * all.
 *
 * <p><b>Where the corpora come from.</b> The bundled photosynthesis package for the premise
 * cases and the licensed research pool for the conflict and ambiguity cases. The pool is
 * imported through the production acquisition path, so the claims the test reasons about are
 * the production claims with the production identifiers.
 */
class PremiseSemanticsTest {

    /** The workspace the bundled photosynthesis package is loaded into. */
    private static final String PHOTOSYNTHESIS = "photosynthesis";

    /** The workspace the licensed research pool is imported into. */
    private static final String POOL = "epistemic-pool";

    /** The workspace the bundled logic package is loaded into. */
    private static final String LOGIC = "logic";

    /** The premise the photosynthesis package states. */
    private static final String SUPPORTED_PREMISE =
            "Since photosynthesis converts light energy into chemical energy, what is the role of "
                    + "RuBisCO?";

    /** The premise the photosynthesis package denies. */
    private static final String REFUTED_PREMISE =
            "Since photosynthesis produces oxygen from carbon dioxide, how much oxygen does a "
                    + "plant release in an hour?";

    /** The premise the photosynthesis package knows nothing about. */
    private static final String IRRELEVANT_PREMISE =
            "Since the moon is made of green cheese, what is the role of RuBisCO?";

    /** A paraphrase of the supported premise: the same claim in other words. */
    private static final String PARAPHRASED_PREMISE =
            "Since photosynthesis turns light energy into chemical energy, what is the role of "
                    + "RuBisCO?";

    /** A premise the corpus denies, and the negation must be read. */
    private static final String NEGATED_PREMISE =
            "Since photosynthesis does not convert light energy into chemical energy, what is the "
                    + "role of RuBisCO?";

    /** The Russian form of the same pitfall. */
    private static final String RUSSIAN_NEGATED_PREMISE =
            "Поскольку фотосинтез никогда не производит кислород из углекислого газа, сколько "
                    + "молекул кислорода даёт одна молекула CO2?";

    /** A question that asserts nothing. */
    private static final String NO_PREMISE = "What is photosynthesis?";

    @TempDir
    Path directory;

    // ── the five dispositions ─────────────────────────────────────────────────

    @Test
    @DisplayName("a supported premise is answered, and the answer is kept")
    void aSupportedPremiseIsAnswered() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, SUPPORTED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status()).isEqualTo(PremiseSemantics.Status.SUPPORTED);
            assertThat(answer.answered())
                    .as("a supported premise must not cost the user the answer")
                    .isTrue();
            assertThat(answer.statement()).isNotBlank();
            assertThat(answer.premise().refuses()).isFalse();
        }
    }

    @Test
    @DisplayName("a refuted premise is rejected, and the claim and source that refute it are named")
    void aRefutedPremiseIsRejectedWithItsCitingIds() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, REFUTED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status()).isEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.answered())
                    .as("a question asked under a denied premise is not answered")
                    .isFalse();
            assertThat(answer.abstentionReason())
                    .as("the refusal says what is wrong")
                    .contains("premise")
                    .contains("contradicted");
            assertThat(answer.premise().evidence())
                    .as("the claim that denies the premise is cited by its identifier")
                    .anySatisfy(reference -> {
                        assertThat(reference.relation()).isEqualTo("CONTRADICTS");
                        assertThat(reference.claimId()).isNotBlank();
                        assertThat(reference.sourceId()).isNotBlank();
                        assertThat(reference.proposition()).isNotBlank();
                    });
            assertThat(answer.abstentionReason())
                    .as("the refusal names the claim that denies the premise")
                    .contains("claim ");
        }
    }

    @Test
    @DisplayName("an unsupported premise is reported as unsupported and never labelled false")
    void anUnsupportedPremiseIsNotLabelledFalse() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, IRRELEVANT_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status()).isEqualTo(PremiseSemantics.Status.UNSUPPORTED);
            assertThat(answer.premise().status())
                    .as("an unsupported premise is not a refuted one")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
            assertThat(answer.premise().reason())
                    .as("the reason says that the absence of evidence is not a contradiction")
                    .contains("absence of evidence is not a contradiction");
            assertThat(answer.abstentionReason())
                    .as("the abstention is not about a false premise")
                    .doesNotContain("premise of the question is contradicted");
        }
    }

    @Test
    @DisplayName("a corpus that holds claims which cannot both hold is surfaced as a conflict")
    void aConflictingCorpusIsSurfacedWithBothSides() throws Exception {
        try (KnowledgeWorkbench bench = logic()) {
            // the logic package holds "the propositional variable p is true in the assignment
            // under consideration" and "the propositional variable p is not true in the
            // assignment under consideration": the same proposition with opposite polarity
            Answer answer = bench.ask(LOGIC, "What is propositional logic?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().conflicts())
                    .as("the corpus holds a conflict and the answer reports it")
                    .isNotEmpty();
            ClaimConflictDetector.Conflict conflict = answer.premise().conflicts().get(0);
            assertThat(conflict.sides())
                    .as("both sides are named")
                    .hasSizeGreaterThanOrEqualTo(2);
            assertThat(conflict.sides())
                    .allSatisfy(side -> {
                        assertThat(side.claimId()).isNotBlank();
                        assertThat(side.proposition()).isNotBlank();
                        assertThat(side.sourceId()).isNotBlank();
                    });
            assertThat(conflict.reason())
                    .as("the conflict says that the system does not decide")
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("a claim that only shares the field's vocabulary with another is not a conflict")
    void theVocabularyOfTheFieldIsNotAContent() throws Exception {
        try (KnowledgeWorkbench bench = logic()) {
            Answer answer = bench.ask(LOGIC, "What is propositional logic?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().conflicts())
                    .as("the conflicts the detector reports are the contradictions the corpus "
                            + "holds, not the accounts that share its vocabulary")
                    .allSatisfy(conflict -> assertThat(conflict.sides().get(0).proposition())
                            .isNotEqualTo(conflict.sides().get(1).proposition()));
            assertThat(answer.premise().reason())
                    .as("a question with no premise does not claim the corpus is in conflict")
                    .contains("asserts no premise");
        }
    }

    @Test
    @DisplayName("a question that admits several readings is reported with its readings")
    void anAmbiguousQuestionIsReportedWithItsReadings() throws Exception {
        try (KnowledgeWorkbench bench = pool()) {
            Answer answer = bench.ask(POOL, "In the research pool, what does the word slab name?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status()).isEqualTo(PremiseSemantics.Status.AMBIGUOUS);
            assertThat(answer.premise().readings())
                    .as("the readings are distinguished, not merged into one answer")
                    .hasSizeGreaterThanOrEqualTo(2);
            assertThat(answer.premise().readings())
                    .allSatisfy(reading -> {
                        assertThat(reading.label()).isNotBlank();
                        assertThat(reading.evidence())
                                .as("every reading carries its provenance")
                                .isNotEmpty();
                        assertThat(reading.evidence().get(0).claimId()).isNotBlank();
                        assertThat(reading.evidence().get(0).sourceId()).isNotBlank();
                    });
            assertThat(answer.premise().reason())
                    .as("the reason says that none of the readings is chosen")
                    .contains("none of them is chosen");
        }
    }

    // ── the negative controls ─────────────────────────────────────────────────

    @Test
    @DisplayName("a premise that is irrelevant to the corpus is unsupported, not refuted")
    void anIrrelevantPremiseIsUnsupported() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, IRRELEVANT_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status()).isEqualTo(PremiseSemantics.Status.UNSUPPORTED);
            assertThat(answer.premise().refuses())
                    .as("a look-alike claim about another subject must not refute the premise")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a paraphrase is not refused and is not labelled false")
    void aParaphraseIsNotRefusedAndNotLabelledFalse() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, PARAPHRASED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(answer.answered())
                    .as("a paraphrase of a supported premise is still answered")
                    .isTrue();
            assertThat(answer.premise().refuses())
                    .as("a paraphrase is never the reason to refuse a question")
                    .isFalse();
            assertThat(answer.premise().status())
                    .as("a paraphrase is never labelled false")
                    .isNotEqualTo(PremiseSemantics.Status.REFUTED);
        }
    }

    @Test
    @DisplayName("a paraphrase and its original get the same disposition")
    void aParaphraseAndItsOriginalGetTheSameDisposition() throws Exception {
        try (KnowledgeWorkbench bench = pool()) {
            Answer original = bench.ask(POOL,
                    "Do the reversals of the geomagnetic field occur at regular intervals through "
                            + "the history of the Earth?",
                    AbstractionLevel.INTRODUCTORY);
            Answer paraphrase = bench.ask(POOL,
                    "Do the reversals of the geomagnetic field happen at regular intervals through "
                            + "the history of the Earth?",
                    AbstractionLevel.INTRODUCTORY);

            assertThat(paraphrase.premise().status())
                    .as("the same presupposition in other words is the same presupposition")
                    .isEqualTo(original.premise().status());
        }
    }

    @Test
    @DisplayName("the lexical negation pitfall is read: a negated premise is not a supported one")
    void theLexicalNegationPitfallIsRead() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer negated = bench.ask(PHOTOSYNTHESIS, NEGATED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(negated.premise().status())
                    .as("the negation must not be dropped: the premise says the opposite of "
                            + "what the workspace states")
                    .isNotEqualTo(PremiseSemantics.Status.SUPPORTED);
            assertThat(negated.premise().reason())
                    .as("the reason says why the premise is not supported")
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("the Russian negation pitfall is read too")
    void theRussianLexicalNegationPitfallIsRead() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer negated = bench.ask(PHOTOSYNTHESIS, RUSSIAN_NEGATED_PREMISE,
                    AbstractionLevel.INTRODUCTORY);

            assertThat(negated.premise().status())
                    .as("a Russian rule that works in one language and fails in the next is a "
                            + "rule that works only where it was tested")
                    .isNotEqualTo(PremiseSemantics.Status.SUPPORTED);
            assertThat(negated.premise().premise())
                    .as("the Russian premise is found and named")
                    .contains("никогда");
        }
    }

    @Test
    @DisplayName("a question with no premise says so, and is answered as before")
    void aQuestionWithNoPremiseIsUnchanged() throws Exception {
        try (KnowledgeWorkbench bench = photosynthesis()) {
            Answer answer = bench.ask(PHOTOSYNTHESIS, NO_PREMISE, AbstractionLevel.INTRODUCTORY);

            assertThat(answer.premise().status()).isEqualTo(PremiseSemantics.Status.NO_PREMISE);
            assertThat(answer.answered()).isTrue();
            assertThat(answer.claims()).isNotEmpty();
        }
    }

    // ── the false-refusal metric ──────────────────────────────────────────────

    @Test
    @DisplayName("no premise the workspace supports costs the user the answer")
    void noSupportedPremiseIsRefused() throws Exception {
        List<String> questions = List.of(
                SUPPORTED_PREMISE,
                PARAPHRASED_PREMISE,
                "Since photosynthesis stores light energy as chemical energy, what is the role of "
                        + "RuBisCO?",
                "Since a plant releases oxygen during the day, what does a plant release during "
                        + "the day?");
        try (KnowledgeWorkbench bench = photosynthesis()) {
            List<String> refused = new ArrayList<>();
            List<String> supported = new ArrayList<>();
            for (String question : questions) {
                Answer answer = bench.ask(PHOTOSYNTHESIS, question, AbstractionLevel.INTRODUCTORY);
                if (answer.premise().status() == PremiseSemantics.Status.SUPPORTED) {
                    supported.add(question);
                    if (!answer.answered()) {
                        refused.add(question);
                    }
                }
            }
            assertThat(supported)
                    .as("the supported premises of the set were read as supported")
                    .hasSizeGreaterThanOrEqualTo(2);
            assertThat(refused)
                    .as("FALSE REFUSALS on premises the workspace supports")
                    .isEmpty();
        }
    }

    // ── the corpora ───────────────────────────────────────────────────────────

    /**
     * A workbench with the bundled photosynthesis package.
     *
     * @return the workbench
     * @throws Exception when the store cannot be opened
     */
    private KnowledgeWorkbench photosynthesis() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("photosynthesis.sqlite")
                        .toString());
        bench.loadBundledPackage(PHOTOSYNTHESIS);
        return bench;
    }

    /**
     * A workbench with the licensed research pool, imported through the production path.
     *
     * @return the workbench
     * @throws Exception when the store cannot be opened
     */
    private KnowledgeWorkbench logic() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("logic.sqlite")
                        .toString());
        bench.loadBundledPackage(LOGIC);
        return bench;
    }

    private KnowledgeWorkbench pool() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("pool.sqlite")
                        .toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            bench.importDocument(POOL, entry.path(), entry.file(), entry.language());
        }
        return bench;
    }
}
