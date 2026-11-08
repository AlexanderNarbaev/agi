package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — the system's estimate of the state of a domain at a moment.
 *
 * <p><b>What this replaces.</b> The repository had no world state at all. It had a snapshot of
 * a WORKSPACE ({@code io.matrix.knowledge.WorkspaceSnapshot}) — a copy of the epistemic store —
 * and the micro-world's assignments, which live inside a live object rather than in a record.
 * Neither is a world state: one is a database dump and the other is a set of booleans.</p>
 *
 * <p><b>A world state is an estimate, not the world.</b> It says what the system believes about
 * the domain, and it says how it knows. Every entity and relation it carries has a status and a
 * confidence, and the state as a whole carries the provenance of the observations it was built
 * from. A world state that is wrong is a legitimate object: it is what the system believed at a
 * moment, and comparing it with what happened next is how learning is measured.</p>
 *
 * <p><b>The revision number is what makes two states comparable.</b> Two world states of the
 * same environment at the same instant are the same state; a state with a higher revision was
 * produced later from more or different observations. The revision is a counter, not a
 * timestamp, and it never depends on a clock.</p>
 *
 * @param id            the identity of this world state
 * @param environmentId the domain the state describes
 * @param revision      the counter of how many times this environment's state was revised
 * @param entities      the entities the state holds
 * @param relations     the relations the state holds
 * @param properties    the properties the state holds
 * @param observedAt    when the observations the state was built from were made
 * @param provenance    where the state came from
 */
public record WorldState(
        String id,
        String environmentId,
        long revision,
        Map<String, Entity> entities,
        List<Relation> relations,
        List<Property> properties,
        String observedAt,
        Provenance provenance) {

    public WorldState {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("world state id must not be blank");
        }
        if (environmentId == null || environmentId.isBlank()) {
            throw new IllegalArgumentException("world state environment id must not be blank");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("world state revision must not be negative");
        }
        entities = entities == null ? Map.of() : Map.copyOf(entities);
        relations = relations == null ? List.of() : List.copyOf(relations);
        properties = properties == null ? List.of() : List.copyOf(properties);
    }

    /**
     * The entity with the given identifier.
     *
     * @param entityId the identifier to look up
     * @return the entity, or {@code null} when the state does not hold it
     */
    public Entity entity(String entityId) {
        return entities.get(entityId);
    }

    /**
     * The relations of the given type.
     *
     * @param type the type to filter by
     * @return the matching relations
     */
    public List<Relation> relationsOfType(String type) {
        return relations.stream().filter(r -> r.type().equals(type)).toList();
    }

    /**
     * The established relations of the given type.
     *
     * @param type the type to filter by
     * @return the matching relations that are established
     */
    public List<Relation> establishedRelationsOfType(String type) {
        return relationsOfType(type).stream().filter(Relation::established).toList();
    }

    /**
     * The properties of an entity, by name.
     *
     * @param entityId the entity to look up
     * @return the properties of that entity
     */
    public List<Property> propertiesOf(String entityId) {
        return properties.stream().filter(p -> p.entityId().equals(entityId)).toList();
    }

    /** The next revision of this environment's state. */
    public long nextRevision() {
        return revision + 1L;
    }

    @Override
   public String toString() {
        return "WorldState[" + environmentId + " rev=" + revision
                + " entities=" + entities.size() + " relations=" + relations.size() + "]";
    }
}
