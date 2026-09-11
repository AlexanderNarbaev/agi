package io.matrix.chain;

import java.util.Objects;
import java.util.UUID;

/**
 * DESIGN-21 — Unique identifier for a registered chain in
 * {@link ChainRegistry}. Wraps UUID for type safety and human-readable
 * short name.
 */
public record ChainId(UUID uuid, String name) {
    public ChainId {
        Objects.requireNonNull(uuid, "uuid");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** Factory: generates a new id with the given short name. */
    public static ChainId of(String name) {
        return new ChainId(UUID.randomUUID(), name);
    }

    /** Factory: re-hydrates from a UUID + name (for serialization). */
    public static ChainId of(UUID uuid, String name) {
        return new ChainId(uuid, name);
    }

    @Override
    public String toString() {
        return "ChainId[" + name + ":" + uuid.toString().substring(0, 8) + "]";
    }
}
