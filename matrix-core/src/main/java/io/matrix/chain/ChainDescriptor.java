package io.matrix.chain;

import io.matrix.imports.BooleanChainRunner;

import java.util.Set;

/**
 * DESIGN-21 — Description of a single registered chain.
 *
 * <p>Used by {@link ChainRegistry} to track chains, their dependencies,
 * and activation conditions.
 */
public record ChainDescriptor(
        ChainId id,
        String name,
        BooleanChainRunner chain,
        Set<ChainId> dependsOn,
        int priority
) {
    public ChainDescriptor {
        if (id == null) throw new IllegalArgumentException("id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (chain == null) throw new IllegalArgumentException("chain");
        dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    }

    /** Builder helper. */
    public static ChainDescriptor of(String name, BooleanChainRunner chain, int priority) {
        return new ChainDescriptor(ChainId.of(name), name, chain, Set.of(), priority);
    }
}
