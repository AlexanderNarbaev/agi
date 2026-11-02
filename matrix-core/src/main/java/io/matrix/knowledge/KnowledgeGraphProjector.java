package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The knowledge graph as a READ-ONLY PROJECTION of the workspace
 * (RECON-W42.7).
 *
 * <p>The decision D-003 of the reconnaissance: <em>the graph store becomes
 * a projection over the W41 store</em>. Until now it was not: the
 * {@link KnowledgeGraphStore} was a separate, hand-populated store, and
 * the lifecycle manager added the two entities it cared about ("lifecycle",
 * "brain") to it. The workspace's own concepts and claims were not in it
 * at all, so the graph could not answer a question about what the
 * workspace knows.
 *
 * <p><b>What the projection does.</b> It reads a workspace's snapshot and
 * projects it into the graph's own shapes:
 *
 * <ul>
 *   <li>the CONCEPTS become entities;</li>
 *   <li>the CLAIMS become entities too, so that the claim's evidence and
 *       its concept are visible;</li>
 *   <li>the relations are: a claim <em>states</em> a concept, a concept
 *       <em>requires</em> a prerequisite, a claim is <em>supported by</em>
 *       a source, and two concepts that share a claim are
 *       <em>related by</em> it.</li>
 * </ul>
 *
 * <p><b>Why it is read-only.</b> The projection holds no state of its
 * own: it is built from the snapshot and thrown away. There is no second
 * copy of the knowledge that could drift from the first — the mission's
 * §16 forbids a fourth knowledge system, and a projection is not one.
 *
 * <p><b>What it does not claim.</b> The weight of a relation is the
 * claim's own authored confidence — an authored field, not a computed
 * probability, and the support score remains explicitly uncalibrated.
 */
public final class KnowledgeGraphProjector {

    private KnowledgeGraphProjector() {
    }

    /**
     * Projects a workspace's snapshot into the graph.
     *
     * @param snapshot the workspace
     * @return the projected graph
     */
    public static KnowledgeGraphStore project(KnowledgeSnapshot snapshot) {
        KnowledgeGraphStore graph = new KnowledgeGraphStore();
        if (snapshot == null) {
            return graph;
        }

        for (Concept concept : snapshot.concepts()) {
            graph.addEntity(new KnowledgeGraphStore.Entity(concept.id(), "concept",
                    Map.of("name", concept.name(),
                            "definition", concept.definition() == null ? "" : concept.definition())));
        }
        for (Claim claim : snapshot.claims()) {
            graph.addEntity(new KnowledgeGraphStore.Entity(claim.id(), "claim",
                    Map.of("proposition", claim.proposition(),
                            "status", claim.status().name(),
                            "confidence", claim.confidence())));
        }
        for (Source source : snapshot.sources()) {
            graph.addEntity(new KnowledgeGraphStore.Entity(source.id(), "source",
                    Map.of("title", source.title() == null ? "" : source.title(),
                            "kind", source.kind().name())));
        }

        for (Claim claim : snapshot.claims()) {
            graph.addRelation(new KnowledgeGraphStore.Relation(claim.id(), claim.conceptId(),
                    "states", weightOf(claim)));
        }
        for (Evidence evidence : snapshot.evidence()) {
            graph.addRelation(new KnowledgeGraphStore.Relation(evidence.claimId(), evidence.sourceId(),
                    "supported by", 1.0));
        }
        for (Prerequisite prerequisite : snapshot.prerequisites()) {
            graph.addRelation(new KnowledgeGraphStore.Relation(prerequisite.conceptId(),
                    prerequisite.requiresConceptId(), "requires", 1.0));
        }

        // Two concepts that a claim states together are related by it. The
        // relation is the claim's own confidence, so a relation built on a
        // weak claim is a weak relation.
        Map<String, List<Claim>> claimsByConcept = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            claimsByConcept.computeIfAbsent(claim.conceptId(), key -> new ArrayList<>()).add(claim);
        }
        for (List<Claim> claims : claimsByConcept.values()) {
            for (int i = 0; i < claims.size(); i++) {
                for (int j = i + 1; j < claims.size(); j++) {
                    Claim first = claims.get(i);
                    Claim second = claims.get(j);
                    if (first.conceptId().equals(second.conceptId())) {
                        continue;
                    }
                    double weight = Math.min(weightOf(first), weightOf(second));
                    graph.addRelation(new KnowledgeGraphStore.Relation(first.conceptId(),
                            second.conceptId(), "related by", weight));
                    graph.addRelation(new KnowledgeGraphStore.Relation(second.conceptId(),
                            first.conceptId(), "related by", weight));
                }
            }
        }
        return graph;
    }

    /**
     * The relation's weight: the claim's own authored confidence.
     *
     * <p>The confidence is an authored field. It is not a probability and
     * the system does not pretend it is one.
     *
     * @param claim the claim
     * @return the weight
     */
    private static double weightOf(Claim claim) {
        double confidence = claim.confidence();
        if (confidence < 0.0) {
            return 0.0;
        }
        return Math.min(confidence, 1.0);
    }
}
