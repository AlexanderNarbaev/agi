package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.brain.runtime.FactTriples.Level;
import io.matrix.brain.runtime.FactTriples.Report;
import io.matrix.brain.runtime.FactTriples.Triple;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * W36.2 — acceptance for logical contradiction detection.
 *
 * <p>These replace the capability the similarity heuristic used to advertise. The two properties
 * that matter are stated first, because the old implementation failed both:</p>
 *
 * <ol>
 *   <li>a genuine contradiction must be reported as a contradiction, and</li>
 *   <li>a near-duplicate must NOT be reported as a contradiction.</li>
 * </ol>
 *
 * <p>Under the old Jaccard scheme, three real contradictions measured 0.714 and came back
 * {@code DUPLICATE} — the most confident label available — while the only pair carrying logical
 * negation measured 0.333 and came back {@code NOVEL}, a miss.</p>
 */
class FactTriplesTest {

    @Nested
    @DisplayName("Acceptance criteria from the specification")
    class Acceptance {

        @Test
        @DisplayName("'Paris is capital' vs 'Lyon is capital' is a CONTRADICTION")
        void sameSubjectAndPredicateDifferentObject() {
            Report r = FactTriples.compareSentences(
                    "The capital of France is Paris",
                    "The capital of France is Lyon");
            assertThat(r.level())
                    .as("same subject, same predicate, incompatible objects — this is the "
                            + "definition of a contradiction and the old detector called it "
                            + "DUPLICATE at 0.714 similarity")
                    .isEqualTo(Level.CONTRADICTION);
            assertThat(r.a().subject()).isEqualTo("france");
            assertThat(r.a().predicate()).isEqualTo("capital-of");
            assertThat(r.b().object()).isEqualTo("lyon");
        }

        @Test
        @DisplayName("'Paris is capital' vs 'Paris is a city' is NOVEL, not a contradiction")
        void sameSubjectDifferentPredicateIsNovel() {
            Report r = FactTriples.compareSentences(
                    "The capital of France is Paris",
                    "France is a city");
            assertThat(r.level())
                    .as("a different predicate is a different claim; there is nothing to "
                            + "conflict about")
                    .isEqualTo(Level.NOVEL);
        }
    }

    @Nested
    @DisplayName("Cases the similarity heuristic got wrong")
    class PreviouslyMissed {

        @Test
        @DisplayName("boiling-point disagreement is a contradiction")
        void boilingPoint() {
            assertThat(FactTriples.compareSentences(
                    "Water boils at 100 degrees Celsius",
                    "Water boils at 50 degrees Celsius").level())
                    .isEqualTo(Level.CONTRADICTION);
        }

        @Test
        @DisplayName("port disagreement is a contradiction")
        void portDisagreement() {
            assertThat(FactTriples.compareSentences(
                    "The server runs on port 8080",
                    "The server runs on port 9090").level())
                    .isEqualTo(Level.CONTRADICTION);
        }

        @Test
        @DisplayName("asserting and denying the same triple is a NEGATION")
        void assertionVersusDenial() {
            Report r = FactTriples.compareSentences(
                    "The patient is allergic to penicillin",
                    "The patient is not allergic to penicillin");
            assertThat(r.level())
                    .as("the old detector scored this 0.333 and called it NOVEL — a miss, and "
                            + "the exact case a contradiction detector exists for")
                    .isEqualTo(Level.NEGATION);
            assertThat(r.a().negated()).isFalse();
            assertThat(r.b().negated()).isTrue();
        }
    }

    @Nested
    @DisplayName("Near-duplicates must NOT be reported as contradictions")
    class NotContradictions {

        @Test
        @DisplayName("the same fact stated twice is a DUPLICATE")
        void identicalFactsAreDuplicates() {
            assertThat(FactTriples.compareSentences(
                    "The capital of France is Paris",
                    "The capital of France is Paris").level())
                    .as("identical triples are agreement, not disagreement")
                    .isEqualTo(Level.DUPLICATE);
        }

        @Test
        @DisplayName("a paraphrase with the same triple is still a DUPLICATE")
        void paraphraseIsDuplicate() {
            assertThat(FactTriples.compareSentences(
                    "France is located in Europe",
                    "France is in Europe").level())
                    .as("different surface forms, identical triple")
                    .isEqualTo(Level.DUPLICATE);
        }

