package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — a thing the cognitive system can talk about.
 *
 * <p><b>What this replaces.</b> The repository had three partial notions of "a thing" and none
 * of them crossed a process boundary: {@code io.matrix.knowledge.Concept} (a topic of the
 * workbench), {@code io.matrix.knowledge.KnowledgeGraphStore}'s nodes (a graph whose shape the
 * workbench owns), and the {@code WorldInput} assignments of the micro-world (variables of one
 * toy environment). None of them carried a type, an identity that survives a restart, or a
 * provenance.</p>
 *
 * <p><b>An entity is not a word.</b> It is a referent: a lamp, a plate, a person, a rule. The
 * name is an attribute, not the identity — two entities may share a name and still be two
 * entities, which is exactly the case a natural-language pipeline cannot represent and a
 * structured one can. The system's canonical representation must not be a bag of tokens, and this
 * record is the reason why: it has an identity that is not derived from a label.</p>
 *
 * <p><b>The attributes are typed.</b> A value is a string, a number, a boolean, a timestamp, a
 * reference to another entity, or a list of values. Anything else is not an attribute; it is a
 * claim about the entity, and claims live in the epistemic layer where they can be disputed.</p>
 *
 * @param id          the stable identity of the entity
 * @param type        the type of the entity, from the system's own vocabulary
 * @param name        a human-readable label, or {@code null} when the entity has none
 * @param workspaceId the workspace the entity belongs to
 * @param attributes  the typed attributes of the entity
 * @param provenance  where the entity's identity came from
 */
public record Entity(
        String id,
        String type,
        String name,
        String workspaceId,
        Map<String, Object> attributes,
        Provenance provenance) {

    public Entity {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("entity id must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("entity type must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** True when the entity carries an attribute with the given name. */
    public boolean has(String attribute) {
        return attributes.containsKey(attribute);
    }

    /**
     * The value of an attribute.
     *
     * @param attribute the name of the attribute
     * @return the value, or {@code null} when the entity does not carry it
     */
    public Object attribute(String attribute) {
        return attributes.get(attribute);
    }

    /** The identifiers of the entities this one refers to, found under the given attribute. */
    @SuppressWarnings("unchecked")
    public List<String> references(String attribute) {
        Object value = attributes.get(attribute);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        if (value instanceof String single) {
            return List.of(single);
        }
        return List.of();
    }

    @Override
    public String toString() {
        return "Entity[" + id + " " + type + (name == null ? "" : " '" + name + "'") + "]";
    }
}
