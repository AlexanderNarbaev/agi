package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the knowledge travels to another learner; the history does not.
 *
 * <p>§17: "transferring validated knowledge to another learner". The store's
 * export and import are a backup and a restore — they put the same workspace
 * back with the same identifiers. A transfer gives the knowledge to a
 * DIFFERENT learner, and the identifiers have to change, because a claim's
 * identifier is derived from the workspace that holds it.
 */
class KnowledgeTransferTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage("photosynthesis");
    }

    @Test
    @DisplayName("the receiving workspace holds the knowledge")
    void theReceivingWorkspaceHoldsTheKnowledge() {
        KnowledgeTransfer.TransferResult result =
                workbench.transferKnowledge("photosynthesis", "learner-b");

        assertThat(result.fromWorkspace()).isEqualTo("photosynthesis");
        assertThat(result.toWorkspace()).isEqualTo("learner-b");
        assertThat(result.concepts()).isEqualTo(8);
        assertThat(result.claims()).isEqualTo(17);
        assertThat(result.sources()).isEqualTo(4);
        assertThat(result.evidenceLinks()).isEqualTo(18);

        KnowledgeSnapshot received = workbench.workspace("learner-b");
        assertThat(received.concepts()).hasSize(8);
        assertThat(received.claims()).hasSize(17);
        assertThat(received.sources()).hasSize(4);
    }

    @Test
    @DisplayName("the identifiers are regenerated for the receiving workspace")
    void theIdentifiersAreRegenerated() {
        KnowledgeSnapshot before = workbench.workspace("photosynthesis");
        workbench.transferKnowledge("photosynthesis", "learner-b");
        KnowledgeSnapshot after = workbench.workspace("learner-b");

        assertThat(after.concepts().stream().map(Concept::id).toList())
                .doesNotContainAnyElementsOf(before.concepts().stream().map(Concept::id).toList());
        assertThat(after.claims().stream().map(Claim::id).toList())
                .doesNotContainAnyElementsOf(before.claims().stream().map(Claim::id).toList());
        // Every claim still points at a concept that exists in ITS workspace.
        for (Claim claim : after.claims()) {
            assertThat(after.concepts()).anyMatch(c -> c.id().equals(claim.conceptId()));
        }
        for (Evidence evidence : after.evidence()) {
            assertThat(after.claims()).anyMatch(c -> c.id().equals(evidence.claimId()));
            assertThat(after.sources()).anyMatch(s -> s.id().equals(evidence.sourceId()));
        }
    }

    @Test
    @DisplayName("the receiving learner can answer what the other one could")
    void theReceivingLearnerCanAnswer() {
        workbench.transferKnowledge("photosynthesis", "learner-b");

        Answer answer = workbench.ask("learner-b", "What is photosynthesis?",
                AbstractionLevel.INTRODUCTORY);

        assertThat(answer.answered()).isTrue();
        assertThat(answer.sources()).isNotEmpty();
        assertThat(answer.supportScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("the provenance travels with the knowledge")
    void theProvenanceTravelsWithTheKnowledge() {
        workbench.transferKnowledge("photosynthesis", "learner-b");

        KnowledgeSnapshot received = workbench.workspace("learner-b");
        Source source = received.sources().stream()
                .filter(s -> s.locator().contains("10.12703"))
                .findFirst().orElseThrow();

        assertThat(source.excerpt()).isNotBlank();
        assertThat(source.authority()).isGreaterThan(0);
        assertThat(source.freshness()).isGreaterThan(0);
    }

    @Test
    @DisplayName("the learner's history stays behind")
    void theHistoryStaysBehind() {
        workbench.assess("photosynthesis", "t-recall-1",
                "Photosynthesis uses light, carbon dioxide and water to make sugars.", 0.8, "s1");
        workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
        assertThat(workbench.outcomes("photosynthesis")).isNotEmpty();

        workbench.transferKnowledge("photosynthesis", "learner-b");

        // The knowledge travelled; the evidence that a learner demonstrated
        // it did not. A learner who has just received the knowledge has not
        // been assessed on it.
        assertThat(workbench.outcomes("learner-b")).isEmpty();
        assertThat(workbench.askedQuestions("learner-b")).isEmpty();
        assertThat(workbench.learningStates("learner-b")).isEmpty();
        assertThat(workbench.outcomes("photosynthesis")).isNotEmpty();
    }

    @Test
    @DisplayName("a transfer to the same workspace is refused")
    void aTransferToTheSameWorkspaceIsRefused() {
        assertThatThrownBy(() -> workbench.transferKnowledge("photosynthesis", "photosynthesis"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two workspaces");
    }

    @Test
    @DisplayName("a transfer from an empty workspace is refused")
    void aTransferFromAnEmptyWorkspaceIsRefused() {
        assertThatThrownBy(() -> workbench.transferKnowledge("nothing-here", "learner-b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no knowledge");
    }

    @Test
    @DisplayName("the transfer survives a restart")
    void theTransferSurvivesARestart() {
        workbench.transferKnowledge("photosynthesis", "learner-b");
        int claims = workbench.workspace("learner-b").claims().size();
        workbench.close();

        KnowledgeWorkbench reopened = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        try {
            assertThat(reopened.workspace("learner-b").claims()).hasSize(claims);
            assertThat(reopened.ask("learner-b", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY).answered()).isTrue();
        } finally {
            reopened.close();
        }
    }
}
