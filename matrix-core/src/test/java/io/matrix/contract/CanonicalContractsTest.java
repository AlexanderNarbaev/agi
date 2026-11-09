package io.matrix.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the invariants of the canonical cognitive contracts.
 *
 * <p>These tests exist because each of the records encodes a RULE, and a rule that is not
 * checked is a comment. The rules under test are the ones the wave's mandate names: a
 * candidate belief does not become an established answer because of a score; a graph
 * association is not proof of truth; association strength, frequency and evidential support
 * are not the same quantity; a prediction is settled before it is compared; a reflection
 * produces decisions rather than prose; and a plugin cannot widen its own authority.</p>
 */
class CanonicalContractsTest {

    private static Provenance provenance() {
        return Provenance.of("the test", "2026-11-08T00:00:00Z");
    }

    @Nested
    @DisplayName("the epistemic states are distinct and the answer path consults the status")
    class EpistemicStates {

        @Test
        @DisplayName("only SUPPORTED may be presented as an established fact")
        void establishedOnlyWhenSupported() {
            assertTrue(BeliefStatus.SUPPORTED.established());
            assertFalse(BeliefStatus.CANDIDATE.established());
            assertFalse(BeliefStatus.OBSERVED.established());
            assertFalse(BeliefStatus.ASSERTED.established());
            assertFalse(BeliefStatus.CONTESTED.established());
            assertFalse(BeliefStatus.REFUTED.established());
            assertFalse(BeliefStatus.UNRESOLVED.established());
            assertFalse(BeliefStatus.SUPERSEDED.established());
        }

        @Test
        @DisplayName("a refuted belief is not an answer, but it may be answered about")
        void refutedIsNotAnswerable() {
            assertFalse(BeliefStatus.REFUTED.answerable());
            assertTrue(BeliefStatus.SUPERSEDED.answerable());
            assertTrue(BeliefStatus.CONTESTED.answerable());
        }

        @Test
        @DisplayName("a high confidence does not promote a candidate belief")
        void confidenceDoesNotPromote() {
            Belief candidate = new Belief("b-1", "the proposition", BeliefStatus.CANDIDATE,
                    Confidence.declared(0.99, List.of("a high number")), List.of(), List.of(),
                    provenance(), ValidityInterval.UNBOUNDED, 1, null, null, null);

            assertFalse(candidate.established());
            assertFalse(candidate.answerable() && candidate.established());
            assertEquals(BeliefStatus.CANDIDATE, candidate.status());
        }

        @Test
        @DisplayName("a supported belief with a low confidence is still supported")
        void supportIsNotDerivedFromConfidence() {
            Belief supported = new Belief("b-2", "the proposition", BeliefStatus.SUPPORTED,
                    Confidence.measured(0.1, 1, 0, "the method", List.of()), List.of("e-1"), List.of(), provenance(),
                    ValidityInterval.UNBOUNDED, 1, null, null, null);

            assertTrue(supported.established());
            assertTrue(supported.confidence().supported());
        }
    }

    @Nested
    @DisplayName("confidence keeps its basis, and the basis is not a number")
    class ConfidenceBasis {

        @Test
        @DisplayName("an unestablished confidence says so")
        void unestablishedIsVisible() {
            assertTrue(Confidence.UNESTABLISHED.unestablished());
            assertEquals(Confidence.Basis.UNESTABLISHED, Confidence.UNESTABLISHED.basis());
            assertFalse(Confidence.measured(0.5, 1, 0, "the method", List.of()).unestablished());
        }

        @Test
        @DisplayName("a contradiction is visible even when the value is high")
        void contradictionIsVisible() {
            Confidence contested = Confidence.measured(0.9, 5, 2, "the method", List.of());
            assertTrue(contested.contested());
            assertTrue(contested.supported());
        }

