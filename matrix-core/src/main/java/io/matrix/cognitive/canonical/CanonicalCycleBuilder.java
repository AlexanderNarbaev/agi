package io.matrix.cognitive.canonical;

import io.matrix.cognitive.ErrorSignal;
import io.matrix.cognitive.LearningTrace;
import io.matrix.cognitive.MicroWorldCodec;
import io.matrix.cognitive.Prediction;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.microworld.RuleHypothesis;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.cognitive.worldstate.MicroWorldStateAdapter;
import io.matrix.cognitive.worldstate.WorldStateMemory;
import io.matrix.contract.Action;
import io.matrix.contract.Algorithm;
import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Capability;
import io.matrix.contract.Confidence;
import io.matrix.contract.CognitiveCycle;
import io.matrix.contract.Correlation;
import io.matrix.contract.Event;
import io.matrix.contract.Evaluation;
import io.matrix.contract.Evidence;
import io.matrix.contract.Goal;
import io.matrix.contract.Hypothesis;
import io.matrix.contract.Observation;
import io.matrix.contract.Outcome;
import io.matrix.contract.Procedure;
import io.matrix.contract.Provenance;
import io.matrix.contract.Reflection;
import io.matrix.contract.Relation;
import io.matrix.contract.Skill;
import io.matrix.contract.Task;
import io.matrix.contract.ValidityInterval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W52 §3 — the mapping from the runtime's own vocabulary to the canonical one.
 *
 * <p><b>What this component is.</b> The repository holds two vocabularies: the canonical
 * one in {@code io.matrix.contract}, which is the interchange form, and the runtime's own,
 * which is older, lives in the packages that own the behaviour, and is what the store's
 * schema and the W45.1 recovery contracts are written against. The mandate forbids
 * "competing implementations of the same responsibility", and two vocabularies for one
 * concept are exactly that — unless the boundary between them is <b>explicit, named and
 * tested</b>. This class is that boundary.</p>
 *
 * <p><b>What it is not.</b> It does not replace the runtime's records and it does not
 * rewrite the store. The internal records stay: they are the durable representation, and
 * replacing them would be a refactor of a working, durable path for no measured gain. What
 * this class guarantees is that <b>the canonical vocabulary is filled from the runtime's
 * facts, and that every conversion is stated in one place</b>.</p>
 *
 * <p><b>The rule about absence.</b> A fact that the cycle did not have becomes an absent
 * canonical record — never a placeholder, never a default that looks like a fact. The one
 * exception is stated in the code: a hypothesis with no support is
 * {@link BeliefStatus#UNRESOLVED}, because that is what "nothing supports it and nothing
 * refutes it" means, and it is a fact about the hypothesis.</p>
 *
 * <p><b>Determinism.</b> The mapping is a pure function of the facts: the same cycle
 * produces the same canonical records. It reads no clock and no randomness — the timestamp
 * comes from the cycle's own clock, which the runtime already owns.</p>
 */
public final class CanonicalCycleBuilder {

    /** The kind of the cycle's own event. */
    public static final String EVENT_KIND_COGNITIVE_CYCLE = "COGNITIVE_CYCLE";

    private CanonicalCycleBuilder() {
    }

    /**
     * Builds the canonical form of a cycle.
     *
     * @param facts what the cycle knows about itself
     * @return the canonical cycle
     */
    public static CognitiveCycle build(CycleFacts facts) {
        String episodeId = facts.episodeId();
        String at = facts.at();
        Provenance provenance = provenance(facts, at);

        Goal goal = goal(facts);
        Task task = task(facts, goal);
        List<Hypothesis> hypotheses = hypotheses(facts, provenance);
        io.matrix.contract.Prediction prediction = prediction(facts, hypotheses, at);
        Action action = action(facts);
        io.matrix.contract.Experiment experiment =
                experiment(facts, action, hypotheses, at, provenance);
        Observation observation = observation(facts, provenance);
        Outcome outcome = outcome(facts, prediction, observation, at);
        List<Evidence> evidences = evidences(facts, at);
        Belief belief = belief(facts, prediction, evidences, at, provenance);
        Event event = event(facts, at, provenance);
        Algorithm algorithm = algorithm(facts);
        Procedure procedure = procedure(facts, at, provenance);
        Skill skill = skill(facts, procedure, at, provenance);
        Evaluation evaluation = evaluation(facts);
        Reflection reflection = facts.reflection();
        WorldStateMemory world = facts.world();
        io.matrix.contract.WorldState worldState = world == null ? null : world.state();
        List<io.matrix.contract.StateTransition> transitions =
                world == null ? List.of() : world.transitions();
        ValidityInterval validity = validity(facts, at);
        Correlation correlation = correlation(facts);
        Capability capability = capability(facts);
        Confidence confidence = confidence(facts, prediction);

        return new CognitiveCycle("cyc-" + episodeId, episodeId, goal, task, hypotheses,
                prediction, experiment, action, outcome, observation, evidences, belief, event,
                algorithm, procedure, skill, evaluation, reflection, worldState, transitions,
                validity, correlation, capability, confidence, provenance);
    }

    // ── the provenance of the cycle ──────────────────────────────────────────

    private static Provenance provenance(CycleFacts facts, String at) {
        return new Provenance(
                facts.signalId(),
                "cognitive-kernel",
                "the cognitive cycle",
                null,
                List.of("episode:" + facts.episodeId()),
                at);
    }

    // ── the goal and the task ───────────────────────────────────────────────

    private static Goal goal(CycleFacts facts) {
        String subject = facts.subject() == null || facts.subject().isBlank()
                ? "the requested assignment" : facts.subject();
        return new Goal("goal-" + facts.episodeId(),
                "decide what the world does for " + subject,
                List.of("the answer is supported by the evidence the workspace holds",
                        "the disagreement between the prediction and the observation is recorded"),
                1.0,
                1,
                Goal.Status.ACTIVE,
                null,
                List.of("task-" + facts.episodeId()),
                facts.at(),
                null);
    }

    private static Task task(CycleFacts facts, Goal goal) {
        return new Task("task-" + facts.episodeId(),
                goal == null ? null : goal.id(),
                "the cycle's unit of work",
                "a supported answer for the requested assignment",
                "one cycle of the micro-world",
                "the cycle has compared its prediction with the observation",
                "the cycle's budget is exhausted",
                List.of(Action.KIND_OBSERVE, Action.KIND_PROBE),
                io.matrix.cognitive.CognitiveKernel.DEFAULT_CYCLE_BUDGET_MILLIS,
                Task.Status.OPEN,
                facts.at(),
                null);
    }

    // ── the hypotheses ──────────────────────────────────────────────────────

    private static List<Hypothesis> hypotheses(CycleFacts facts, Provenance provenance) {
        List<Hypothesis> result = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (RuleHypothesis hypothesis : facts.hypotheses()) {
            ids.add(hypothesis.id());
        }
        for (RuleHypothesis hypothesis : facts.hypotheses()) {
            // The status follows the EVIDENCE, and the evidence is not one number: a
            // hypothesis that something contradicts is CONTESTED even when other cases
            // support it, because a contradicted hypothesis is not a settled one; one that
            // only support exists for is SUPPORTED; and one that neither support nor
            // contradiction has reached is UNRESOLVED — a fact about it, not a
            // placeholder for a record nobody had.
            BeliefStatus status = hypothesis.contradictingCases() > 0
                    ? BeliefStatus.CONTESTED
                    : (hypothesis.supportingCases() > 0
                    ? BeliefStatus.SUPPORTED : BeliefStatus.UNRESOLVED);
            List<String> competing = new ArrayList<>(ids);
            competing.remove(hypothesis.id());
            result.add(new Hypothesis(hypothesis.id(),
                    hypothesis.statement(),
                    status,
                    new Confidence(1.0, Confidence.Basis.MEASURED,
                            hypothesis.supportingCases(), hypothesis.contradictingCases(),
                            "the cases the model was measured against",
                            List.of("the truth table " + hypothesis.truthTable())),
                    competing,
                    "the hypothesis survived the cases the session holds",
                    null,
                    null,
                    provenance));
        }
        return result;
    }

    // ── the prediction ──────────────────────────────────────────────────────

    private static io.matrix.contract.Prediction prediction(CycleFacts facts,
                                                            List<Hypothesis> hypotheses,
                                                            String at) {
        Prediction internal = facts.prediction();
        if (internal == null) {
            return null;
        }
        List<String> basedOn = new ArrayList<>();
        for (Hypothesis hypothesis : hypotheses) {
            basedOn.add(hypothesis.id());
        }
        return new io.matrix.contract.Prediction(internal.id(),
                internal.subject(),
                internal.predictedValue(),
                new Confidence(internal.confidence(),
                        internal.confidence() >= 1.0
                                ? Confidence.Basis.MEASURED : Confidence.Basis.DECLARED,
                        internal.caseIds().size(), 0,
                        "the surviving hypotheses of the model",
                        List.of("the basis was " + internal.basis()
                                + " at version " + internal.basisVersion())),
                "the model's surviving hypotheses at " + internal.basisVersion(),
                basedOn,
                internal.madeAt(),
                null,
                0.0);
    }

    // ── the action, the experiment and the outcome ──────────────────────────

    private static Action action(CycleFacts facts) {
        io.matrix.cognitive.microworld.Observation observation = facts.observation();
        if (observation == null) {
            return null;
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("assignment", MicroWorldCodec.describe(observation.input()));
        return new Action("act-" + facts.episodeId(),
                Action.KIND_PROBE,
                MicroWorldStateAdapter.entityId(observation.input()),
                parameters,
                "the cognitive kernel",
                "the micro-world environment",
                io.matrix.cognitive.CognitiveKernel.DEFAULT_CYCLE_BUDGET_MILLIS,
                new Provenance(facts.signalId(), "cognitive-kernel", "the probe of the environment",
                        null, List.of("episode:" + facts.episodeId()), facts.at()));
    }

    private static io.matrix.contract.Experiment experiment(CycleFacts facts, Action action,
                                        List<Hypothesis> hypotheses, String at,
                                        Provenance provenance) {
        io.matrix.cognitive.microworld.Experiment internal = facts.experiment();
        if (internal == null) {
            return null;
        }
        List<String> hypothesisIds = new ArrayList<>();
        for (Hypothesis hypothesis : hypotheses) {
            hypothesisIds.add(hypothesis.id());
        }
        return new io.matrix.contract.Experiment(internal.id(),
                action,
                hypothesisIds,
                "the lamp's state for the assignment "
                        + MicroWorldCodec.describe(internal.input()),
                internal.expectedInformationGain(),
                io.matrix.contract.Experiment.Status.COMPLETED,
                null,
                internal.rationale(),
                provenance);
    }

    private static Observation observation(CycleFacts facts, Provenance provenance) {
        io.matrix.cognitive.microworld.Observation internal = facts.observation();
        if (internal == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assignment", MicroWorldCodec.describe(internal.input()));
        payload.put("lamp", internal.lamp());
        payload.put("adversarial", internal.adversarial());
        payload.put("disposition", internal.disposition() == null
                ? null : internal.disposition().name());
        return new Observation(internal.id(),
                Observation.Modality.STRUCTURED,
                payload,
                Map.of("lamp", internal.lamp()),
                internal.observedAt(),
                provenance,
                null,
                List.of("micro-world"));
    }

    private static Outcome outcome(CycleFacts facts, io.matrix.contract.Prediction prediction,
                                   Observation observation, String at) {
        if (observation == null) {
            return null;
        }
        Boolean matched = null;
        Double error = null;
        String reason = "the environment answered and the model was compared with it";
        if (prediction != null) {
            String expected = prediction.expectedOutcome();
            String observed = String.valueOf(observation.payload().get("lamp"));
            matched = expected.equals(observed);
            error = matched ? 0.0 : 1.0;
            reason = matched
                    ? "the observation matched the prediction"
                    : "the observation disagreed with the prediction";
        }
        return new Outcome("out-" + facts.episodeId(),
                "act-" + facts.episodeId(),
                observation.id(),
                prediction == null ? null : prediction.id(),
                matched == null ? Outcome.Status.SUCCEEDED
                        : (matched ? Outcome.Status.SUCCEEDED : Outcome.Status.DEVIATED),
                String.valueOf(observation.payload().get("lamp")),
                matched,
                error,
                reason,
                at);
    }

    // ── the evidence and the belief ─────────────────────────────────────────

    private static List<Evidence> evidences(CycleFacts facts, String at) {
        List<Evidence> result = new ArrayList<>();
        io.matrix.cognitive.microworld.Observation observation = facts.observation();
        if (observation == null) {
            return result;
        }
        ErrorSignal error = facts.error();
        Evidence.Relation relation = error == null
                ? Evidence.Relation.SUPPORTS : Evidence.Relation.CONTRADICTS;
        result.add(new Evidence("evd-" + facts.episodeId(),
                "belief-" + facts.episodeId(),
                "micro-world-environment",
                relation,
                "the lamp was " + observation.lamp() + " for "
                        + MicroWorldCodec.describe(observation.input()),
                error == null ? 2 : 1,
                at,
                new Provenance(observation.id(), "micro-world-environment",
                        "the probe of the environment", null,
                        List.of("episode:" + facts.episodeId()), at)));
        return result;
    }

    private static Belief belief(CycleFacts facts, io.matrix.contract.Prediction prediction,
                                 List<Evidence> evidences, String at, Provenance provenance) {
        if (prediction == null) {
            return null;
        }
        List<String> supporting = new ArrayList<>();
        List<String> contradicting = new ArrayList<>();
        for (Evidence evidence : evidences) {
            if (evidence.relation() == Evidence.Relation.SUPPORTS) {
                supporting.add(evidence.id());
            } else {
                contradicting.add(evidence.id());
            }
        }
        BeliefStatus status = contradicting.isEmpty() ? BeliefStatus.SUPPORTED
                : (supporting.isEmpty() ? BeliefStatus.CONTESTED : BeliefStatus.CONTESTED);
        return new Belief("belief-" + facts.episodeId(),
                "the lamp's state for " + prediction.subjectId(),
                status,
                new Confidence(prediction.confidence().value(),
                        Confidence.Basis.MEASURED,
                        supporting.size(), contradicting.size(),
                        "the cases the model was measured against",
                        List.of("the belief rests on the observation the cycle recorded")),
                supporting,
                contradicting,
                provenance,
                validity(facts, at),
                1,
                null,
                null,
                "the model's account of the world, as the cycle found it");
    }

    // ── the event, the algorithm and the procedure ──────────────────────────

    private static Event event(CycleFacts facts, String at, Provenance provenance) {
        LearningTrace trace = facts.trace();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("episodeId", facts.episodeId());
        payload.put("channel", facts.channel() == null ? null : facts.channel().name());
        if (trace != null) {
            List<String> phases = new ArrayList<>();
            for (LearningTrace.Step step : trace.steps()) {
                phases.add(step.phase());
            }
            payload.put("phases", phases);
        }
        return new Event("evt-" + facts.episodeId(),
                EVENT_KIND_COGNITIVE_CYCLE,
                facts.subject(),
                payload,
                at,
                correlation(facts),
                provenance);
    }

    private static Algorithm algorithm(CycleFacts facts) {
        AlgorithmDecision decision = facts.decision();
        if (decision == null) {
            return null;
        }
        return new Algorithm(decision.selectedAlgorithmId(),
                1,
                decision.problemFamily(),
                decision.reason(),
                List.of(),
                0.0,
                0,
                Algorithm.CostBasis.DECLARED,
                "the declared complexity of the method; the measured cost is recorded in the "
                        + "ledger and is not a property of the algorithm itself",
                decision.fallbackAlgorithmId(),
                null);
    }

    private static Procedure procedure(CycleFacts facts, String at, Provenance provenance) {
        io.matrix.cognitive.algorithms.Procedure internal = facts.procedure();
        if (internal == null) {
            return null;
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("problemFamily", internal.problemFamily() == null
                ? "" : internal.problemFamily());
        parameters.put("referenceAlgorithmId", internal.referenceAlgorithmId() == null
                ? "" : internal.referenceAlgorithmId());
        parameters.put("validationKind", internal.validationKind() == null
                ? "UNKNOWN" : internal.validationKind().name());
        parameters.put("validationCases", Integer.toString(internal.validationCases()));
        parameters.put("validationAgreement", Double.toString(internal.validationAgreement()));
        return new Procedure(internal.id(),
                1,
                internal.name(),
                List.of(internal.referenceAlgorithmId() == null
                        ? "run the method" : "run " + internal.referenceAlgorithmId()),
                internal.requiredFacts() == null
                        ? List.of() : List.copyOf(internal.requiredFacts()),
                List.of("the method answered the request"),
                parameters,
                "the cognitive kernel",
                provenance);
    }

    private static Skill skill(CycleFacts facts, Procedure procedure, String at,
                                Provenance provenance) {
        io.matrix.cognitive.algorithms.Procedure internal = facts.procedure();
        if (internal == null || procedure == null) {
            return null;
        }
        // A skill is a procedure that has been VALIDATED and may be reused. The record
        // says the truth either way: the internal procedure knows whether it rests on
        // independent evidence, and a procedure that does not is NOT marked reusable —
        // the micro-world's fast path is an in-sample shortcut, and the W45 finding says
        // so, and the canonical record must say it too.
        return new Skill("skill-" + internal.id(),
                internal.name(),
                procedure.id(),
                List.of(),
                internal.useCount(),
                Math.max(0, internal.useCount() - internal.failureCount()),
                internal.validated(),
                List.of(facts.episodeId()),
                1,
                provenance);
    }

    /**
     * The evaluation of the cycle.
     *
     * <p>It is the reflection's own evaluation, not a second one: the mapper reuses
     * {@code CycleReflection.evaluationOf} rather than rebuilding the metrics, so that the
     * numbers the cycle reports and the numbers the canonical record carries cannot
     * drift apart.</p>
     */
    private static Evaluation evaluation(CycleFacts facts) {
        Reflection reflection = facts.reflection();
        if (reflection == null) {
            return null;
        }
        return io.matrix.cognitive.reflection.CycleReflection.evaluationOf(
                facts.episodeId(), reflection, facts.error(),
                reflection.learned() == null ? 0 : reflection.learned().size(),
                facts.at());
    }

    // ── the validity, the correlation, the capability and the confidence ─────

    private static ValidityInterval validity(CycleFacts facts, String at) {
        return ValidityInterval.openFrom(at);
    }

    private static Correlation correlation(CycleFacts facts) {
        return new Correlation("corr-" + facts.episodeId(),
                "cause-" + facts.episodeId(),
                facts.episodeId(),
                facts.signalId());
    }

    private static Capability capability(CycleFacts facts) {
        if (facts.capabilityId() == null) {
            return null;
        }
        return new Capability(facts.capabilityId(),
                "the method the cycle selected",
                "io.matrix.cognitive.algorithms.AlgorithmMethod",
                "the method that ran inside the cycle",
                List.of("the method refused", "the method ran out of budget"),
                true);
    }

    private static Confidence confidence(CycleFacts facts,
                                         io.matrix.contract.Prediction prediction) {
        if (prediction == null) {
            return null;
        }
        return prediction.confidence();
    }

    /**
     * The relation the world state carries between an assignment and the lamp.
     *
     * @param world the world-state memory
     * @return the relations, or an empty list when there is no world state
     */
    public static List<Relation> relations(WorldStateMemory world) {
        if (world == null || world.state() == null) {
            return List.of();
        }
        return world.state().relations();
    }

    /**
     * The description of an assignment, in the runtime's own words.
     *
     * @param input the assignment
     * @return the description
     */
    public static String describe(WorldInput input) {
        return MicroWorldCodec.describe(input);
    }
}
