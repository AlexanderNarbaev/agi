package io.matrix.runtime;

import io.matrix.contract.Capability;

import java.util.List;

/**
 * W51 — one component of the cognitive system, as the runtime knows it.
 *
 * <p><b>What this replaces.</b> Nothing declared the parts of the system in one place. The
 * capability registry knows about capabilities but not about who offers them; the chain
 * isolation gate knows the classes that exist but not what they can do; the cognitive kernel
 * wires its collaborators by hand in a constructor and has no way to say what it needs.
 * Without a single place that names the parts, "the runtime discovers or initializes the
 * available subsystems" — the second item of the W51 end-to-end acceptance list — is not a
 * thing the system can do.</p>
 *
 * <p><b>A component declares what it does, what it needs and how it is doing.</b> The three
 * are separate because they answer different questions: {@link #capabilities()} answers "what
 * can it do", {@link #requires()} answers "what must be up for it to work", and
 * {@link #health()} answers "is it working now".</p>
 *
 * <p><b>A component may live in another process.</b> {@link #remote()} says so, and the
 * runtime treats a remote component's health as reported rather than observed. That is the
 * honest form of the cross-chain integration: the runtime knows chain B offers a capability
 * because chain B said so in a handshake, and it knows when chain B stops answering because
 * the handshake has a freshness rule.</p>
 *
 * @param id           the stable identity of the component
 * @param kind         the role the component plays in the cognitive cycle
 * @param capabilities the capabilities the component offers
 * @param requires     the identifiers of the components it depends on
 * @param remote       whether the component runs in another process
 * @param health       the component's current health
 */
public record RuntimeComponent(
        String id,
        Kind kind,
        List<Capability> capabilities,
        List<String> requires,
        boolean remote,
        Health health) {

    /** The role a component plays in the cognitive cycle. */
    public enum Kind {
        /** Turns an input into a representation. */
        PERCEPTION,
        /** Holds what is known. */
        MEMORY,
        /** Reasons over what is known. */
        REASONING,
        /** Changes what is known. */
        LEARNING,
        /** Acts on the world. */
        ACTION,
        /** Evaluates what the cycle did. */
        REFLECTION,
        /** Decides what may happen. */
        GOVERNANCE,
        /** Joins two processes. */
        BRIDGE
    }

    /** How a component is doing. */
    public enum Health {
        /** The component answers and everything it needs is up. */
        UP,
        /** The component answers but something it needs is not up. */
        DEGRADED,
        /** The component does not answer. */
        DOWN,
        /** Nothing is known about the component. */
        UNKNOWN
    }

    public RuntimeComponent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("component id must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("component kind must not be null");
        }
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        requires = requires == null ? List.of() : List.copyOf(requires);
        if (health == null) {
            health = Health.UNKNOWN;
        }
    }

    /**
     * The capability with the given identifier.
     *
     * @param capabilityId the identifier of the capability
     * @return the capability, or {@code null} when the component does not offer it
     */
    public Capability capability(String capabilityId) {
        return capabilities.stream()
                .filter(c -> c.id().equals(capabilityId))
                .findFirst()
                .orElse(null);
    }

    /** True when the component offers the named capability. */
    public boolean offers(String capabilityId) {
        return capability(capabilityId) != null;
    }

    /** True when the component answers. */
    public boolean answering() {
        return health == Health.UP || health == Health.DEGRADED;
    }

    @Override
    public String toString() {
        return "RuntimeComponent[" + id + " " + kind + " " + health + "]";
    }
}
