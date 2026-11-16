package io.matrix.knowledge.concept.definitions;

import io.matrix.knowledge.acquisition.SourceSpanDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P1 — the rule of the SOURCE-SPAN-ANCHORED DEFINITION.
 *
 * <p>The rule is the frozen tight v1.1 rule of
 * {@code docs-v2/engineering/C2A-CANDIDATE-FREEZE.md} §3.1, and this class pins
 * it: the adjacency, the closed copula vocabulary, the length cap, the verbatim
 * property and the refusal to invent.</p>
 *
 * <p>Every threshold and every word list is a named constant of the production
 * class, so a change to the rule is a change to that class and this test turns
 * red.</p>
 */
class SourceSpanDefinitionRuleTest {

    /** A sentence that states a definition: the name, then the copula. */
    private static final String DEFINES =
            "Plate tectonics is the scientific theory that Earth's lithosphere comprises a number "
                    + "of large plates.";

    /** A sentence that names the concept and says something else about it. */
    private static final String NAMES_ONLY =
            "A trench forms and, as the subducted plate is heated, it releases volatiles, mostly "
                    + "water from hydrous minerals.";

    @Test
    @DisplayName("the name must be IMMEDIATELY followed by a definitional copula")
    void theNameMustBeImmediatelyFollowedByADefinitionalCopula() {
        assertThat(SourceSpanDefinitions.statesDefinition(DEFINES, "Plate tectonics")).isTrue();
        // The same sentence, and a copula that is merely somewhere in it.
        assertThat(SourceSpanDefinitions.statesDefinition(NAMES_ONLY, "A trench")).isFalse();
        assertThat(SourceSpanDefinitions.statesDefinition(
                "Plate tectonics was proposed in 1965 and is widely accepted today.",
                "Plate tectonics")).isFalse();
    }

    @Test
    @DisplayName("the copula vocabulary is closed: a word that is not in it does not define")
    void theCopulaVocabularyIsClosed() {
        for (String copula : SourceSpanDefinitions.DEFINITIONAL_COPULAS) {
            assertThat(SourceSpanDefinitions.statesDefinition(
                    "Plate tectonics " + copula + " the theory of moving plates.",
                    "Plate tectonics"))
                    .as("the copula %s is in the frozen vocabulary", copula)
                    .isTrue();
        }
        // A verb that is NOT a definitional copula: the sentence says something else.
        assertThat(SourceSpanDefinitions.statesDefinition(
                "Plate tectonics explains the movement of the plates.",
                "Plate tectonics")).isFalse();
        assertThat(SourceSpanDefinitions.statesDefinition(
                "Plate tectonics resembles the theory of continental drift.",
                "Plate tectonics")).isFalse();
    }

    @Test
    @DisplayName("the length cap is applied: a paragraph is not a definition")
    void theLengthCapIsApplied() {
        String longSentence = "Plate tectonics is " + "x".repeat(SourceSpanDefinitions
                .MAX_DEFINITION_LENGTH) + " the theory.";
        assertThat(longSentence.length())
                .as("the sentence is over the cap")
                .isGreaterThan(SourceSpanDefinitions.MAX_DEFINITION_LENGTH);
        assertThat(SourceSpanDefinitions.statesDefinition(longSentence, "Plate tectonics")).isFalse();
        // One character under the cap still qualifies.
        String atCap = "Plate tectonics is " + "x".repeat(
                SourceSpanDefinitions.MAX_DEFINITION_LENGTH - "Plate tectonics is ".length()
                        - " the theory.".length()) + " the theory.";
        assertThat(atCap.length()).isEqualTo(SourceSpanDefinitions.MAX_DEFINITION_LENGTH);
        assertThat(SourceSpanDefinitions.statesDefinition(atCap, "Plate tectonics")).isTrue();
    }

    @Test
    @DisplayName("a multi-word name is matched as a contiguous token sequence")
    void aMultiWordNameIsMatchedAsAContiguousTokenSequence() {
        assertThat(SourceSpanDefinitions.holdsName(DEFINES, "Plate tectonics")).isTrue();
        assertThat(SourceSpanDefinitions.holdsName(DEFINES, "plate TECTONICS")).isTrue();
        // The tokens must be contiguous and in this order.
        assertThat(SourceSpanDefinitions.holdsName(DEFINES, "tectonics Plate")).isFalse();
        assertThat(SourceSpanDefinitions.holdsName(DEFINES, "Earth's lithosphere")).isTrue();
        assertThat(SourceSpanDefinitions.holdsName(DEFINES, "lithosphere Earth's")).isFalse();
    }

    @Test
    @DisplayName("nothing is invented: a concept with no qualifying sentence has no definition")
    void nothingIsInvented() {
        assertThat(SourceSpanDefinitions.statesDefinition(null, "Plate tectonics")).isFalse();
        assertThat(SourceSpanDefinitions.statesDefinition("", "Plate tectonics")).isFalse();
        assertThat(SourceSpanDefinitions.statesDefinition(DEFINES, "")).isFalse();
        assertThat(SourceSpanDefinitions.statesDefinition(DEFINES, null)).isFalse();
        assertThat(SourceSpanDefinitions.statesDefinition(NAMES_ONLY, "A trench")).isFalse();
    }

    @Test
    @DisplayName("the words of a text are its letter-or-digit runs, lower-cased")
    void theWordsOfATextAreItsTokens() {
        assertThat(SourceSpanDefinitions.words("Plate tectonics (1965) is a theory."))
                .containsExactly("plate", "tectonics", "1965", "is", "a", "theory");
        assertThat(SourceSpanDefinitions.words(null)).isEmpty();
    }

    @Test
    @DisplayName("the rule is deterministic: the same text always gives the same answer")
    void theRuleIsDeterministic() {
        for (int run = 0; run < 3; run++) {
            assertThat(SourceSpanDefinitions.statesDefinition(DEFINES, "Plate tectonics")).isTrue();
            assertThat(SourceSpanDefinitions.statesDefinition(NAMES_ONLY, "A trench")).isFalse();
        }
        assertThat(SourceSpanDefinitions.DEFINITION_METHOD)
                .as("the method name is the frozen one, so a definition is traceable")
                .isEqualTo("definitional-adjacency-first-sentence-v1.1");
    }
}
