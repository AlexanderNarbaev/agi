package io.matrix.contract;

/**
 * W51 — the interval during which a record is the current account.
 *
 * <p><b>What this replaces.</b> Nothing: no record in the repository carried a validity
 * interval, and the W50 audit named the consequence — "revision decisions could supersede
 * claims incorrectly", because a supersession with no interval cannot be checked against the
 * fact that a claim was true only under a condition. A belief whose scope is "while the plate is
 * moving westward" and one whose scope is "always" are different records, and the difference is
 * the interval.</p>
 *
 * <p><b>The interval is half-open and it says so.</b> {@code from} is inclusive and {@code to}
 * is exclusive, so two intervals that meet at a boundary do not overlap. An open interval
 * ({@code to == null}) means "still current", which is a different fact from "valid until
 * never" and is not the same as "no interval at all".</p>
 *
 * <p><b>Determinism.</b> The instants are strings on purpose. The repository's records carry
 * ISO-8601 instants as strings, and a record that compares two of them must not depend on a
 * clock reading or on a zone database.</p>
 *
 * @param from the inclusive start of the interval, or {@code null} for "since the beginning"
 * @param to   the exclusive end of the interval, or {@code null} for "still current"
 */
public record ValidityInterval(String from, String to) {

    /** An interval that began when the record was made and has not ended. */
    public static ValidityInterval openFrom(String from) {
        return new ValidityInterval(from, null);
    }

    /** An interval with no beginning and no end: the record has no time dimension. */
    public static final ValidityInterval UNBOUNDED = new ValidityInterval(null, null);

    public ValidityInterval {
        if (from != null && from.isBlank()) {
            throw new IllegalArgumentException("validity start must not be blank");
        }
        if (to != null && to.isBlank()) {
            throw new IllegalArgumentException("validity end must not be blank");
        }
    }

    /** True when the interval has no end, which means "still current". */
    public boolean current() {
        return to == null;
    }

    /** True when the interval has neither a start nor an end. */
    public boolean unbounded() {
        return from == null && to == null;
    }

    @Override
    public String toString() {
        return "ValidityInterval[" + (from == null ? "-inf" : from) + ", "
                + (to == null ? "+inf" : to) + ")";
    }
}
