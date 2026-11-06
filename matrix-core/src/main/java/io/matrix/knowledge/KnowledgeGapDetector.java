package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds what the workspace does not know (owner directive §19 Workflow A,
 * §20 KnowledgeGapDetector).
 *
 * <p>A gap is not an empty answer — it is a specific, nameable absence. This
 * detector produces three kinds:
 *
 * <ul>
 *   <li>the terms of a question that no claim covers, so a user can be told
 *       which part of their question the system could not answer;</li>
 *   <li>the concepts that are represented but carry no established claim,
 *       which are the ones whose representation is an assertion without
 *       evidence;</li>
 *   <li>the prerequisites a concept declares that the workspace does not
 *       contain, which are the dependencies a learner is missing.</li>
 * </ul>
 */
public final class KnowledgeGapDetector {

    private final KnowledgeSnapshot snapshot;
    private final KnowledgeRetriever retriever;

    public KnowledgeGapDetector(KnowledgeSnapshot snapshot) {
        this.snapshot = snapshot;
        this.retriever = new KnowledgeRetriever(snapshot);
    }

    /** The workspace this detector examines. */
    public KnowledgeSnapshot snapshot() {
        return snapshot;
    }

    /** The terms of a question that nothing in the workspace covers. */
    public List<String> uncoveredTerms(String question) {
        return List.copyOf(retriever.unmatchedTerms(question));
    }

    /** The concepts that have a representation but no established claim. */
    public List<Concept> conceptsWithoutEstablishedClaims() {
        Set<String> established = new LinkedHashSet<>();
        for (Claim claim : snapshot.claims()) {
            if (claim.status() == ClaimStatus.SUPPORTED
                    || claim.status() == ClaimStatus.VERIFIED
                    || claim.status() == ClaimStatus.REFUTED
                    || claim.status() == ClaimStatus.CONTESTED) {
                established.add(claim.conceptId());
            }
        }
        Set<String> represented = new LinkedHashSet<>();
        for (Representation representation : snapshot.representations()) {
            represented.add(representation.conceptId());
        }
        List<Concept> gaps = new ArrayList<>();
        for (Concept concept : snapshot.concepts()) {
            if (represented.contains(concept.id()) && !established.contains(concept.id())) {
                gaps.add(concept);
            }
        }
        return gaps;
    }

    /** The prerequisites a concept declares that the workspace does not contain. */
    public List<String> missingPrerequisites(String conceptId) {
        Set<String> known = new LinkedHashSet<>();
        for (Concept concept : snapshot.concepts()) {
            known.add(concept.id());
        }
        List<String> missing = new ArrayList<>();
        for (Prerequisite prerequisite : snapshot.prerequisites()) {
            if (prerequisite.conceptId().equals(conceptId) && !known.contains(prerequisite.requiresConceptId())) {
                missing.add(prerequisite.requiresConceptId());
            }
        }
        return missing;
    }
}
