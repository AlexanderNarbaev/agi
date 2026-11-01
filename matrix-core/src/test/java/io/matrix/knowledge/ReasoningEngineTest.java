package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W41 — the reasoning path records what it did, and refuses to say
 * what it does not know.
 *
 * <p>The mission's §6.1 names the pipeline; §9.3 requires the answer to
 * expose the claims, the sources, the inference steps and the limitations.
 * These tests hold the engine to both.
 */
class ReasoningEngineTest {

    private static final String WS = "logic";

    /** A workspace with two facts and one rule that concludes a third. */
    private KnowledgeSnapshot logicWorkspace() {
        Concept p = new Concept("c-p", WS, "Proposition p", "A proposition that may be true.",
                "logic", "2026-11-01T00:00:00Z");
        Concept q = new Concept("c-q", WS, "Proposition q", "Another proposition.",
                "logic", "2026-11-01T00:00:00Z");
        Concept r = new Concept("c-r", WS, "Proposition r", "The conclusion.",
                "logic", "2026-11-01T00:00:00Z");

        Source source = new Source("s-1", WS, SourceKind.AUTHORITATIVE_REFERENCE,
                "A reference", "urn:isbn:978-0-00", 90, 90,
                "If p is true and p implies q, then q is true.", "2026-11-01T00:00:00Z",
                "hash-1", "2020");

        Claim pClaim = new Claim("cl-p", WS, "c-p", "Proposition p is true.", null, null,
                ClaimStatus.VERIFIED, 0.9, 1, "s-1", "transcribed", "checked",
                ClaimWarrant.TESTIMONY, DerivationStatus.NOT_DERIVED,
                "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
        Claim pToQ = new Claim("cl-ptoq", WS, "c-p", "Proposition p implies proposition q.", null,
                null, ClaimStatus.VERIFIED, 0.9, 1, "s-1", "transcribed", "checked",
                ClaimWarrant.TESTIMONY, DerivationStatus.NOT_DERIVED,
                "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
        Claim qClaim = new Claim("cl-q", WS, "c-q", "Proposition q is true.", null, null,
                ClaimStatus.SUPPORTED, 0.8, 1, "s-1", "transcribed", "checked",
                ClaimWarrant.TESTIMONY, DerivationStatus.NOT_DERIVED,
                "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");

        Derivation modusPonens = new Derivation("der-mp", WS, "rule-mp",
                "modus ponens", Derivation.Connective.AND, "cl-q",
                List.of("cl-p", "cl-ptoq"), DerivationStatus.UNDECIDED,
                "2026-11-01T00:00:00Z");

        return new KnowledgeSnapshot(WS, "Logic", List.of(p, q, r), List.of(source),
                List.of(pClaim, pToQ, qClaim),
                List.of(new Evidence("e-1", WS, "cl-p", "s-1", EvidenceRelation.SUPPORTS,
                        "p is true", 90, "2026-11-01T00:00:00Z")),
                List.of(), List.of(), List.of(), List.of(modusPonens), "2026-11-01T00:00:00Z");
    }

    @Test
    @DisplayName("the pipeline records every stage it went through")
    void thePipelineRecordsEveryStage() {
        ReasoningEngine.Result result =
                new ReasoningEngine().reason(logicWorkspace(), "What is proposition p?",
                        AbstractionLevel.INTRODUCTORY);

        assertThat(result.steps()).extracting(ReasoningEngine.Step::stage)
                .contains("intent", "classification", "retrieval", "evidence selection",
                        "inference", "verification", "answer", "provenance");
    }

    @Test
    @DisplayName("a question the workspace cannot cover is abstained from, not guessed")
    void aQuestionTheWorkspaceCannotCoverIsAbstainedFrom() {
        ReasoningEngine.Result result =
                new ReasoningEngine().reason(logicWorkspace(), "What is the boiling point of mercury?",
                        AbstractionLevel.INTRODUCTORY);

        assertThat(result.answered()).isFalse();
        assertThat(result.answer()).isEmpty();
        assertThat(result.abstentionReason()).isNotBlank();
        assertThat(result.steps()).extracting(ReasoningEngine.Step::stage).contains("answer");
    }

    @Test
    @DisplayName("a valid derivation is reported as a derivation")
    void aValidDerivationIsReportedAsADerivation() {
        ReasoningEngine.Result result =
                new ReasoningEngine().reason(logicWorkspace(), "What is proposition p?",
                        AbstractionLevel.INTRODUCTORY);

        assertThat(result.steps()).anyMatch(step -> step.stage().equals("inference")
                && step.detail().contains("modus ponens=DERIVED_VALID"));
    }

    @Test
    @DisplayName("the answer names the claims and the sources it rests on")
    void theAnswerNamesItsClaimsAndSources() {
        ReasoningEngine.Result result =
                new ReasoningEngine().reason(logicWorkspace(), "What is proposition p?",
                        AbstractionLevel.INTRODUCTORY);

        assertThat(result.answered()).isTrue();
        assertThat(result.claims()).isNotEmpty();
        assertThat(result.sources()).isNotEmpty();
        assertThat(result.sources().get(0).locator()).isEqualTo("urn:isbn:978-0-00");
    }

    @Test
    @DisplayName("a contradiction is reported and neither side is preferred")
    void aContradictionIsReportedAndNeitherSideIsPreferred() {
        KnowledgeSnapshot base = logicWorkspace();
        Claim positive = new Claim("cl-positive", WS, "c-q", "Proposition q is true.", null, null,
                ClaimStatus.VERIFIED, 0.9, 1, "s-1", "transcribed", "checked",
                ClaimWarrant.TESTIMONY, DerivationStatus.NOT_DERIVED,
                "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
        Claim negative = new Claim("cl-negative", WS, "c-q", "Proposition q is not true.", null, null,
                ClaimStatus.VERIFIED, 0.9, 1, "s-1", "transcribed", "checked",
                ClaimWarrant.TESTIMONY, DerivationStatus.NOT_DERIVED,
                "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");

        List<Claim> claims = new java.util.ArrayList<>(base.claims());
        claims.add(positive);
        claims.add(negative);
        KnowledgeSnapshot withConflict = new KnowledgeSnapshot(WS, "Logic", base.concepts(),
                base.sources(), claims, base.evidence(), base.representations(),
                base.prerequisites(), base.revisions(), base.derivations(), base.exportedAt());

        List<Contradiction> conflicts = new ContradictionDetector().detect(withConflict);

        // The conflict between the two opposite claims is found and named.
        assertThat(conflicts).anySatisfy(conflict -> {
            assertThat(java.util.Set.of(conflict.claimAId(), conflict.claimBId()))
                    .containsExactlyInAnyOrder("cl-positive", "cl-negative");
            assertThat(conflict.kind()).isEqualTo(Contradiction.Kind.OPPOSITE_POLARITY);
        });
        // And it changes neither side.
        assertThat(withConflict.claims()).filteredOn(claim ->
                claim.id().equals("cl-positive") || claim.id().equals("cl-negative"))
                .allSatisfy(claim -> assertThat(claim.status()).isEqualTo(ClaimStatus.VERIFIED));
    }
}
