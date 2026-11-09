package io.matrix.cognitive.worldstate;

import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Provenance;
import io.matrix.contract.StateTransition;
import io.matrix.cognitive.MicroWorldCodec;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 §4 — the micro-world's observations, expressed as facts about a world.
 *
 * <p><b>What this is.</b> The micro-world has two input variables and one outcome
 * variable, and a probe answers one question: under this assignment, was the lamp on or
 * off? The world state of that domain is therefore the set of assignments the system has
 * probed and what it saw, and the adapter is what turns a durable observation into that
 * shape.</p>
 *
 * <p><b>Why an assignment is an entity.</b> Each assignment is a distinct situation of the
 * world — "power off and switch on" is not the same situation as "power on and switch on" —
 * and the property that holds in it is whether the lamp was lit. Modelling it any other way
 * would either lose the context of the outcome or claim that the world has one lamp state
 * when it has four.</p>
 *
 * <p><b>What is NOT here.</b> The adapter does not decide what the world IS. It reports what
 * was observed and leaves the interpretation to the session, which already owns the
 * hypotheses. The world-state memory and the hypothesis space are two different things: one
 * is what the system saw, the other is what it thinks the rule is.</p>
 */
public final class MicroWorldStateAdapter {

    /** The domain the micro-world describes. */
    public static final String ENVIRONMENT_ID = "micro-world.lamp";

    /** The type of the entities the adapter produces. */
    public static final String ENTITY_TYPE = "micro-world.assignment";

    /** The property that holds whether the lamp was lit. */
    public static final String PROPERTY_LAMP = "lamp";

    /** The prefix of the entities the adapter produces. */
    public static final String ENTITY_ID_PREFIX = "assign";

    /** The identity of the lamp the micro-world has. */
    public static final String LAMP_ENTITY_ID = "micro-world.lamp";

    /** The type of the lamp the micro-world has. */
    public static final String LAMP_ENTITY_TYPE = "micro-world.lamp";

    /** The connection an assignment has with the lamp. */
    public static final String RELATION_DETERMINES = "determines";

    private MicroWorldStateAdapter() {
        throw new AssertionError("no instances");
    }

    /**
     * The identity of the entity that holds an assignment.
     *
     * @param input the assignment
     * @return the identity
     */
    public static String entityId(WorldInput input) {
        return ENTITY_ID_PREFIX + ":" + MicroWorldCodec.describe(input);
    }

    /**
     * The fact an observation states.
     *
     * @param observation the observation
     * @param cause       what caused the change: an action when the system probed, an
     *                    observation when the system merely read what it had recorded
     * @param actionId    the action that caused it, when the cause was an action
     * @return the fact
     */
    public static StateFact fact(Observation observation, StateTransition.Cause cause,
                                 String actionId) {
        if (observation == null) {
            throw new IllegalArgumentException("an observation must not be null");
        }
        if (cause == null) {
            throw new IllegalArgumentException("a fact must name its cause");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(PROPERTY_LAMP, observation.lamp());
        StateFact fact = new StateFact(
                observation.id(),
                entityId(observation.input()),
                ENTITY_TYPE,
                MicroWorldCodec.describe(observation.input()),
                properties,
                cause,
                actionId,
                BeliefStatus.OBSERVED,
                observation.observedAt(),
                // The DOMAIN knows that the assignment determines the lamp; the system does
                // not know it until the cases say so. The connection is asserted here and
                // the memory records it with the system's own evidence.
                new StateRelation(RELATION_DETERMINES, LAMP_ENTITY_ID, LAMP_ENTITY_TYPE, true));
        // A FAULT-INJECTING probe answers inconsistently on purpose: the answer is a fact
        // about the INSTRUMENT, not about the world. The fact is recorded, because the
        // answer happened, but it does not establish anything about the world.
        return observation.adversarial() ? fact.withStatus(BeliefStatus.CONTESTED) : fact;
    }

    /**
     * The facts that a list of durable observations states.
     *
     * <p>Each observation is an OBSERVATION-caused fact: the system read what it had
     * recorded, it did not cause it now. The observations it caused itself are applied
     * separately, at the moment they happen, so that the transition names the action that
     * caused it.</p>
     *
     * @param observations the durable observations, oldest first
     * @return the facts
     */
    public static List<StateFact> facts(List<Observation> observations) {
        List<StateFact> facts = new ArrayList<>();
        if (observations != null) {
            for (Observation observation : observations) {
                facts.add(fact(observation, StateTransition.Cause.OBSERVATION, null));
            }
        }
        return facts;
    }

    /**
     * A memory rebuilt from the durable observations.
     *
     * @param workspaceId  the workspace the entities belong to
     * @param provenance   where the state's identity comes from
     * @param observations the durable observations, oldest first
     * @return the memory
     */
    public static WorldStateMemory replay(String workspaceId, Provenance provenance,
                                           List<Observation> observations) {
        return WorldStateMemory.replay(ENVIRONMENT_ID, workspaceId, provenance,
                facts(observations));
    }
}
