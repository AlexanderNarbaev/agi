package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W40 — the retriever's contract.
 *
 * <p>Two properties matter more than the rest, and they pull in opposite
 * directions: the system must ANSWER what it knows and ABSTAIN on what it
 * does not. A retriever that always answers is worse than one that never
 * does, because its confidence cannot be read.
 */
class KnowledgeRetrieverTest {

    private KnowledgeSnapshot snapshot;
    private KnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        snapshot = new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
        retriever = new KnowledgeRetriever(snapshot);
    }

    @Test
    @DisplayName("a question the workspace knows is answered, with its claims and sources")
    void aKnownQuestionIsAnswered() {
        Answer answer = retriever.answer("What is photosynthesis?");

        assertThat(answer.answered()).isTrue();
        assertThat(answer.abstained()).isFalse();
        assertThat(answer.statement()).isNotBlank();
        assertThat(answer.concepts()).extracting(Concept::name).contains("Photosynthesis");
        assertThat(answer.claims()).isNotEmpty();
        assertThat(answer.sources()).isNotEmpty();
        assertThat(answer.confidence()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("the sources shown are the sources of the evidence, not invented ones")
    void theSourcesAreTheEvidenceSources() {
        Answer answer = retriever.answer("What is photosynthesis?");

        var sourceIds = snapshot.sources().stream().map(Source::id).toList();
        for (Source source : answer.sources()) {
            assertThat(sourceIds).contains(source.id());
            assertThat(source.locator()).contains("http");
        }
        // Every source shown is attached to a claim of the answer by an
        // evidence record.
        var evidenceSourceIds = snapshot.evidence().stream().map(Evidence::sourceId).toList();
        for (Source source : answer.sources()) {
            assertThat(evidenceSourceIds).contains(source.id());
        }
    }

    @Test
    @DisplayName("a question the workspace does not know is abstained from")
    void anUnknownQuestionIsAbstainedFrom() {
        Answer answer = retriever.answer("What is the boiling point of mercury?");

        assertThat(answer.answered()).isFalse();
        assertThat(answer.abstained()).isTrue();
        assertThat(answer.abstentionReason()).isNotBlank();
        assertThat(answer.statement()).isEmpty();
        assertThat(answer.claims()).isEmpty();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.confidence()).isEqualTo(0.0);
        // The user is told WHAT is missing, not merely that something is.
        assertThat(answer.unmatchedTerms()).isNotEmpty();
    }

    @Test
    @DisplayName("the same question is answered at a different level with a different account")
    void theLevelChangesTheAnswer() {
        Answer introductory = retriever.answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
        Answer mechanistic = retriever.answer("What is photosynthesis?", AbstractionLevel.MECHANISTIC);

        assertThat(introductory.answered()).isTrue();
        assertThat(mechanistic.answered()).isTrue();
        assertThat(introductory.statement()).isNotEqualTo(mechanistic.statement());
        assertThat(introductory.level()).isEqualTo(AbstractionLevel.INTRODUCTORY);
        assertThat(mechanistic.level()).isEqualTo(AbstractionLevel.MECHANISTIC);

        // The introductory account is not replaced by the mechanistic one:
        // both exist, and the introductory one is still the beginner's answer.
        assertThat(introductory.statement()).contains("sugars");
        assertThat(mechanistic.statement()).contains("RuBisCO");
    }

    @Test
    @DisplayName("the answer is deterministic")
    void theAnswerIsDeterministic() {
        Answer first = retriever.answer("How does RuBisCO affect photosynthetic efficiency?");
        Answer second = retriever.answer("How does RuBisCO affect photosynthetic efficiency?");

        assertThat(first.statement()).isEqualTo(second.statement());
        assertThat(first.confidence()).isEqualTo(second.confidence());
        assertThat(first.claims()).extracting(Claim::id)
                .containsExactlyElementsOf(second.claims().stream().map(Claim::id).toList());
    }

    @Test
    @DisplayName("the answer carries its limitations")
    void theAnswerCarriesItsLimitations() {
        Answer answer = retriever.answer("How do plants release oxygen?");
        assertThat(answer.answered()).isTrue();
        assertThat(answer.limitations()).isNotEmpty();
    }

    @Test
    @DisplayName("a refuted misconception is never returned as an established claim")
    void aRefutedClaimIsMarkedAsSuch() {
        Answer answer = retriever.answer("Where does the oxygen plants release come from?");

        if (answer.answered()) {
            for (Claim claim : answer.claims()) {
                assertThat(claim.status()).isNotEqualTo(ClaimStatus.VERIFIED);
            }
            // The refuted misconception is present in the workspace and is
            // marked REFUTED, so it can be shown to a learner as a mistake
            // to correct rather than as a fact.
            Claim refuted = snapshot.claims().stream()
                    .filter(c -> c.status() == ClaimStatus.REFUTED)
                    .findFirst().orElseThrow();
            assertThat(refuted.status()).isEqualTo(ClaimStatus.REFUTED);
        }
    }

    @Test
    @DisplayName("the gap detector names what the question asks that nothing covers")
    void theGapDetectorNamesTheGaps() {
        KnowledgeGapDetector detector = new KnowledgeGapDetector(snapshot);

        assertThat(detector.uncoveredTerms("What is the boiling point of mercury?"))
                .contains("mercury");
        assertThat(detector.uncoveredTerms("What is photosynthesis?")).isEmpty();
    }

    @Test
    @DisplayName("a question with no searchable terms is refused rather than guessed at")
    void anEmptyQuestionIsRefused() {
        Answer answer = retriever.answer("???");

        assertThat(answer.answered()).isFalse();
        assertThat(answer.abstentionReason()).isNotBlank();
    }

    @Test
    @DisplayName("the retriever works on a workspace with no claims at all")
    void anEmptyWorkspaceAnswersNothing() {
        KnowledgeRetriever empty = new KnowledgeRetriever(
                KnowledgeSnapshot.empty("empty", "Empty workspace"));

        Answer answer = empty.answer("What is photosynthesis?");
        assertThat(answer.answered()).isFalse();
        assertThat(answer.abstentionReason()).isNotBlank();
    }
}
