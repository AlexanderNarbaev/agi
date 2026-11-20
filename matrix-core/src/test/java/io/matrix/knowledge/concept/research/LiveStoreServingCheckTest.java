package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The owner's live store, after the migration: the SERVING path still answers.
 *
 * <p>This is the acceptance check of the owner's decision D1-LIVE-STORE-MIGRATION-019:
 * the migrated store must not merely be internally consistent (the orphan audit says
 * that) — the production asking path must still answer from it.</p>
 *
 * <p><b>What it pins.</b> The two answers the report publishes. The report quotes the
 * {@code logic} answer — "Propositional logic evaluates formulas by assigning truth
 * values to their atomic variables…" — and the {@code photosynthesis} answer —
 * "Photosynthesis has two linked stages. The light-dependent reactions, in the thylakoid
 * membranes, harvest light…". The question is asserted as well: it is the workspace's own
 * first concept ("What is …"), so the assertion pins <b>which</b> question was asked and
 * not only that something was answered.</p>
 *
 * <p><b>Why a substring and not the whole statement.</b> The statement the asking path
 * builds is the workspace's own representations <b>concatenated</b>: it grows and shrinks
 * with every representation the store holds, and it ends with paragraphs that answer
 * questions nobody asked. An equality on the whole statement would therefore break on any
 * unrelated edit of the store, and it would assert nothing about the published claim. The
 * substring asserted here is the workspace's <b>opening</b> — the answer to the question
 * that was asked, in the workspace's own words — and that is the sentence the report
 * quotes and the sentence that must not change: it is the claim that the migration did
 * not change what the store says. The opening is asserted for both workspaces.</p>
 *
 * <p>The test is <b>skipped, not failed</b>, when the store is not in the checkout (CI).</p>
 */
@DisplayName("P1 — the owner's live store after the migration: the asking path still answers")
class LiveStoreServingCheckTest {

    private static final Path LIVE = Path.of("data/knowledge/workbench.sqlite");

    /**
     * The questions the two workspaces are asked.
     *
     * <p>Each question is built from the workspace's first concept — the store's concepts
     * are ordered by name, so the first one is the alphabetically first — and the names
     * are the store's own.</p>
     */
    private static final String LOGIC_QUESTION = "What is A truth table assigns every combination "
            + "of truth values to the propositional variables of a formula and computes the value "
            + "of the formula on each combination.?";

    private static final String PHOTOSYNTHESIS_QUESTION = "What is A photosynthetic organism "
            + "obtains all of its energy from photosynthesis and never needs cellular respiration.?";

    /**
     * The openings of the two answers the report publishes.
     *
     * <p>These are the store's own words — the representation the workspace answers
     * with. The report's {@code photosynthesis} quote is verbatim; its {@code logic} quote
     * carries an extra "it is" ("…and <b>it is</b> a consequence of another when…") that the
     * store's representation does not have, so the assertion pins the store's wording and
     * not the report's paraphrase.</p>
     */
    private static final String LOGIC_ANSWER = "Propositional logic evaluates formulas by "
            + "assigning truth values to their atomic variables. A formula is valid when it is true "
            + "on every assignment, satisfiable when it is true on at least one, and a consequence "
            + "of another when it is true wherever the other is.";

    private static final String PHOTOSYNTHESIS_ANSWER = "Photosynthesis has two linked stages. "
            + "The light-dependent reactions, in the thylakoid membranes, harvest light, split "
            + "water and build a proton gradient that drives ATP and NADPH production. The "
            + "light-independent reactions, in the chloroplast stroma, use that ATP and NADPH to "
            + "fix carbon dioxide into carbohydrate through the Calvin-Benson cycle.";

    @Test
    @DisplayName("both workspaces: the concepts, the claims and the answers")
    void theMigratedStoreAnswers() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(path.toString())) {
            System.out.println("[live-serving] the workspaces: " + workbench.workspaces());
            for (String workspace : List.of("logic", "photosynthesis")) {
                var snapshot = workbench.workspace(workspace);
                System.out.printf("[live-serving] the workspace %s: the concepts %d, the claims %d, "
                        + "the representations %d%n", workspace, snapshot.concepts().size(),
                        snapshot.claims().size(), snapshot.representations().size());
                String question = questionFor(snapshot);
                Answer answer = workbench.ask(workspace, question, AbstractionLevel.INTERMEDIATE);
                System.out.printf("[live-serving] the workspace %s: the question — %s%n", workspace,
                        question);
                System.out.printf("[live-serving] the workspace %s: the answer — the statement %s | "
                        + "the answered %s | the support %s | the limitations %s | the gaps %s%n",
                        workspace, answer.statement(), answer.answered(),
                        answer.supportScore(), answer.limitations(), answer.gaps());
                assertThat(answer.statement()).as("the answer of %s", workspace).isNotBlank();
                assertThat(answer.answered()).as("the answered flag of %s", workspace).isTrue();
            }
        }
    }

    @Test
    @DisplayName("the two published answers: the question asked and the answer given")
    void theTwoPublishedAnswers() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(path.toString())) {
            // ── the logic workspace ────────────────────────────────────────────
            var logic = workbench.workspace("logic");
            assertThat(logic.concepts().size()).as("the logic workspace's concepts").isEqualTo(30);
            assertThat(logic.claims().size()).as("the logic workspace's claims").isEqualTo(28);
            assertThat(logic.representations().size()).as("the logic workspace's representations")
                    .isEqualTo(8);
            String logicQuestion = questionFor(logic);
            assertThat(logicQuestion).as("the question the logic workspace is asked")
                    .isEqualTo(LOGIC_QUESTION);
            Answer logicAnswer = workbench.ask("logic", logicQuestion,
                    AbstractionLevel.INTERMEDIATE);
            assertThat(logicAnswer.statement()).as("the logic answer's statement")
                    .isNotBlank()
                    .contains(LOGIC_ANSWER);

            // ── the photosynthesis workspace ───────────────────────────────────
            var photosynthesis = workbench.workspace("photosynthesis");
            assertThat(photosynthesis.concepts().size()).as("the photosynthesis concepts")
                    .isEqualTo(37);
            assertThat(photosynthesis.claims().size()).as("the photosynthesis claims")
                    .isEqualTo(24);
            assertThat(photosynthesis.representations().size())
                    .as("the photosynthesis representations").isEqualTo(19);
            String photosynthesisQuestion = questionFor(photosynthesis);
            assertThat(photosynthesisQuestion).as("the question the photosynthesis workspace is asked")
                    .isEqualTo(PHOTOSYNTHESIS_QUESTION);
            Answer photosynthesisAnswer = workbench.ask("photosynthesis", photosynthesisQuestion,
                    AbstractionLevel.INTERMEDIATE);
            assertThat(photosynthesisAnswer.statement()).as("the photosynthesis answer's statement")
                    .isNotBlank()
                    .contains(PHOTOSYNTHESIS_ANSWER);
        }
    }

    /** A question about the workspace's first concept. */
    private static String questionFor(io.matrix.knowledge.KnowledgeSnapshot snapshot) {
        String name = snapshot.concepts().stream().findFirst()
                .map(concept -> concept.name()).orElse("the first concept");
        return "What is " + name + "?";
    }
}
