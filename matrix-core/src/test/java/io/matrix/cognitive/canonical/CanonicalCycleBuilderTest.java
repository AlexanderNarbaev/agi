package io.matrix.cognitive.canonical;

import io.matrix.cognitive.LearningTrace;
import io.matrix.cognitive.Percept;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;
import io.matrix.cognitive.microworld.RuleHypothesis;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.CognitiveCycle;
import io.matrix.contract.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W52 §3 — the mapping from the runtime's vocabulary to the canonical one.
 *
 * <p>The end-to-end coverage test proves that the runtime fills the canonical contracts.
 * This one proves the rules the mapping follows — above all the rule that <b>an absent fact
 * becomes an absent record</b>, and the rule that a hypothesis with no support is
 * UNRESOLVED rather than SUPPORTED.</p>
 */
class CanonicalCycleBuilderTest {

    private static final String AT = "2026-11-09T00:00:00Z";

    private static WorldInput assignment() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("power", true);
        values.put("switch", true);
        return new WorldInput(values);
    }

    private static RuleHypothesis hypothesis(String id, int supporting, int contradicting) {
        return new RuleHypothesis(id, "the lamp is on when power and switch are both on",
                Map.of("power=true,switch=true", true), supporting, contradicting);
    }

    @Test
    @DisplayName("a cycle that produced nothing carries nothing")
    void anEmptyCycleCarriesNothing() {
        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-1", AT, "sig-1", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(), null, null, null, null, null, null,
                null, null, null, null));

        assertThat(cycle.id()).isEqualTo("cyc-ep-1");
        assertThat(cycle.episodeId()).isEqualTo("ep-1");
        assertThat(cycle.goal()).isNotNull();
        assertThat(cycle.task()).isNotNull();
        assertThat(cycle.prediction()).isNull();
        assertThat(cycle.observation()).isNull();
        assertThat(cycle.outcome()).isNull();
        assertThat(cycle.algorithm()).isNull();
        assertThat(cycle.worldState()).isNull();
        assertThat(cycle.hypotheses()).isEmpty();
        assertThat(cycle.evidences()).isEmpty();
        assertThat(cycle.produced("Prediction")).isFalse();
        assertThat(cycle.produced("Entity")).isFalse();
        assertThat(cycle.produced("Goal")).isTrue();
    }

    @Test
    @DisplayName("a hypothesis with no support is unresolved, not supported")
    void anUnsupportedHypothesisIsUnresolved() {
        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-2", AT, "sig-2", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(hypothesis("h-1", 0, 0)), null, null, null,
                null, null, null, null, null, null, null));

        assertThat(cycle.hypotheses()).hasSize(1);
        assertThat(cycle.hypotheses().get(0).status()).isEqualTo(BeliefStatus.UNRESOLVED);
        assertThat(cycle.hypotheses().get(0).confidence().supportingCount()).isZero();
    }

    @Test
    @DisplayName("a contradicted hypothesis is contested, and the counts say why")
    void aContradictedHypothesisIsContested() {
        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-3", AT, "sig-3", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(hypothesis("h-1", 2, 1)), null, null, null,
                null, null, null, null, null, null, null));

        io.matrix.contract.Hypothesis hypothesis = cycle.hypotheses().get(0);
        assertThat(hypothesis.status()).isEqualTo(BeliefStatus.CONTESTED);
        assertThat(hypothesis.confidence().supportingCount()).isEqualTo(2);
        assertThat(hypothesis.confidence().contradictingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the method decision becomes the canonical algorithm, with its reason")
    void theDecisionBecomesTheAlgorithm() {
        AlgorithmDecision decision = new AlgorithmDecision(
                "micro-world-outcome", "the-method", "v1", "it was the only admitted candidate",
                List.of(), null, AlgorithmDecision.Status.COMPLETED, null, "the method answered");

        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-4", AT, "sig-4", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(), null, null, null, null, decision, null,
                null, null, null, "the.capability"));

        assertThat(cycle.algorithm()).isNotNull();
        assertThat(cycle.algorithm().id()).isEqualTo("the-method");
        assertThat(cycle.algorithm().family()).isEqualTo("micro-world-outcome");
        assertThat(cycle.algorithm().description()).contains("only admitted candidate");
        assertThat(cycle.capability()).isNotNull();
        assertThat(cycle.capability().id()).isEqualTo("the.capability");
    }

    @Test
    @DisplayName("reuse is a decision, not a fact: an active procedure is offered again, and "
            + "what its agreement rests on is named in its own record")
    void reuseIsADecisionNotAFact() {
        // W53. The canonical contract says: "reusable() is the system's own judgement that
        // the procedure should be offered again. It is set by what happened, and it can be
        // withdrawn." The settled-model lookup IS offered again — it is the fast path — so
        // the skill says so. What the skill does NOT say is that the procedure is
        // VALIDATED: that is a different claim, and it is carried by the procedure.
        Procedure inSample = new Procedure("proc-1", "the shortcut", "micro-world-outcome",
                "v1", "the model settled", java.util.Set.of("MODEL_SETTLED"), "the-reference",
                4, 1.0, 3, 1, AT, null, ValidationKind.IN_SAMPLE, "ep-4",
                "the agreement was measured over the 4 case(s) the model was built from");

        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-5", AT, "sig-5", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(), null, null, null, null, null, null,
                inSample, null, null, null));

        assertThat(cycle.procedure()).isNotNull();
        assertThat(cycle.procedure().parameters()).containsEntry("validationKind", "IN_SAMPLE");
        assertThat(cycle.procedure().parameters()).containsEntry("learnedInEpisode", "ep-4");
        assertThat(cycle.procedure().parameters())
                .containsEntry("validationNote", inSample.validationNote());
        assertThat(cycle.skill()).isNotNull();
        assertThat(cycle.skill().reusable())
                .withFailMessage("the system offers the settled-model lookup again")
                .isTrue();
        assertThat(cycle.skill().learnedFromEpisodeIds()).containsExactly("ep-4");
        assertThat(cycle.skill().attempts()).isEqualTo(3);
        assertThat(cycle.skill().successes()).isEqualTo(2);
    }

    @Test
    @DisplayName("a withdrawn procedure is not offered again")
    void aWithdrawnProcedureIsNotOfferedAgain() {
        Procedure withdrawn = new Procedure("proc-3", "the shortcut", "micro-world-outcome",
                "v1", "the model settled", java.util.Set.of("MODEL_SETTLED"), "the-reference",
                4, 1.0, 3, 1, AT, AT, ValidationKind.IN_SAMPLE, "ep-4",
                "the agreement was measured over the 4 case(s) the model was built from");

        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-8", AT, "sig-8", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(), null, null, null, null, null, null,
                withdrawn, null, null, null));

        assertThat(cycle.skill()).isNotNull();
        assertThat(cycle.skill().reusable())
                .withFailMessage("a withdrawn procedure is not offered again")
                .isFalse();
    }

    @Test
    @DisplayName("a validated procedure is marked reusable, and the evidence is named")
    void aValidatedProcedureIsReusable() {
        Procedure validated = new Procedure("proc-2", "the shortcut", "micro-world-outcome",
                "v1", "the model settled", java.util.Set.of("MODEL_SETTLED"), "the-reference",
                2, 1.0, 3, 0, AT, null, ValidationKind.INDEPENDENT);

        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-6", AT, "sig-6", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(), null, null, null, null, null, null,
                validated, null, null, null));

        assertThat(cycle.procedure().parameters()).containsEntry("validationKind", "INDEPENDENT");
        assertThat(cycle.procedure().parameters()).containsEntry("validationCases", "2");
        assertThat(cycle.skill().reusable()).isTrue();
        // The validation's standing is the procedure's own claim, and it is visible here.
        assertThat(cycle.procedure().parameters()).containsEntry("validationKind", "INDEPENDENT");
    }

    @Test
    @DisplayName("the evidence says what it is about, and the belief keeps the two apart")
    void theEvidenceAndTheBeliefAreSeparate() {
        io.matrix.cognitive.microworld.Observation observation =
                new io.matrix.cognitive.microworld.Observation("obs-1", assignment(), true,
                        false, AT);
        io.matrix.cognitive.Prediction prediction = new io.matrix.cognitive.Prediction(
                "prd-1", "ws", "ep-7", "lamp@power=true,switch=true", "true",
                "surviving-hypotheses", "v1", 1.0, false, List.of("case-1"), AT);

        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-7", AT, "sig-7", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(hypothesis("h-1", 1, 0)), null, prediction,
                observation, null, null, null, null, null, null, null));

        assertThat(cycle.observation()).isNotNull();
        assertThat(cycle.observation().payload()).containsEntry("lamp", true);
        assertThat(cycle.evidences()).hasSize(1);
        Evidence evidence = cycle.evidences().get(0);
        assertThat(evidence.relation()).isEqualTo(Evidence.Relation.SUPPORTS);
        assertThat(cycle.belief()).isNotNull();
        assertThat(cycle.belief().status()).isEqualTo(BeliefStatus.SUPPORTED);
        assertThat(cycle.belief().evidenceIds()).containsExactly(evidence.id());
        assertThat(cycle.outcome()).isNotNull();
        assertThat(cycle.outcome().matched()).isTrue();
        assertThat(cycle.outcome().error()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("the trace becomes the cycle's own event, with its phases")
    void theTraceBecomesTheEvent() {
        LearningTrace trace = new LearningTrace("trace-1", "ep-8", List.of(
                new LearningTrace.Step("PERCEIVE", "ChannelClassifier", "the channel"),
                new LearningTrace.Step("ANSWER", "CognitiveKernel", "the answer")));

        CognitiveCycle cycle = CanonicalCycleBuilder.build(new CycleFacts(
                "ep-8", AT, "sig-8", io.matrix.cognitive.CognitiveSignal.Channel.STRUCTURED,
                "the assignment", null, List.of(), null, null, null, null, null, null,
                null, trace, null, null));

        assertThat(cycle.event()).isNotNull();
        assertThat(cycle.event().kind())
                .isEqualTo(CanonicalCycleBuilder.EVENT_KIND_COGNITIVE_CYCLE);
        assertThat(cycle.event().payload()).containsKey("phases");
        assertThat((List<String>) cycle.event().payload().get("phases"))
                .containsExactly("PERCEIVE", "ANSWER");
        assertThat(cycle.correlation().episodeId()).isEqualTo("ep-8");
    }
}
