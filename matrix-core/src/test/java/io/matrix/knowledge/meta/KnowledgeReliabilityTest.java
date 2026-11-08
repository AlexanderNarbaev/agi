package io.matrix.knowledge.meta;

import io.matrix.contract.Evaluation;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeRevision;
import io.matrix.knowledge.RevisionKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §4 — the meta-memory of the system's own knowledge.
 *
 * <p>These tests hold the measure to what it says it is: a plain count of how many of the
 * claims a source or a validation method produced were revised afterwards, with the reasons
 * carried along, and nothing more.</p>
 */
class KnowledgeReliabilityTest {

    private static final String AT = "2026-11-08T00:00:00Z";

    private static Claim claim(String id, String source, String method, ClaimStatus status) {
        return new Claim(id, "ws", "cpt-" + id, "the proposition " + id, "scope", "context",
                status, 0.8, 1, source, "the extractor", method, null, null, AT, AT);
    }

    private static KnowledgeRevision revision(String id, String targetId, String summary) {
        return new KnowledgeRevision(id, "ws", "claim", targetId, RevisionKind.CORRECTION,
                summary, List.of(), List.of(), List.of(), List.of(), List.of(), AT);
    }

    @Test
    @DisplayName("a source that produced no revisions is fully reliable")
    void aCleanSourceIsFullyReliable() {
        KnowledgeReliability reliability = KnowledgeReliability.of(
                List.of(claim("c1", "src-good", "the validator", ClaimStatus.SUPPORTED),
                        claim("c2", "src-good", "the validator", ClaimStatus.SUPPORTED)),
                List.of());

        KnowledgeReliability.SourceReliability source = reliability.sources().get("src-good");
        assertEquals(2, source.claimsIntroduced());
        assertEquals(0, source.claimsRevised());
        assertEquals(0.0, source.revisionRate());
        assertTrue(source.reasons().isEmpty());
        assertEquals(0.0, reliability.methods().get("the validator").revisionRate());
    }

    @Test
    @DisplayName("a source whose claims were revised carries the reasons with the number")
    void aRevisedSourceCarriesItsReasons() {
        KnowledgeReliability reliability = KnowledgeReliability.of(
                List.of(claim("c1", "src-bad", "the validator", ClaimStatus.SUPPORTED),
                        claim("c2", "src-bad", "the validator", ClaimStatus.SUPPORTED),
                        claim("c3", "src-bad", "the validator", ClaimStatus.SUPPORTED)),
                List.of(revision("r1", "c1", "the document said otherwise")));

        KnowledgeReliability.SourceReliability source = reliability.sources().get("src-bad");
        assertEquals(3, source.claimsIntroduced());
        assertEquals(1, source.claimsRevised());
        assertEquals(1.0 / 3.0, source.revisionRate(), 0.0001);
        assertTrue(source.reasons().get(0).contains("the document said otherwise"));
        assertEquals(1, reliability.methods().get("the validator").claimsRevised());
    }

    @Test
    @DisplayName("a claim that was superseded afterwards counts as revised")
    void aSupersededClaimCountsAsRevised() {
        KnowledgeReliability reliability = KnowledgeReliability.of(
                List.of(claim("c1", "src", "the validator", ClaimStatus.SUPERSEDED),
                        claim("c2", "src", "the validator", ClaimStatus.SUPPORTED)),
                List.of());

        KnowledgeReliability.SourceReliability source = reliability.sources().get("src");
        assertEquals(1, source.claimsRevised());
        assertTrue(source.reasons().get(0).contains("SUPERSEDED"),
                "the reason must say what happened: " + source.reasons());
    }

    @Test
    @DisplayName("the evaluation reports how much of the workspace is established")
    void theEvaluationReportsWhatIsEstablished() {
        KnowledgeReliability reliability = KnowledgeReliability.of(
                List.of(claim("c1", "src", "the validator", ClaimStatus.SUPPORTED),
                        claim("c2", "src", "the validator", ClaimStatus.VERIFIED),
                        claim("c3", "src", "the validator", ClaimStatus.CANDIDATE)),
                List.of());

        Evaluation evaluation = reliability.evaluation("ws", AT);

        assertEquals("meta-ws", evaluation.id());
        assertEquals(2.0, evaluation.metrics().get("claims.established").value());
        assertEquals(3, evaluation.metrics().get("claims.established").sampleSize());
        assertEquals(1.0, evaluation.metrics().get("sources.known").value());
    }
}
