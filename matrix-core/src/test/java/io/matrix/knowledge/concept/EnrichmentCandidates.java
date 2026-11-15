package io.matrix.knowledge.concept;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C2A (Workstream B3+B5) — the three candidate enrichments, applied to a snapshot or to a
 * question.
 *
 * <p><b>This is research code, in the TEST source set.</b> The production retrieval path is not
 * modified: the candidates are applied to the SNAPSHOT before the production
 * {@link io.matrix.knowledge.KnowledgeRetriever} sees it, or to the QUESTION before it is
 * asked. The production retriever is reused as it is.</p>
 *
 * <p>The definitions are the ones frozen in
 * {@code docs-v2/engineering/C2A-CANDIDATE-FREEZE.md} §4:</p>
 *
 * <ul>
 *   <li><b>H1</b> — the concept's {@code definition} field carries the definition candidate: the
 *       verbatim sentence of the source document. A concept with no definition candidate keeps
 *       {@code null};</li>
 *   <li><b>H2</b> — the concept's {@code definition} field carries the retrieval text of the
 *       concepts related to it by one relation hop. That field is the only free-text slot the
 *       production retriever composes into a claim's searchable text
 *       ({@code KnowledgeRetriever.java:148-154}), so the text written there is a retrieval aid
 *       and NOT a definition;</li>
 *   <li><b>H3</b> — the question is expanded with the names of the concepts related to the
 *       concepts the question names. The corpus is not touched at all.</li>
 * </ul>
 *
 * <p>Every candidate is ONE HOP. There is no transitive closure anywhere in this class.</p>
 */
public final class EnrichmentCandidates {

    /** The name of the candidate that attaches the extracted definitions. */
    public static final String H1 = "H1-definition-enrichment";

    /** The name of the candidate that expands the corpus with the related concepts. */
    public static final String H2 = "H2-relation-expansion";

    /** The name of the candidate that expands the question with the related concepts. */
    public static final String H3 = "H3-query-lexical-expansion";

    /** The name of the combination of H1 and H2. */
    public static final String H1_AND_H2 = "H1+H2-definition-and-relation-expansion";

    /** An enriched snapshot, with the counts of what was changed. */
    public record EnrichedSnapshot(
            KnowledgeSnapshot snapshot,
            String candidate,
            int conceptsChanged,
            int definitionsApplied,
            int relatedTextsApplied,
            int relatedConceptNamesApplied) {
    }

    /**
     * H1 — the definition candidates become the concepts' definitions.
     *
     * @param snapshot the workspace
     * @param extraction what the source-grounded extractor found
     * @return the enriched snapshot, with the counts of what was changed
     */
    public static EnrichedSnapshot applyH1(KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction) {
        Map<String, String> definitions = new LinkedHashMap<>();
        for (SourceGroundedEnrichment.DefinitionCandidate candidate : extraction.definitions()) {
            definitions.put(candidate.conceptId(), candidate.definition());
        }
        int changed = 0;
        List<Concept> concepts = new ArrayList<>();
        for (Concept concept : snapshot.concepts()) {
            String definition = definitions.get(concept.id());
            if (definition == null) {
                concepts.add(concept);
                continue;
            }
            concepts.add(new Concept(concept.id(), concept.workspaceId(), concept.name(),
                    definition, concept.domain(), concept.createdAt()));
            changed++;
        }
        return new EnrichedSnapshot(withConcepts(snapshot, concepts), H1, changed, changed, 0, 0);
    }

    /**
     * H2 — one hop of relation expansion on the corpus side.
     *
     * <p>The claim's searchable text also carries the text of the concepts related to its own
     * concept. The expansion is symmetric: a claim about the effect reaches the vocabulary of the
     * cause and the other way round. The relation's direction is recorded on the candidate and
     * is what the relation MEANS; the expansion does not distinguish it.</p>
     *
     * @param snapshot the workspace
     * @param extraction what the source-grounded extractor found
     * @return the enriched snapshot, with the counts of what was changed
     */
    public static EnrichedSnapshot applyH2(KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction) {
        return applyRelations(snapshot, extraction, false);
    }

    /**
     * H1 + H2 — the definitions AND the one hop of relation expansion.
     *
     * @param snapshot the workspace
     * @param extraction what the source-grounded extractor found
     * @return the enriched snapshot, with the counts of what was changed
     */
    public static EnrichedSnapshot applyH1AndH2(KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction) {
        return applyRelations(snapshot, extraction, true);
    }

