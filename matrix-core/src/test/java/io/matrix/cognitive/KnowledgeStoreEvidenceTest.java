package io.matrix.cognitive;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.ClaimWarrant;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — chain A's own evidence, read from chain A's own knowledge.
 *
 * <p><b>What this test is about.</b> The adjudicator asks whether chain A's own records
 * support or contradict a proposition. Until now the only implementation of that answer
 * was "nothing", so the adjudicator could never support a proposition and never reject
 * one — every remote report came back unresolved for want of a reading rather than for
 * want of evidence. This test holds the reading: a citable claim with supporting
 * evidence supports; a candidate does not; a claim whose evidence contradicts
 * contradicts.</p>
 */
class KnowledgeStoreEvidenceTest {

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    /** The workspace the fixtures live in. */
    private static final String WORKSPACE = "bridged";

    @TempDir
    Path directory;

    @Test
    @DisplayName("a citable claim with supporting evidence supports the proposition")
    void aCitableClaimWithSupportingEvidenceSupportsTheProposition() {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.applyChanges(WORKSPACE, changes("clm-1", "src-1", "evd-1",
                    "the lamp is lit", ClaimStatus.SUPPORTED, EvidenceRelation.SUPPORTS));

            KnowledgeStoreEvidence evidence = new KnowledgeStoreEvidence(store, WORKSPACE);

            assertEquals(1, evidence.supporting("the lamp is lit"));
            assertEquals(0, evidence.contradicting("the lamp is lit"));
            assertEquals(0, evidence.supporting("the lamp is dark"),
                    "a claim about another proposition supports nothing");
        }
    }

    @Test
    @DisplayName("a candidate supports nothing: the rule that keeps a candidate from "
            + "answering as an established fact applies to chain A's own records too")
    void aCandidateSupportsNothing() {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.applyChanges(WORKSPACE, changes("clm-2", "src-2", "evd-2",
                    "the lamp is lit", ClaimStatus.CANDIDATE, EvidenceRelation.SUPPORTS));

            KnowledgeStoreEvidence evidence = new KnowledgeStoreEvidence(store, WORKSPACE);

            assertEquals(0, evidence.supporting("the lamp is lit"),
                    "an unvalidated claim is not citable and supports nothing");
        }
    }

    @Test
    @DisplayName("a claim whose evidence contradicts the proposition contradicts it")
    void aClaimWhoseEvidenceContradictsThePropositionContradictsIt() {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.applyChanges(WORKSPACE, changes("clm-3", "src-3", "evd-3",
                    "the lamp is lit", ClaimStatus.SUPPORTED, EvidenceRelation.CONTRADICTS));

            KnowledgeStoreEvidence evidence = new KnowledgeStoreEvidence(store, WORKSPACE);

            assertEquals(1, evidence.contradicting("the lamp is lit"));
            assertEquals(0, evidence.supporting("the lamp is lit"));
        }
    }

    @Test
    @DisplayName("the propositions of the supplied records are readable")
    void thePropositionsOfTheSuppliedRecordsAreReadable() {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.applyChanges(WORKSPACE, changes("clm-4", "src-4", "evd-4",
                    "the lamp is lit", ClaimStatus.SUPPORTED, EvidenceRelation.SUPPORTS));

            KnowledgeStoreEvidence evidence = new KnowledgeStoreEvidence(store, WORKSPACE);

            assertEquals(List.of("the lamp is lit"), evidence.propositionsOf(List.of("clm-4")));
            assertTrue(evidence.propositionsOf(List.of("clm-unknown")).isEmpty(),
                    "a record this process does not hold yields no proposition");
        }
    }

    /**
     * The changes of one claim.
     *
     * @param claimId    the claim
     * @param sourceId   the source
     * @param evidenceId the evidence
     * @param proposition the proposition
     * @param status     the claim's status
     * @param relation   the evidence's relation
     * @return the changes
     */
    private static KnowledgeChanges changes(String claimId, String sourceId, String evidenceId,
                                            String proposition, ClaimStatus status,
                                            EvidenceRelation relation) {
        Source source = new Source(sourceId, WORKSPACE, SourceKind.PUBLICATION,
                "a textbook", "locator-" + sourceId, 50, 50, proposition, NOW,
                "hash-" + sourceId, null, null, "text");
        Claim claim = new Claim(claimId, WORKSPACE, "con-" + claimId, proposition, null, null,
                status, 0.8, 1, sourceId, "stated", "read", ClaimWarrant.NONE, null, NOW, NOW);
        Evidence evidence = new Evidence(evidenceId, WORKSPACE, claimId, sourceId, relation,
                proposition, 20, NOW);
        return new KnowledgeChanges(List.of(source), List.of(claim), List.of(evidence),
                List.of(), List.of());
    }
}