        @Test
        @DisplayName("unrelated facts are NOVEL, never CONTRADICTION")
        void unrelatedFactsAreNovel() {
            for (String[] pair : List.of(
                    new String[]{"The mitochondrion is the powerhouse of the cell",
                            "Tectonic plates move very slowly"},
                    new String[]{"Water boils at 100 degrees Celsius",
                            "The server runs on port 8080"},
                    new String[]{"France is located in Europe", "Japan is located in Asia"})) {
                assertThat(FactTriples.compareSentences(pair[0], pair[1]).level())
                        .as("unrelated: %s / %s", pair[0], pair[1])
                        .isNotEqualTo(Level.CONTRADICTION);
            }
        }

        @Test
        @DisplayName("different subjects with the same predicate do not contradict")
        void differentSubjectsDoNotContradict() {
            assertThat(FactTriples.compareSentences(
                    "France is located in Europe", "Japan is located in Asia").level())
                    .as("same predicate, different subject: both can be true")
                    .isEqualTo(Level.NOVEL);
        }
    }

    @Nested
    @DisplayName("Honesty about limits")
    class Limits {

        @Test
        @DisplayName("a sentence with no known predicate is UNPARSEABLE, not silently NOVEL")
        void unparseableIsVisible() {
            // NB: "Purple is a colour" is NOT unparseable any more. It reduces to
            // (purple, is-a, colour) through the fallback, and reporting it as a miss would be
            // wrong — we CAN represent that claim. Genuinely unrepresentable input has no copula
            // and no recognised relation, so nothing is asserted or denied about it at all.
            assertThat(FactTriples.parse("Purple is a colour"))
                    .as("a plain copular claim IS representable via the is-a fallback")
                    .hasSize(1);
            assertThat(FactTriples.compareSentences(
                    "Purple is a colour", "Green is a colour").level())
                    .as("two different subjects with the same weak predicate are novel")
                    .isNotEqualTo(Level.CONTRADICTION);
            assertThat(FactTriples.compareSentences(
                    "Purple", "Green").level())
                    .as("a miss in a contradiction detector is the dangerous direction, so it has "
                            + "its own level rather than being absorbed into NOVEL")
                    .isEqualTo(Level.UNPARSEABLE);
        }

        @Test
        @DisplayName("blank input is UNPARSEABLE, never a contradiction")
        void blankIsUnparseable() {
            assertThat(FactTriples.compareSentences("", "The capital of France is Paris").level())
                    .isEqualTo(Level.UNPARSEABLE);
            assertThat(FactTriples.compareSentences(null, null).level())
                    .isEqualTo(Level.UNPARSEABLE);
        }

        @Test
        @DisplayName("every verdict carries a human-readable explanation")
        void explanationsAreAlwaysPresent() {
            for (String[] pair : List.of(
                    new String[]{"The capital of France is Paris", "The capital of France is Lyon"},
                    new String[]{"The capital of France is Paris", "France is a city"},
                    new String[]{"Purple is a colour", "Green is a colour"})) {
                Report r = FactTriples.compareSentences(pair[0], pair[1]);
                assertThat(r.explanation())
                        .as("a verdict nobody can read is not auditable: %s / %s",
                                pair[0], pair[1])
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("parsing extracts the triple it claims")
        void parseProducesTheTriple() {
            List<Triple> t = FactTriples.parse("The capital of France is Paris");
            assertThat(t).hasSize(1);
            assertThat(t.get(0).subject()).isEqualTo("france");
            assertThat(t.get(0).predicate()).isEqualTo("capital-of");
            assertThat(t.get(0).object()).isEqualTo("paris");
            assertThat(t.get(0).negated()).isFalse();
        }

        @Test
        @DisplayName("contradiction detection does not depend on lexical overlap")
        void lexicalOverlapIsIrrelevant() {
            // These two share almost no tokens but ARE a contradiction, which is precisely the
            // case the similarity heuristic could not see.
            Report r = FactTriples.compareSentences(
                    "Water freezes at 0 degrees Celsius",
                    "Water freezes at 40 degrees Celsius");
            assertThat(r.level()).isEqualTo(Level.CONTRADICTION);
            assertThat(FactTriples.tokens("Water freezes at 0 degrees Celsius"))
                    .containsExactly("water", "freezes", "at", "0", "degrees", "celsius");
        }
    }
}