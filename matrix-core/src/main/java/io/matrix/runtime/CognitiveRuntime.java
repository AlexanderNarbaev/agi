package io.matrix.runtime;

import io.matrix.contract.Capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * W51 — the composition layer that makes the parts of MATRIX one system.
 *
 * <p><b>What this replaces.</b> The repository had no such layer. Each subsystem was wired by
 * hand where it happened to be used: the cognitive kernel took its collaborators as constructor
 * arguments and had no way to say what it needed of them; the chain isolation gate knew which
 * classes existed but not what they could do; the capability registry listed capabilities
 * without saying who offered them. The consequence the W51 mission names is exact — "a
 * subsystem must not be considered integrated merely because it is registered or
 * discoverable" — and it cannot be checked without a place where the parts are declared.</p>
 *
 * <p><b>What this class is.</b> A registry of the parts of the cognitive system, their
 * capabilities, their dependencies and their health, plus the two questions a caller asks of
 * it: "who can do this?" and "is the thing I want to use working?". It is deliberately NOT a
 * service locator and NOT a bus: it holds no references to the components themselves, only to
 * their declarations, so a component can be started, stopped or replaced without the runtime
 * knowing anything about it.</p>
 *
 * <p><b>The dependency rule is enforced, not documented.</b> A component whose dependency is
 * not {@link RuntimeComponent.Health#UP} is reported as
 * {@link RuntimeComponent.Health#DEGRADED}, and {@link #require(String)} refuses to hand out
 * a component whose dependencies are down — by name, so that the caller learns what to fix.
 * This is the W51 requirement that "what happens when they fail" be part of the shared
 * runtime's knowledge rather than of each caller's discretion.</p>
 *
 * <p><b>Health of a remote component is reported, not observed.</b> The runtime cannot see
 * into another process. A remote component's health is what the peer last reported; the
 * runtime does not invent an optimism it cannot justify.</p>
 *
 * <p><b>Determinism.</b> The registry keeps its components in the order they were declared
 * and never reorders them. A report of the runtime is therefore reproducible.</p>
 */
public final class CognitiveRuntime {

    private final Map<String, RuntimeComponent> components = new LinkedHashMap<>();
    private final Map<String, Capability> capabilityIndex = new LinkedHashMap<>();

    /**
     * Declares a component.
     *
     * @param component the declaration
     * @return this runtime
     * @throws IllegalArgumentException when a component with the same identity is already
     *                                  declared with a different declaration
     */
    public CognitiveRuntime register(RuntimeComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("component must not be null");
        }
        RuntimeComponent existing = components.get(component.id());
        if (existing != null && !existing.equals(component)) {
            throw new IllegalArgumentException("the component " + component.id()
                    + " is already declared with a different declaration");
        }
        components.put(component.id(), component);
        for (Capability capability : component.capabilities()) {
            capabilityIndex.put(capability.id(), capability);
        }
        return this;
    }

    /**
     * The component with the given identity.
     *
     * @param id the identity of the component
     * @return the component, or {@code null} when it is not declared
     */
    public RuntimeComponent component(String id) {
        return components.get(id);
    }

    /**
     * The component that offers the given capability.
     *
     * @param capabilityId the identity of the capability
     * @return the offering component, or {@code null} when nobody offers it
     */
    public RuntimeComponent providerOf(String capabilityId) {
        for (RuntimeComponent component : components.values()) {
            if (component.offers(capabilityId)) {
                return component;
            }
        }
        return null;
    }

    /**
     * The capability with the given identity.
     *
     * @param capabilityId the identity of the capability
     * @return the capability, or {@code null} when nobody offers it
     */
    public Capability capability(String capabilityId) {
        return capabilityIndex.get(capabilityId);
    }

    /**
     * The component that must be used for a capability, if it is usable.
     *
     * <p>A component that is down, or that is degraded because something it needs is down,
     * is not returned: the caller is told that the capability is unavailable rather than
     * being handed something that will fail in a less explicable way.</p>
     *
     * @param capabilityId the identity of the capability
     * @return the component, when one is usable
     */
    public Optional<RuntimeComponent> usableProviderOf(String capabilityId) {
        RuntimeComponent provider = providerOf(capabilityId);
        if (provider == null) {
            return Optional.empty();
        }
        if (provider.health() != RuntimeComponent.Health.UP) {
            return Optional.empty();
        }
        if (!unmetDependencies(provider).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(provider);
    }

    /**
     * The component with the given identity, if it and everything it needs are up.
     *
     * @param id the identity of the component
     * @return the component, when it is usable
     * @throws IllegalStateException when the component is unknown or its dependencies are down
     */
    public RuntimeComponent require(String id) {
        RuntimeComponent component = components.get(id);
        if (component == null) {
            throw new IllegalStateException("the component " + id + " is not declared");
        }
        List<String> down = unmetDependencies(component);
        if (!down.isEmpty()) {
            throw new IllegalStateException("the component " + id
                    + " cannot be used: the dependency (or dependencies) "
                    + String.join(", ", down) + " are not up");
        }
        return component;
    }

    /**
     * The dependencies of a component that are not up.
     *
     * @param component the component to inspect
     * @return the identities of the dependencies that are missing or down
     */
    public List<String> unmetDependencies(RuntimeComponent component) {
        List<String> unmet = new ArrayList<>();
        for (String requirement : component.requires()) {
            RuntimeComponent dependency = components.get(requirement);
            if (dependency == null) {
                unmet.add(requirement);
            } else if (dependency.health() != RuntimeComponent.Health.UP) {
                unmet.add(requirement);
            }
        }
        return List.copyOf(unmet);
    }

    /** Every declared component, in declaration order. */
    public List<RuntimeComponent> components() {
        return List.copyOf(components.values());
    }

    /** Every declared capability, by identity. */
    public Map<String, Capability> capabilities() {
        return Map.copyOf(capabilityIndex);
    }

    /**
     * The components whose dependencies are not all up.
     *
     * @return the degraded components
     */
    public List<RuntimeComponent> degradedComponents() {
        return components.values().stream()
                .filter(c -> !unmetDependencies(c).isEmpty())
                .toList();
    }

    /**
     * A machine-readable report of what the runtime knows.
     *
     * <p>The report is the input of the W51 end-to-end demonstration: it says which subsystems
     * the runtime discovered, what each of them can do, and what it depends on.</p>
     *
     * @return the report
     */
    public Map<String, Object> describe() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("componentCount", components.size());
        report.put("capabilityCount", capabilityIndex.size());
        List<Map<String, Object>> declared = new ArrayList<>();
        for (RuntimeComponent component : components.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", component.id());
            entry.put("kind", component.kind().name());
            entry.put("health", component.health().name());
            entry.put("remote", component.remote());
            entry.put("requires", component.requires());
            entry.put("capabilities",
                    component.capabilities().stream().map(Capability::id).toList());
            entry.put("unmetDependencies", unmetDependencies(component));
            declared.add(entry);
        }
        report.put("components", List.copyOf(declared));
        return report;
    }
}
