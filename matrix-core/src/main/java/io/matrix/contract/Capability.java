package io.matrix.contract;

import java.util.List;

/**
 * W51 — a named ability a component of the runtime offers.
 *
 * <p><b>What this replaces.</b> Nothing. The repository has the word "capability" in several
 * unrelated senses — a cognitive capability level, a device ability, a protobuf field — and
 * none of them says what a component of the running system can actually do. The W51
 * requirement asks for "capability checks" as part of the contract machinery, and this is the
 * thing that is checked.</p>
 *
 * <p><b>A capability is a promise about behaviour, not a label.</b> Each capability names the
 * contract the component speaks when it offers it, the input it accepts, and the failure it
 * produces when it cannot satisfy the request. A component that cannot say what it does when
 * it cannot do something has not declared a capability.</p>
 *
 * <p><b>The check is done against the declaration, not against the object.</b> The runtime
 * asks a component whether it holds a capability; it does not ask whether the component
 * happens to have a method of a certain name. That is what makes a refusal explainable: the
 * runtime can say which capability was missing.</p>
 *
 * @param id          the identity of the capability
 * @param component   the component that offers it
 * @param contract    the contract the component speaks when it offers this capability
 * @param description what the capability does, in one honest sentence
 * @param failures    the failures the component produces for this capability
 * @param sideEffectFree whether using the capability changes anything outside the component
 */
public record Capability(
        String id,
        String component,
        String contract,
        String description,
        List<String> failures,
        boolean sideEffectFree) {

    public Capability {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("capability id must not be blank");
        }
        if (component == null || component.isBlank()) {
            throw new IllegalArgumentException("capability component must not be blank");
        }
        if (contract == null || contract.isBlank()) {
            throw new IllegalArgumentException("capability contract must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("capability description must not be blank");
        }
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    /** True when using the capability can change something outside the component. */
    public boolean mutating() {
        return !sideEffectFree;
    }

    @Override
    public String toString() {
        return "Capability[" + id + " of " + component + "]";
    }
}
