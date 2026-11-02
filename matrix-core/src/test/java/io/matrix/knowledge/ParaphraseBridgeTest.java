package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W42.7 — the workspace's own explanation is searchable.
 *
 * <p>The answer is composed from the REPRESENTATIONS, and the retriever
 * scored only the CLAIMS. So the answer could speak a vocabulary the
 * retrieval could not see.
 */
class ParaphraseBridgeTest {

    private final KnowledgeSnapshot snapshot =
            new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");

    @Test
    @DisplayName("a question in the representation's vocabulary reaches the concept")
    void aQuestionInTheRepresentationsVocabularyReachesTheConcept() {
        KnowledgeRetriever retriever = new KnowledgeRetriever(snapshot);

        // The workspace's explanation of photosynthesis says: "Photosynthesis
        // is how green plants use light energy to build sugars out of
        // carbon dioxide and water, releasing oxygen as a by-product. The
        // sugars are the plant's stored food." The question uses that
        // vocabulary and no claim's.
        Answer answer = retriever.answer("Explain how green plants make food using sunlight.");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.statement().toLowerCase()).contains("photosynthesis");
        assertThat(answer.concepts()).anySatisfy(concept ->
                assertThat(concept.name().toLowerCase()).contains("photosynthesis"));
    }

    @Test
    @DisplayName("the second paraphrase reaches the concept too")
    void theSecondParaphraseReachesTheConceptToo() {
        KnowledgeRetriever retriever = new KnowledgeRetriever(snapshot);

        Answer answer = retriever.answer("Tell me about the sugar production of a green plant.");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.statement().toLowerCase()).contains("photosynthesis");
    }

    @Test
    @DisplayName("the bridge does not turn a refusal into an answer")
    void theBridgeDoesNotTurnARefusalIntoAnAnswer() {
        KnowledgeRetriever retriever = new KnowledgeRetriever(snapshot);

        // Nothing in this workspace is about the mitochondrion's own
        // membrane chemistry, and no representation of the workspace says
        // so.
        Answer answer = retriever.answer("Describe the membrane chemistry of the mitochondrial "
                + "cristae in detail.");

        assertThat(answer.answered()).isFalse();
        assertThat(answer.abstentionReason()).contains("No claim in this workspace");
    }

    @Test
    @DisplayName("the bridge adds the concept's evidence, not an unrelated claim's")
    void theBridgeAddsTheConceptsEvidenceNotAnUnrelatedClaims() {
        KnowledgeRetriever retriever = new KnowledgeRetriever(snapshot);

        Answer answer = retriever.answer("Explain how green plants make food using sunlight.");

        // The evidence the bridge adds is the concept's own evidence, and
        // it keeps its statuses: a refuted claim of the concept is still
        // REFUTED, so it can be shown as a mistake to correct rather than
        // as a fact.
        assertThat(answer.claims()).isNotEmpty();
        for (Claim claim : answer.claims()) {
            Claim inTheWorkspace = snapshot.claims().stream()
                    .filter(c -> c.id().equals(claim.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(claim.status()).isEqualTo(inTheWorkspace.status());
        }
        // And the concept's own claim about the oxygen is among them.
        assertThat(answer.claims()).anySatisfy(claim ->
                assertThat(claim.proposition()).contains("releases oxygen as a byproduct"));
    }
}