        @Test
        @DisplayName("the value is bounded")
        void valueIsBounded() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Confidence(1.5, Confidence.Basis.DECLARED, 0, 0, null, List.of()));
            assertThrows(IllegalArgumentException.class,
                    () -> new Confidence(Double.NaN, Confidence.Basis.DECLARED, 0, 0, null, List.of()));
        }
    }

    @Nested
    @DisplayName("provenance carries the source, the origin and a declared offset unit")
    class ProvenanceSpan {

        @Test
        @DisplayName("a span names its unit")
        void spanNamesItsUnit() {
            Provenance.Span span = Provenance.Span.characters(10, 20);
            assertEquals(Provenance.Span.Unit.CHARACTERS, span.unit());
            assertTrue(span.bounded());
        }

        @Test
        @DisplayName("a span must be well formed")
        void spanIsWellFormed() {
            assertThrows(IllegalArgumentException.class,
                    () -> new Provenance.Span(-1, 10L, Provenance.Span.Unit.BYTES));
            assertThrows(IllegalArgumentException.class,
                    () -> new Provenance.Span(10L, 5L, Provenance.Span.Unit.BYTES));
        }

        @Test
        @DisplayName("a record without a source says that it has none")
        void absenceIsVisible() {
            assertFalse(provenance().hasSource());
            assertFalse(provenance().hasSpan());
        }
    }

    @Nested
    @DisplayName("a relation is not a proof")
    class Relations {

        private Relation relation(BeliefStatus status, double strength, int observations,
                                  boolean causal) {
            return new Relation("r-1", "CAUSES", "e-1", "e-2", status,
                    Confidence.measured(0.4, observations, 0, "the method", List.of()), strength,
                    observations, causal, List.of(), provenance());
        }

        @Test
        @DisplayName("a strong association is not an established relation")
        void strengthIsNotTruth() {
            Relation strong = relation(BeliefStatus.CANDIDATE, 0.99, 1000, false);
            assertFalse(strong.established());
            assertEquals(BeliefStatus.CANDIDATE, strong.status());
        }

        @Test
        @DisplayName("frequency, strength and evidential support stay separate fields")
        void quantitiesAreSeparate() {
            Relation r = relation(BeliefStatus.SUPPORTED, 0.2, 50, true);
            assertEquals(0.2, r.associationStrength());
            assertEquals(50, r.observationCount());
            assertTrue(r.causallyRelevant());
            assertEquals(50, r.confidence().supportingCount());
            assertEquals(0, r.confidence().contradictingCount());
        }
    }

    @Nested
    @DisplayName("a prediction is settled before it is compared")
    class Predictions {

        @Test
        @DisplayName("an open prediction is not settled")
        void openIsNotSettled() {
            Prediction prediction = new Prediction("p-1", "subject", "lamp is on",
                    Confidence.measured(0.8, 0, 0, "the method", List.of()), "the rule says so",
                    List.of("h-1", "h-2"), "2026-11-08T00:00:00Z", null, 0.25);
            assertFalse(prediction.settled());
            assertTrue(prediction.discriminating());
        }

        @Test
        @DisplayName("settling is a distinct step")
        void settlingIsDistinct() {
            Prediction prediction = new Prediction("p-1", "subject", "lamp is on",
                    Confidence.UNESTABLISHED, "the rule says so", List.of("h-1"),
                    "2026-11-08T00:00:00Z", null, 0.25);
            Prediction settled = prediction.settledAt("2026-11-08T00:00:01Z");
            assertTrue(settled.settled());
            assertFalse(prediction.settled());
        }

        @Test
        @DisplayName("the information gain cannot be negative")
        void informationGainIsBounded() {
            assertThrows(IllegalArgumentException.class, () -> new Prediction("p-1", "s", "e",
                    Confidence.UNESTABLISHED, "why", List.of(), "at", null, -0.1));
        }
    }

    @Nested
    @DisplayName("an outcome compares with a prediction, or says that it cannot")
    class Outcomes {

        @Test
        @DisplayName("an outcome without a prediction is not a failed prediction")
        void noPredictionIsNotAFailure() {
            Outcome outcome = new Outcome("o-1", "a-1", "obs-1", null, Outcome.Status.SUCCEEDED,
                    "the lamp is on", null, null, null, "2026-11-08T00:00:00Z");
            assertFalse(outcome.comparable());
            assertFalse(outcome.incorrect());
        }

        @Test
        @DisplayName("a deviation is visible and carries the magnitude")
        void deviationIsVisible() {
            Outcome outcome = Outcome.against("o-2", "a-1", "obs-1", "p-1", false, 1.0,
                    "the lamp is off", "2026-11-08T00:00:01Z");
            assertTrue(outcome.comparable());
            assertTrue(outcome.incorrect());
            assertEquals(Outcome.Status.DEVIATED, outcome.status());
            assertEquals(1.0, outcome.error());
        }
    }

    @Nested
    @DisplayName("reflection produces decisions, not prose")
    class Reflections {

        @Test
        @DisplayName("a reflection may learn nothing and say so")
        void learningNothingIsAllowed() {
            Reflection reflection = Reflection.learnedNothing("r-1", "ep-1", "t-1", "m-1",
                    "it was the only admitted method", "2026-11-08T00:00:02Z");
            assertFalse(reflection.learnedSomething());
            assertTrue(reflection.reuseMethod());
            assertTrue(reflection.decisions().isEmpty());
        }

        @Test
        @DisplayName("the decisions are machine readable")
        void decisionsAreMachineReadable() {
            Reflection.Decision decision = new Reflection.Decision(
                    Reflection.DECISION_PREFER_METHOD, "m-2", "it was right twice", Map.of());
            Reflection reflection = new Reflection("r-2", "ep-1", "t-1", "m-1", "reason", "p-1",
                    "lamp on", "lamp off", "the lamp was off", List.of("the rule is narrower"),
                    false, List.of("the third case is untested"), List.of(decision),
                    "2026-11-08T00:00:02Z");
            assertTrue(reflection.learnedSomething());
            assertTrue(reflection.predictionWrong());
            assertEquals(1, reflection.decisionsOfKind(Reflection.DECISION_PREFER_METHOD).size());
            assertEquals(1, reflection.uncertainties().size());
        }
    }

    @Nested
    @DisplayName("a plugin cannot widen its own authority")
    class Plugins {

        private Plugin plugin(List<String> declared, List<String> granted,
                              Plugin.Stage stage) {
            return new Plugin("p-1", 1, "the plugin", "does a thing", "in", "out", declared,
                    granted, List.of(), Plugin.Isolation.DECLARATIVE,
                    new Plugin.Budget(4, 100L, 1024L), "sha256:abc", stage, List.of("v-1"),
                    provenance(), "the registry removes it");
        }

        @Test
        @DisplayName("a declared capability that was not granted is visible")
        void ungrantedIsVisible() {
            Plugin p = plugin(List.of("observe", "network"), List.of("observe"),
                    Plugin.Stage.VALIDATED);
            assertTrue(p.requestsUngranted());
            assertTrue(p.holds("observe"));
            assertFalse(p.holds("network"));
        }

        @Test
        @DisplayName("only the serving stages serve")
        void onlyServingStagesServe() {
            assertFalse(plugin(List.of(), List.of(), Plugin.Stage.VALIDATED).serving());
            assertTrue(plugin(List.of(), List.of(), Plugin.Stage.SHADOW).serving());
            assertTrue(plugin(List.of(), List.of(), Plugin.Stage.ACTIVE).serving());
            assertFalse(plugin(List.of(), List.of(), Plugin.Stage.REVOKED).serving());
        }

        @Test
        @DisplayName("a grant can be narrowed after the fact")
        void grantCanBeNarrowed() {
            Plugin narrowed = plugin(List.of("observe"), List.of("observe"),
                    Plugin.Stage.ACTIVE).granting(List.of());
            assertFalse(narrowed.holds("observe"));
            assertTrue(narrowed.requestsUngranted());
        }
    }

    @Nested
    @DisplayName("the envelopes carry causality and refuse what they do not understand")
    class Envelopes {

        @Test
        @DisplayName("a command without a correlation is refused")
        void commandNeedsCorrelation() {
            assertThrows(IllegalArgumentException.class, () -> new CognitiveCommand("c", 1, "id",
                    null, "target", "{}", 100L, RetryPolicy.NONE, "at"));
        }

        @Test
        @DisplayName("a refusal must say why")
        void refusalMustSayWhy() {
            assertThrows(IllegalArgumentException.class, () -> new CognitiveResponse(
                    CognitiveResponse.Status.REJECTED, "  ", "{}", null, "id", 1));
        }

        @Test
        @DisplayName("a correlation keeps one request identity and adds the cause")
        void correlationKeepsOneRequest() {
            Correlation correlation = Correlation.ofRequest("req-1");
            Correlation caused = correlation.causedBy("cmd-1").inEpisode("ep-1");
            assertEquals("req-1", caused.correlationId());
            assertEquals("cmd-1", caused.causationId());
            assertEquals("ep-1", caused.episodeId());
            assertTrue(caused.caused());
            assertFalse(correlation.caused());
        }

        @Test
        @DisplayName("an incompatible contract version is not compatible")
        void versionCompatibility() {
            assertTrue(CognitiveContract.compatible(1, 1));
            assertFalse(CognitiveContract.compatible(2, 1));
            assertFalse(CognitiveContract.compatible(0, 1));
        }

        @Test
        @DisplayName("a redelivery is recognisable")
        void redeliveryIsRecognisable() {
            CognitiveEvent event = new CognitiveEvent("c", 1, "e-1",
                    Correlation.ofRequest("req-1"), "{}", "at", CognitiveEvent.FIRST_DELIVERY,
                    "chain-b");
            assertFalse(event.redelivery());
            assertTrue(new CognitiveEvent("c", 1, "e-1", Correlation.ofRequest("req-1"), "{}",
                    "at", 2, "chain-b").redelivery());
        }
    }

    @Nested
    @DisplayName("the retry policy is bounded")
    class Retries {

        @Test
        @DisplayName("the backoff grows and is capped")
        void backoffIsCapped() {
            RetryPolicy policy = new RetryPolicy(5, 100L, 2.0, 500L);
            assertEquals(0L, policy.backoffMillis(1));
            assertEquals(100L, policy.backoffMillis(2));
            assertEquals(200L, policy.backoffMillis(3));
            assertEquals(500L, policy.backoffMillis(9));
        }

        @Test
        @DisplayName("the attempt count is bounded")
        void attemptsAreBounded() {
            RetryPolicy policy = new RetryPolicy(2, 10L, 2.0, 100L);
            assertTrue(policy.mayRetry(1));
            assertFalse(policy.mayRetry(2));
            assertFalse(RetryPolicy.NONE.mayRetry(1));
        }

        @Test
        @DisplayName("a policy must be well formed")
        void policyMustBeWellFormed() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RetryPolicy(0, 10L, 2.0, 100L));
            assertThrows(IllegalArgumentException.class,
                    () -> new RetryPolicy(3, 100L, 2.0, 10L));
        }
    }

    @Nested
    @DisplayName("the world model keeps its parts")
    class WorldModel {

        @Test
        @DisplayName("a world state holds entities, relations and properties")
        void worldStateHoldsItsParts() {
            Entity entity = new Entity("e-1", "LAMP", "the lamp", "ws-1", Map.of("on", true),
                    provenance());
            Property property = new Property("pr-1", "e-1", "on", true, BeliefStatus.SUPPORTED,
                    Confidence.measured(1.0, 1, 0, "the method", List.of()), provenance(),
                    ValidityInterval.openFrom("2026-11-08T00:00:00Z"));
            Relation relation = new Relation("r-1", "PART_OF", "e-1", "e-2",
                    BeliefStatus.SUPPORTED, Confidence.measured(0.7, 2, 0, "m", List.of()), 0.3, 2,
                    false, List.of("ev-1"), provenance());
            WorldState state = new WorldState("ws-1", "lamp-world", 3L,
                    Map.of("e-1", entity), List.of(relation), List.of(property),
                    "2026-11-08T00:00:00Z", provenance());

            assertEquals(entity, state.entity("e-1"));
            assertEquals(1, state.establishedRelationsOfType("PART_OF").size());
            assertEquals(1, state.propertiesOf("e-1").size());
            assertEquals(4L, state.nextRevision());
        }

        @Test
        @DisplayName("an entity's references are found by name")
        void entityReferencesAreTyped() {
            Entity entity = new Entity("e-1", "LAMP", "the lamp", "ws-1",
                    Map.of("parts", List.of("e-2", "e-3")), provenance());
            assertEquals(List.of("e-2", "e-3"), entity.references("parts"));
            assertTrue(entity.references("missing").isEmpty());
        }
    }

    @Nested
    @DisplayName("the evaluation declares its criteria before it measures")
    class Evaluations {

        @Test
        @DisplayName("a failed evaluation keeps its measurements")
        void failureKeepsMeasurements() {
            Evaluation evaluation = Evaluation.passed("ev-1", "p-1", "PLUGIN",
                    Map.of("gate", "the plugin passes its declared cases"),
                    Map.of("cases", new Evaluation.Metric(2.0, 2)), "2026-11-08T00:00:00Z");
            assertTrue(evaluation.passed());
            assertNotNull(evaluation.metric("cases"));
            assertEquals(2, evaluation.metric("cases").sampleSize());

            Evaluation failed = evaluation.failed("one case did not pass");
            assertFalse(failed.passed());
            assertNotNull(failed.metric("cases"));
        }
    }
}
