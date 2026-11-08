package io.matrix.contract;

import java.util.List;
import java.util.Map;

/**
 * W51 — a reusable sequence of steps, with the conditions under which it applies.
 *
 * <p><b>The procedure is the reusable artefact; the skill is the evidence for it.</b> The
 * algorithm portfolio already has this pair — a {@code Procedure} with a validator and a
 * store — and this record is the canonical form of it, so that a procedure can be declared by
 * a plugin, offered to the method selector, and referred to by a skill.</p>
 *
 * <p><b>The preconditions are checked, not documented.</b> {@link #preconditions()} and
 * {@link #postconditions()} are the facts that must hold before and after the procedure runs.
 * The method selector admits a method only inside its declared preconditions, and the W51
 * requirement that "a method is admitted only inside its declared preconditions" is a
 * property of the portfolio, not of the prose.</p>
 *
 * <p><b>Versioning is explicit.</b> A procedure is identified by an id AND a version, and the
 * version is what a skill points at. A procedure that changes its steps gets a new version
 * rather than overwriting the old one, so that a skill learned from version one still means
 * something after version two exists.</p>
 *
 * @param id             the identity of the procedure
 * @param version        the revision number of the procedure
 * @param name           what the procedure is called
 * @param steps          the ordered steps of the procedure
 * @param preconditions  the facts that must hold before the procedure runs
 * @param postconditions the facts that hold after it runs
 * @param parameters     the typed parameters the procedure takes
 * @param origin         what produced the procedure
 * @param provenance     where the procedure came from
 */
public record Procedure(
        String id,
        int version,
        String name,
        List<String> steps,
        List<String> preconditions,
        List<String> postconditions,
        Map<String, String> parameters,
        String origin,
        Provenance provenance) {

    public Procedure {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("procedure id must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("procedure version must be at least one");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("procedure name must not be blank");
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        postconditions = postconditions == null ? List.of() : List.copyOf(postconditions);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /** True when the procedure declares at least one step. */
    public boolean executable() {
        return !steps.isEmpty();
    }

    /** True when the procedure can be validated, which requires declared preconditions. */
    public boolean validatable() {
        return !preconditions.isEmpty();
    }

    @Override
    public String toString() {
        return "Procedure[" + id + " v" + version + " " + name + "]";
    }
}
