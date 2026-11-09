package io.matrix.contract;

import java.util.List;

/**
 * W52 §3 — the canonical form of one cognitive cycle.
 *
 * <p><b>Why this record exists.</b> The mandate names twenty-two types and says they must
 * be meaningful data contracts. W51 gave them their meaning, and then measured that
 * <b>fourteen of them had no producer in the runtime</b>: they were a vocabulary the system
 * never filled, and a vocabulary nobody fills is a shape without a meaning. This record is
 * the place where the runtime fills them — the canonical trace of a cycle, in the canonical
 * vocabulary, carried by the cycle's outcome and by whatever crosses the boundary between
 * two processes.</p>
 *
 * <p><b>What a field means.</b> A field holds the record the cycle actually produced.
 * <b>A null field means the cycle did not produce that record</b> — the cycle was refused
 * before the action, no procedure was promoted, the model did not answer. A null is a fact
 * about the cycle, and it is never a placeholder for a record nobody had.</p>
 *
 * <p><b>The entities, the relations and the properties are not separate fields.</b> They
 * live inside the {@link WorldState}, because that is what a world state is: the entities
 * that exist, the relations between them and the properties they hold. The transitions the
 * cycle caused are separate, because a state is a position and a transition is a change. A reader that wants
 * the relation between an assignment and a lamp reads
 * {@code worldState().relations()}, and a reader that wants the lamp's property reads
 * {@code worldState().properties()}.</p>
 *
 * <p><b>What this record is not.</b> It is not a trace log: it does not carry the ordered
 * steps, the notes or the errors of the cycle — the learning trace does that, and it is the
 * durable record. This record is the <b>canonical</b> one: the same cycle expressed in the
 * interchange vocabulary, so that two processes, two stores and two chains can talk about
 * what happened without agreeing on an internal representation.</p>
 */
public record CognitiveCycle(
        String id,
        String episodeId,
        Goal goal,
        Task task,
        List<Hypothesis> hypotheses,
        Prediction prediction,
        Experiment experiment,
        Action action,
        Outcome outcome,
        Observation observation,
        List<Evidence> evidences,
        Belief belief,
        Event event,
        Algorithm algorithm,
        Procedure procedure,
        Skill skill,
        Evaluation evaluation,
        Reflection reflection,
        WorldState worldState,
        List<StateTransition> transitions,
        ValidityInterval validity,
        Correlation correlation,
        Capability capability,
        Confidence confidence,
        Provenance provenance) {

    public CognitiveCycle {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("cycle id must not be blank");
        }
        if (episodeId == null || episodeId.isBlank()) {
            throw new IllegalArgumentException("cycle episode id must not be blank");
        }
        hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }

    /**
     * Whether the cycle produced a record of the given kind.
     *
     * <p>A reader — or a test — that wants to know what a cycle actually produced asks
     * this question rather than reading a null. It is the method the coverage test uses to
     * assert that every canonical contract has a producer.</p>
     *
     * @param kind the name of the contract
     * @return true when the cycle carries a record of that kind
     */
    public boolean produced(String kind) {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case "Goal" -> goal != null;
            case "Task" -> task != null;
            case "Hypothesis" -> !hypotheses.isEmpty();
            case "Prediction" -> prediction != null;
            case "Experiment" -> experiment != null;
            case "Action" -> action != null;
            case "Outcome" -> outcome != null;
            case "Observation" -> observation != null;
            case "Evidence" -> !evidences.isEmpty();
            case "Belief" -> belief != null;
            case "Event" -> event != null;
            case "Algorithm" -> algorithm != null;
            case "Procedure" -> procedure != null;
            case "Skill" -> skill != null;
            case "Evaluation" -> evaluation != null;
            case "Reflection" -> reflection != null;
            case "WorldState" -> worldState != null;
            case "StateTransition" -> !transitions.isEmpty();
            case "Entity" -> worldState != null
                    && worldState.entities() != null && !worldState.entities().isEmpty();
            case "Property" -> worldState != null
                    && worldState.properties() != null && !worldState.properties().isEmpty();
            case "Relation" -> worldState != null
                    && worldState.relations() != null && !worldState.relations().isEmpty();
            case "ValidityInterval" -> validity != null;
            case "Correlation" -> correlation != null;
            case "Capability" -> capability != null;
            case "Confidence" -> confidence != null;
            case "Provenance" -> provenance != null;
            default -> false;
        };
    }
}
