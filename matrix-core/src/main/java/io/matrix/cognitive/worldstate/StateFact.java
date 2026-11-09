package io.matrix.cognitive.worldstate;

import io.matrix.contract.BeliefStatus;
import io.matrix.contract.StateTransition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W51 §4 — one durable fact about the world, in the shape the world-state memory applies.
 *
 * <p><b>What this is.</b> The world-state memory keeps the system's estimate of a domain.
 * Something has to tell it what it learned; this record is that something. It says which
 * entity the fact is about, which properties of it hold now, what caused the change, and
 * when it happened.</p>
 *
 * <p><b>Why a separate record and not the canonical {@link io.matrix.contract.Observation}.</b>
 * An observation is what an instrument reported. A fact about the world is what the system
 * concluded from the observation and from everything it already knew — and the difference
 * matters for the memory: the same observation can be applied twice, and the second
 * application must not change the state, because nothing about the world changed.</p>
 *
 * @param id         the identity of the fact
 * @param entityId   the entity the fact is about
 * @param entityType the type of that entity, from the system's own vocabulary
 * @param entityName a human-readable label, or {@code null}
 * @param properties the properties of the entity that this fact asserts
 * @param cause      what caused the change
 * @param actionId   the action that caused it, when the cause was an action
 * @param status     how much the system believes the fact
 * @param at         when it happened
 * @param relation   the connection the fact asserts, or {@code null} when it asserts none
 */
public record StateFact(
        String id,
        String entityId,
        String entityType,
        String entityName,
        Map<String, Object> properties,
        StateTransition.Cause cause,
        String actionId,
        BeliefStatus status,
        String at,
        StateRelation relation) {

    public StateFact {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a fact must have an identity");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("a fact must name the entity it is about");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("a fact must name the type of its entity");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("a fact must assert at least one property");
        }
        if (cause == null) {
            throw new IllegalArgumentException("a fact must name its cause");
        }
        if (status == null) {
            throw new IllegalArgumentException("a fact must name how much it is believed");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("a property name must not be blank");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("the property '" + entry.getKey()
                        + "' must have a value; null is not a state of the world");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        properties = Map.copyOf(copy);
    }

    /**
     * A fact caused by something the system observed.
     *
     * @param id         the identity of the fact
     * @param entityId   the entity the fact is about
     * @param entityType the type of that entity
     * @param entityName a human-readable label, or {@code null}
     * @param properties the properties the fact asserts
     * @param at         when it happened
     * @return the fact
     */
    public static StateFact observed(String id, String entityId, String entityType,
                                     String entityName, Map<String, Object> properties, String at) {
        return new StateFact(id, entityId, entityType, entityName, properties,
                StateTransition.Cause.OBSERVATION, null, BeliefStatus.OBSERVED, at, null);
    }

    /**
     * A fact caused by something the system did.
     *
     * @param id         the identity of the fact
     * @param entityId   the entity the fact is about
     * @param entityType the type of that entity
     * @param entityName a human-readable label, or {@code null}
     * @param properties the properties the fact asserts
     * @param actionId   the action that caused it
     * @param at         when it happened
     * @return the fact
     */
    public static StateFact acted(String id, String entityId, String entityType,
                                  String entityName, Map<String, Object> properties,
                                  String actionId, String at) {
        return new StateFact(id, entityId, entityType, entityName, properties,
                StateTransition.Cause.ACTION, actionId, BeliefStatus.OBSERVED, at, null);
    }

    /**
     * The same fact, believed less.
     *
     * <p>This is what a FAULTY INSTRUMENT produces: the world was asked, the instrument
     * answered, and the answer cannot be taken at face value. The fact is recorded — the
     * answer happened — but its status says that it does not establish anything.</p>
     *
     * @param status how much the system believes the fact
     * @return the fact
     */
    public StateFact withStatus(BeliefStatus newStatus) {
        return new StateFact(id, entityId, entityType, entityName, properties,
                cause, actionId, newStatus, at, relation);
    }

    /**
     * The same fact, with a connection asserted as well.
     *
     * <p>W52. A fact may say that the entity it is about is connected to another one — the
     * micro-world's assignment is connected to the lamp it determines. The connection is
     * asserted by the DOMAIN, and the memory records it with its own bookkeeping: the
     * evidence, the count and the confidence are the memory's, not the domain's.</p>
     *
     * @param newRelation the connection the fact asserts
     * @return the fact
     */
    public StateFact withRelation(StateRelation newRelation) {
        return new StateFact(id, entityId, entityType, entityName, properties,
                cause, actionId, status, at, newRelation);
    }
}
