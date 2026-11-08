package io.matrix.cognitive.worldstate;

import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Confidence;
import io.matrix.contract.Entity;
import io.matrix.contract.Property;
import io.matrix.contract.Provenance;
import io.matrix.contract.StateTransition;
import io.matrix.contract.WorldState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * W51 §4 — the world-state memory: the system's estimate of a domain, and how it changed.
 *
 * <p><b>What this closes.</b> The W51 mandate names six memory roles, and the W51 audit
 * found that two of them had no carrier at all: <b>world-state memory</b> and
 * <b>meta-memory</b>. The audit said so plainly — "the role is served by a deterministic
 * function" — and this class is the honest answer for the first of the two: a memory that
 * holds the estimate, that is consulted before an action, and that records the transitions
 * which produced it.</p>
 *
 * <p><b>It is a projection, not a seventh database.</b> The mandate says "do not create six
 * redundant databases simply to represent six concepts", and this memory obeys that: it is
 * rebuilt by replaying the DURABLE, APPEND-ONLY record of what was observed — the case log
 * the cognitive store already keeps. A restart does not lose the world state, because the
 * world state was never stored separately: it is re-derived from the log that survived.</p>
 *
 * <p><b>A state is an estimate, and the estimate says so.</b> Every property it holds carries
 * a {@link BeliefStatus} and a {@link Confidence}; a property the system observed directly is
 * {@link BeliefStatus#OBSERVED}, and a property the system inferred is
 * {@link BeliefStatus#CANDIDATE}. The revision counter is what makes two states comparable:
 * two states of the same environment at the same instant are the same state, and a higher
 * revision was produced later from more or different facts.</p>
 *
 * <p><b>The transition is a diff.</b> A transition names what CHANGED, not what the new state
 * holds. A transition that carried the whole state would hide which part of the state the
 * system actually learned, and it would make the history unreadable.</p>
 *
 * <p><b>Applying the same fact twice changes nothing.</b> The world did not change, so the
 * revision does not advance and the transition says so. That is what makes the memory safe
 * to replay.</p>
 */
public final class WorldStateMemory {

    /** The prefix of the identifiers of the states this memory produces. */
    public static final String STATE_ID_PREFIX = "wst";

    /** The prefix of the identifiers of the transitions this memory produces. */
    public static final String TRANSITION_ID_PREFIX = "wtr";

    /** The confidence of a property the system observed directly. */
    private static final double OBSERVED_CONFIDENCE = 1.0;

    /** The status a property carries when the instrument that reported it is in doubt. */
    private static final BeliefStatus CONTESTED = BeliefStatus.CONTESTED;

    /** The reason a directly observed property carries its confidence. */
    private static final String OBSERVED_METHOD = "the world was observed directly";

    private final String environmentId;
    private final String workspaceId;
    private final Provenance provenance;
    private final Map<String, Entity> entities = new LinkedHashMap<>();
    private final Map<String, Property> properties = new LinkedHashMap<>();
    private final List<StateTransition> transitions = new ArrayList<>();
    private long revision;

    /**
     * Creates an empty memory of a domain.
     *
     * @param environmentId the domain the state describes
     * @param workspaceId   the workspace the entities belong to
     * @param provenance    where the state's identity comes from
     */
    public WorldStateMemory(String environmentId, String workspaceId, Provenance provenance) {
        if (environmentId == null || environmentId.isBlank()) {
            throw new IllegalArgumentException("a world-state memory must name its environment");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("a world-state memory must name its workspace");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("a world-state memory must carry its provenance");
        }
        this.environmentId = environmentId;
        this.workspaceId = workspaceId;
        this.provenance = provenance;
    }

    /**
     * Rebuilds a memory by replaying the durable facts.
     *
     * <p>This is the restart path: the log survived, so the estimate can be re-derived, and
     * the memory comes back with the same revision and the same transitions it had before
     * the restart.</p>
     *
     * @param environmentId the domain the state describes
     * @param workspaceId   the workspace the entities belong to
     * @param provenance    where the state's identity comes from
     * @param facts         the durable facts, in the order they happened
     * @return the memory
     */
    public static WorldStateMemory replay(String environmentId, String workspaceId,
                                           Provenance provenance, List<StateFact> facts) {
        WorldStateMemory memory = new WorldStateMemory(environmentId, workspaceId, provenance);
        if (facts != null) {
            for (StateFact fact : facts) {
                memory.apply(fact);
            }
        }
        return memory;
    }

    /**
     * The current estimate of the domain.
     *
     * @return the state
     */
    public WorldState state() {
        return new WorldState(
                stateId(revision),
                environmentId,
                revision,
                entities,
                List.of(),
                List.copyOf(properties.values()),
                provenance.recordedAt(),
                provenance);
    }

    /**
     * The transitions that produced the current state, oldest first.
     *
     * @return the transitions
     */
    public List<StateTransition> transitions() {
        return List.copyOf(transitions);
    }

    /** The revision of the current state. */
    public long revision() {
        return revision;
    }

    /** The domain this memory describes. */
    public String environmentId() {
        return environmentId;
    }

    /**
     * Whether the memory knows a property of an entity.
     *
     * <p>This is the question the cognitive path asks before it acts: it is what decides
     * whether a probe is a DISCOVERY or a CONFIRMATION, and it is asked of the memory rather
     * than of a list of records, so that the answer and the estimate cannot drift apart.</p>
     *
     * @param entityId     the entity
     * @param propertyName the property
     * @return true when the memory holds the property
     */
    public boolean knows(String entityId, String propertyName) {
        return valueOf(entityId, propertyName).isPresent();
    }

    /**
     * The value the memory holds for a property of an entity.
     *
     * @param entityId     the entity
     * @param propertyName the property
     * @return the value, when the memory holds it
     */
    public Optional<Object> valueOf(String entityId, String propertyName) {
        if (entityId == null || propertyName == null) {
            return Optional.empty();
        }
        Property property = properties.get(propertyKey(entityId, propertyName));
        return property == null ? Optional.empty() : Optional.ofNullable(property.value());
    }

    /**
     * Applies a fact to the memory and records the transition it caused.
     *
     * <p>Applying a fact that says what the memory already says changes nothing: the
     * revision does not advance, and the transition records no change. That is what makes
     * the replay of a log safe.</p>
     *
     * @param fact the fact
     * @return the transition it caused
     */
    public StateTransition apply(StateFact fact) {
        if (fact == null) {
            throw new IllegalArgumentException("a fact must not be null");
        }
        String fromStateId = stateId(revision);
        Entity existing = entities.get(fact.entityId());
        Entity entity = new Entity(
                fact.entityId(),
                fact.entityType(),
                fact.entityName(),
                workspaceId,
                existing == null ? Map.of() : existing.attributes(),
                provenance);
        entities.put(fact.entityId(), entity);

        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fact.properties().entrySet()) {
            String key = propertyKey(fact.entityId(), entry.getKey());
            Property current = properties.get(key);
            boolean isNew = current == null;
            boolean differs = isNew || !String.valueOf(current.value())
                    .equals(String.valueOf(entry.getValue()));
            properties.put(key, new Property(
                    key,
                    fact.entityId(),
                    entry.getKey(),
                    entry.getValue(),
                    fact.status(),
                    Confidence.measured(OBSERVED_CONFIDENCE, 1, 0, OBSERVED_METHOD, List.of()),
                    provenance,
                    null));
            if (differs) {
                changed.put(fact.entityId() + "#" + entry.getKey(), entry.getValue());
            }
        }

        if (changed.isEmpty()) {
            // The world did not change. The revision does not advance, and the transition
            // says so, so that a replay of the same log does not invent a history.
            StateTransition empty = new StateTransition(
                    transitionId(fact.id()), environmentId, fromStateId, fromStateId,
                    fact.cause(), fact.actionId(), Map.of(), List.of(), fact.at());
            transitions.add(empty);
            return empty;
        }

        revision++;
        StateTransition transition = new StateTransition(
                transitionId(fact.id()), environmentId, fromStateId, stateId(revision),
                fact.cause(), fact.actionId(), changed, List.of(), fact.at());
        transitions.add(transition);
        return transition;
    }

    /**
     * The identifier of the state with the given revision.
     *
     * @param revision the revision
     * @return the identifier
     */
    public String stateId(long revision) {
        return STATE_ID_PREFIX + "-" + environmentId + "-" + revision;
    }

    /**
     * The identifier of the transition caused by the given fact.
     *
     * @param factId the identity of the fact
     * @return the identifier
     */
    public String transitionId(String factId) {
        return TRANSITION_ID_PREFIX + "-" + factId;
    }

    private static String propertyKey(String entityId, String propertyName) {
        return entityId + "#" + propertyName;
    }

    @Override
    public String toString() {
        return "WorldStateMemory[" + environmentId + " rev=" + revision
                + " entities=" + entities.size() + " transitions=" + transitions.size() + "]";
    }
}
