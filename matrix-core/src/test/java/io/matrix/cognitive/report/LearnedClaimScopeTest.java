package io.matrix.cognitive.report;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-EPIS-16 — the learned claim's applicability is not the concept ALONE.
 *
 * <p><b>The defect.</b> A learned claim was applied to an answer when the answer's
 * <b>concepts</b> included the claim's concept, and nothing else. That is too coarse: it
 * gave a question about a concept <b>every</b> learned claim of that concept, from
 * <b>any</b> document — including a claim validated against a document the question's
 * evidence never touches, and therefore about a different part of the concept altogether.</p>
 *
 * <p><b>The rule now.</b> The concept match <b>and</b> a common document: the answer's
 * evidence must draw on one of the sources the claim was <b>validated against</b>. The
 * learner's agreement gate established that the reference carried the claim on every
 * held-out question, and those questions' evidence came from exactly those sources — so the
 * claim's validated scope is "what the workspace says about this concept, drawn from these
 * documents", and the narrowed rule applies the claim exactly where that validation
 * speaks.</p>
 *
 * <p><b>What this test pins.</b> That the coarse rule would have added a claim of the same
 * concept from an unrelated document, that the narrowed rule does not, that the claim of the
 * same concept from a document the answer <b>does</b> draw on is still applied, and that the
 * rule fails <b>closed</b> when the data needed to compare is missing.</p>
 */
class LearnedClaimScopeTest {

    /** A concept both claims share — the coarse rule's only condition. */
    private static final Concept SHARED_CONCEPT =
            new Concept("c-plate", "ws", "plate tectonics", "the movement of the plates", "earth",
                    "2026-11-21T00:00:00Z");

    /** A concept that is NOT the claim's concept. */
    private static final Concept OTHER_CONCEPT =
            new Concept("c-other", "ws", "seafloor spreading", "the creation of new crust", "earth",
                    "2026-11-21T00:00:00Z");

    @Test
    @DisplayName("the claim of the same concept from an unrelated document does NOT apply")
    void theClaimOfAnUnrelatedDocumentDoesNotApply() {
        // the answer is about the shared concept, and its evidence comes from ONE document
        Answer answer = answerWith(SHARED_CONCEPT, List.of("src-b"));
        // the learned claim is of the SAME concept, but it was validated against another
        // document — so it is about a different part of the concept
        LearnedClaim unrelated = learnedClaim("learned-claim.a", "c-plate", "src-a",
                Set.of("src-a"));

        assertThat(LearnedClaims.applicableTo(answer, List.of(unrelated)))
                .as("the coarse rule - the concept ALONE - would have added this claim")
                .isEmpty();

        // and the same claim DOES apply when the answer's evidence draws on its document
        Answer sameDocument = answerWith(SHARED_CONCEPT, List.of("src-a", "src-b"));
        assertThat(LearnedClaims.applicableTo(sameDocument, List.of(unrelated)))
                .as("the claim still applies where its validation speaks")
                .hasSize(1);
    }

    @Test
    @DisplayName("the rule fails closed when the data needed to compare is missing")
    void theRuleFailsClosed() {
        LearnedClaim claim = learnedClaim("learned-claim.b", "c-plate", "src-a", Set.of("src-a"));

        // an answer with NO sources cannot be shown to share a document with the claim
        assertThat(LearnedClaims.applicableTo(answerWith(SHARED_CONCEPT, List.of()), List.of(claim)))
                .as("an answer with no sources adds nothing")
                .isEmpty();
        // a claim with NO support has no validated scope to compare
        assertThat(LearnedClaims.applicableTo(answerWith(SHARED_CONCEPT, List.of("src-a")),
                List.of(learnedClaim("learned-claim.c", "c-plate", "src-a", Set.of()))))
                .as("a claim with no support adds nothing")
                .isEmpty();
        // an answer with no concepts at all is out of scope for every claim
        assertThat(LearnedClaims.applicableTo(answerWith(null, List.of("src-a")), List.of(claim)))
                .as("an answer with no concepts adds nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("a claim of a DIFFERENT concept never applies, whatever its support")
    void aClaimOfAnotherConceptNeverApplies() {
        Answer answer = answerWith(SHARED_CONCEPT, List.of("src-a"));
        LearnedClaim other = learnedClaim("learned-claim.d", "c-other", "src-a", Set.of("src-a"));
        assertThat(LearnedClaims.applicableTo(answer, List.of(other)))
                .as("the concept must match as well")
                .isEmpty();
    }

    /**
     * An answer with one concept and the given sources.
     *
     * @param concept the answer's concept, or null for none
     * @param sources the sources the answer's evidence comes from
     * @return the answer
     */
    private static Answer answerWith(Concept concept, List<String> sources) {
        List<Concept> concepts = concept == null ? List.of() : List.of(concept);
        List<Source> answerSources = sources.stream()
                .map(id -> new Source(id, "ws", io.matrix.knowledge.SourceKind.SECONDARY_REPORT,
                        "title-" + id, "locator-" + id, 0, 0, "excerpt-" + id,
                        "2026-11-21T00:00:00Z", "hash-" + id, null))
                .toList();
        List<Claim> claims = List.of();
        return new Answer("the question", AbstractionLevel.INTRODUCTORY, true, null, "the answer",
                1.0, null, concepts, claims, answerSources, List.of(), List.of(), List.of(), null);
    }

    /**
     * A learned claim with a concept and a support.
     *
     * @param id        the claim's id
     * @param conceptId the claim's concept
     * @param sourceId  the claim's own source
     * @param support   the sources the claim was validated against
     * @return the claim
     */
    private static LearnedClaim learnedClaim(String id, String conceptId, String sourceId,
                                              Set<String> support) {
        return new LearnedClaim(id, LearnedClaim.FIRST_VERSION, "ws", "clm-1", "the proposition",
                conceptId, sourceId, support, 5, 1.0, null, "episode-1", "the note",
                "2026-11-21T00:00:00Z");
    }
}
