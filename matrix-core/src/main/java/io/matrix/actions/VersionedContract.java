package io.matrix.actions;

/**
 * Versioned action contract with atomic swap discipline (DESIGN-13 §3).
 *
 * <p>A contract upgrade is legal only when the domain does not change
 * ({@code domainHash} equal) and the version advances by exactly one —
 * a conservative stand-in for full BDD bisimulation until EXP-grade
 * equivalence checks land (DESIGN-13 §7 open question).
 */
public record VersionedContract(String name, int version, String domainHash) {

    public VersionedContract {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be ≥ 1");
        }
        if (domainHash == null || domainHash.isBlank()) {
            throw new IllegalArgumentException("domainHash must not be blank");
        }
    }

    /**
     * Atomic swap to the next version.
     *
     * @throws IllegalArgumentException on domain change or non-consecutive version
     */
    public VersionedContract swap(VersionedContract next) {
        java.util.Objects.requireNonNull(next, "next");
        if (!next.name.equals(name)) {
            throw new IllegalArgumentException("swap must keep action name");
        }
        if (!next.domainHash.equals(domainHash)) {
            throw new IllegalArgumentException("swap must keep domainHash");
        }
        if (next.version != version + 1) {
            throw new IllegalArgumentException(
                    "version must advance by exactly 1: " + version + " -> " + next.version);
        }
        return next;
    }
}