    private static EnrichedSnapshot applyRelations(KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction, boolean withDefinitions) {
        Map<String, String> definitions = new LinkedHashMap<>();
        if (withDefinitions) {
            for (SourceGroundedEnrichment.DefinitionCandidate candidate : extraction.definitions()) {
                definitions.put(candidate.conceptId(), candidate.definition());
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (Concept concept : snapshot.concepts()) {
            names.put(concept.id(), concept.name());
        }
        Map<String, List<String>> related = extraction.relatedByConcept();
        int changed = 0;
        int definitionsApplied = 0;
        int relatedTexts = 0;
        int relatedNames = 0;
        List<Concept> concepts = new ArrayList<>();
        for (Concept concept : snapshot.concepts()) {
            List<String> parts = new ArrayList<>();
            String own = definitions.get(concept.id());
            if (own != null) {
                parts.add(own);
                definitionsApplied++;
            }
            for (String relatedId : related.getOrDefault(concept.id(), List.of())) {
                String name = names.get(relatedId);
                if (name == null || name.isBlank()) {
                    continue;
                }
                parts.add(name);
                relatedNames++;
                String relatedDefinition = definitions.get(relatedId);
                if (relatedDefinition != null) {
                    parts.add(relatedDefinition);
                }
            }
            if (parts.isEmpty()) {
                concepts.add(concept);
                continue;
            }
            String text = String.join(" ", parts);
            concepts.add(new Concept(concept.id(), concept.workspaceId(), concept.name(), text,
                    concept.domain(), concept.createdAt()));
            changed++;
            if (parts.size() > (own == null ? 0 : 1)) {
                relatedTexts++;
            }
        }
        return new EnrichedSnapshot(withConcepts(snapshot, concepts),
                withDefinitions ? H1_AND_H2 : H2, changed, definitionsApplied, relatedTexts,
                relatedNames);
    }

    /**
     * H3 — the question is expanded with the names of the related concepts.
     *
     * <p>No corpus change and no persistent relation: the question that reaches "subduction"
     * also reaches the concepts the corpus links to "subduction". One hop, symmetric.</p>
     *
     * @param question the question as it was asked
     * @param snapshot the workspace
     * @param extraction what the source-grounded extractor found
     * @return the expanded question
     */
    public static String expandQuestion(String question, KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction) {
        List<String> terms = expansionTerms(question, snapshot, extraction);
        if (terms.isEmpty()) {
            return question;
        }
        return question.strip() + " " + String.join(" ", terms);
    }

    /**
     * The names the expansion adds to a question.
     *
     * @param question the question as it was asked
     * @param snapshot the workspace
     * @param extraction what the source-grounded extractor found
     * @return the added names, in the order they were found
     */
    public static List<String> expansionTerms(String question, KnowledgeSnapshot snapshot,
            SourceGroundedEnrichment.Extraction extraction) {
        Map<String, Concept> conceptsById = new LinkedHashMap<>();
        for (Concept concept : snapshot.concepts()) {
            conceptsById.put(concept.id(), concept);
        }
        Set<String> added = new LinkedHashSet<>();
        for (Concept concept : snapshot.concepts()) {
            if (!SourceGroundedEnrichment.holdsName(question, concept.name())) {
                continue;
            }
            for (String relatedId : extraction.relatedByConcept().getOrDefault(concept.id(),
                    List.of())) {
                Concept related = conceptsById.get(relatedId);
                if (related == null || related.name().isBlank()) {
                    continue;
                }
                added.add(related.name());
            }
        }
        return List.copyOf(added);
    }

    /** The same snapshot with other concepts. Everything else is unchanged, byte for byte. */
    private static KnowledgeSnapshot withConcepts(KnowledgeSnapshot snapshot,
            List<Concept> concepts) {
        return new KnowledgeSnapshot(snapshot.workspaceId(), snapshot.workspaceName(), concepts,
                snapshot.sources(), snapshot.claims(), snapshot.evidence(),
                snapshot.representations(), snapshot.prerequisites(), snapshot.revisions(),
                snapshot.derivations(), snapshot.contradictions(), snapshot.goals(),
                snapshot.exportedAt());
    }

    /** The claims of a snapshot that carry a concept, for the report. */
    public static int claimsOf(KnowledgeSnapshot snapshot, String conceptId) {
        int count = 0;
        for (Claim claim : snapshot.claims()) {
            if (claim.conceptId().equals(conceptId)) {
                count++;
            }
        }
        return count;
    }

    private EnrichmentCandidates() {
    }
}
