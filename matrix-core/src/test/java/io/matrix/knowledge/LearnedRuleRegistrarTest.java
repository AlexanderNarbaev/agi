package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — what the system learned about the learner becomes knowledge.
 *
 * <p>A rule that lives only in memory is a rule the user cannot read, cannot
 * check, and cannot build on. This puts it in the store, as a claim with a
 * source and evidence, subject to the same rules as everything else.
 */
class LearnedRuleRegistrarTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage("photosynthesis");
    }

    private String photosynthesisId() {
        return workbench.workspace("photosynthesis").concepts().stream()
                .filter(c -> c.name().equals("Photosynthesis"))
                .findFirst().orElseThrow().id();
    }

    private void answerEverythingCorrectly() {
        for (AssessmentItem item : workbench.dataset("photosynthesis").items()) {
            workbench.assess("photosynthesis", item.id(), String.join(" ", item.keyTerms()),
                    0.9, "session-1");
        }
    }

    @Test
    @DisplayName("the learned rule is registered as a claim with a source and evidence")
    void theLearnedRuleBecomesKnowledge() {
        answerEverythingCorrectly();

        LearnedRuleRegistrar.Registration registration =
                workbench.registerLearnedRule("photosynthesis", photosynthesisId());

        assertThat(registration.claim().status()).isEqualTo(ClaimStatus.SUPPORTED);
        assertThat(registration.claim().conceptId()).isEqualTo(photosynthesisId());
        assertThat(registration.source().kind()).isEqualTo(SourceKind.OBSERVATION);
        assertThat(registration.evidence().relation()).isEqualTo(EvidenceRelation.SUPPORTS);
        assertThat(registration.evidence().claimId()).isEqualTo(registration.claim().id());
        assertThat(registration.evidence().sourceId()).isEqualTo(registration.source().id());
        assertThat(registration.claim().introducedBySourceId()).isEqualTo(registration.source().id());
    }

    @Test
    @DisplayName("the claim says what was measured, and how much of it")
    void theClaimStatesTheMeasurement() {
        answerEverythingCorrectly();

        LearnedRuleRegistrar.Registration registration =
                workbench.registerLearnedRule("photosynthesis", photosynthesisId());

        assertThat(registration.proposition())
                .contains("held-out items")
                .contains("photosynthesis-v1")
                .contains("11 items");
        // It does not claim that the learner understands the subject.
        assertThat(registration.proposition()).doesNotContain("understands photosynthesis");
    }

    @Test
    @DisplayName("with too few held-out answers the claim says so")
    void withTooFewAnswersTheClaimSaysSo() {
        workbench.assess("photosynthesis", "t-recall-1",
                "Photosynthesis uses light, carbon dioxide and water to make sugars.", 0.8, "s1");

        LearnedRuleRegistrar.Registration registration =
                workbench.registerLearnedRule("photosynthesis", photosynthesisId());

        assertThat(registration.proposition()).contains("not yet measured");
        assertThat(registration.claim().confidence()).isLessThan(0.5);
    }

    @Test
    @DisplayName("the registered claim is in the store, with its source and evidence")
    void theRegisteredClaimIsInTheStore() {
        answerEverythingCorrectly();
        int claimsBefore = workbench.workspace("photosynthesis").claims().size();

        workbench.registerLearnedRule("photosynthesis", photosynthesisId());

        KnowledgeSnapshot after = workbench.workspace("photosynthesis");
        assertThat(after.claims()).hasSize(claimsBefore + 1);
        assertThat(after.claims()).anyMatch(c -> c.proposition().contains("held-out items"));
        assertThat(after.sources()).anyMatch(s -> s.kind() == SourceKind.OBSERVATION);
        assertThat(after.evidence()).anyMatch(e -> e.relation() == EvidenceRelation.SUPPORTS
                && after.claims().stream().anyMatch(c -> c.id().equals(e.claimId())
                && c.proposition().contains("held-out items")));
    }

    @Test
    @DisplayName("registering twice does not duplicate the claim")
    void registeringTwiceDoesNotDuplicate() {
        answerEverythingCorrectly();
        workbench.registerLearnedRule("photosynthesis", photosynthesisId());
        int claimsAfterFirst = workbench.workspace("photosynthesis").claims().size();

        workbench.registerLearnedRule("photosynthesis", photosynthesisId());

        assertThat(workbench.workspace("photosynthesis").claims()).hasSize(claimsAfterFirst);
    }

    @Test
    @DisplayName("the registered rule survives a restart")
    void theRegisteredRuleSurvivesARestart() {
        answerEverythingCorrectly();
        workbench.registerLearnedRule("photosynthesis", photosynthesisId());
        int claims = workbench.workspace("photosynthesis").claims().size();
        workbench.close();

        KnowledgeWorkbench reopened = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        try {
            assertThat(reopened.workspace("photosynthesis").claims()).hasSize(claims);
            assertThat(reopened.workspace("photosynthesis").claims())
                    .anyMatch(c -> c.proposition().contains("held-out items"));
        } finally {
            reopened.close();
        }
    }
}
