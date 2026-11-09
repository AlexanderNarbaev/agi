package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — something the system does to the world.
 *
 * <p><b>An action is a request, not an event.</b> It names what is to be done, under which
 * authority, and with which budget. What actually happened is an {@link Outcome}. The
 * separation is what lets a system say "the action was refused" and "the action was performed
 * and had no effect" — two different facts that a single record would merge.</p>
 *
 * <p><b>The authority is named.</b> {@link #authorisedBy()} names the goal or the task under
 * which the action is permitted. The W51 requirement that a task's allow-list is an
 * allow-list — not a description — is enforced by the runtime against this field: an action
 * whose authority does not cover it is not executed.</p>
 *
 * <p><b>The capability is named.</b> {@link #requiredCapability()} says what the action needs
 * in order to be allowed to run at all. It is checked before the action runs, not after.</p>
 *
 * @param id                 the identity of the action
 * @param kind               the kind of action
 * @param target             what the action acts on
 * @param parameters         the typed parameters of the action
 * @param authorisedBy       the identifier of the goal or task that permits this action
 * @param requiredCapability the capability the action needs
 * @param budgetMillis       the time the action may take
 * @param provenance         where the action came from
 */
public record Action(
        String id,
        String kind,
        String target,
        Map<String, Object> parameters,
        String authorisedBy,
        String requiredCapability,
        long budgetMillis,
        Provenance provenance) {

    /** The kinds of action the contract names. */
    public static final String KIND_OBSERVE = "OBSERVE";
    /** The kind of an action that changes the environment. */
    public static final String KIND_PROBE = "PROBE";
    /** The kind of an action that acts inside the system's own state. */
    public static final String KIND_INTERNAL = "INTERNAL";
    /** The kind of an action that reaches outside the process. */
    public static final String KIND_EXTERNAL = "EXTERNAL";

    public Action {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("action id must not be blank");
        }
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("action kind must not be blank");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (budgetMillis < 0L) {
            throw new IllegalArgumentException("action budget must not be negative");
        }
    }

    /**
     * An observation action against a target.
     *
     * @param id      the identity of the action
     * @param target  what to observe
     * @param budgetMillis the time the action may take
     * @return the action
     */
    public static Action observe(String id, String target, long budgetMillis) {
        return new Action(id, KIND_OBSERVE, target, Map.of(), null, null, budgetMillis, null);
    }

    /**
     * A probe action against a target.
     *
     * @param id      the identity of the action
     * @param target  what to probe
     * @param parameters the parameters of the probe
     * @param budgetMillis the time the action may take
     * @return the action
     */
    public static Action probe(String id, String target, Map<String, Object> parameters,
                               long budgetMillis) {
        return new Action(id, KIND_PROBE, target, parameters, null, null, budgetMillis, null);
    }

    /** True when the action reaches outside the process. */
    public boolean external() {
        return KIND_EXTERNAL.equals(kind);
    }

    /**
     * The action under a named authority.
     *
     * @param goalOrTask the identifier of the goal or task that permits the action
     * @param capability the capability the action needs
     * @return the authorised action
     */
    public Action authorisedBy(String goalOrTask, String capability) {
        return new Action(id, kind, target, parameters, goalOrTask, capability, budgetMillis,
                provenance);
    }

    @Override
    public String toString() {
        return "Action[" + id + " " + kind + " " + target + "]";
    }
}
