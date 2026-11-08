package io.matrix.contract;

/**
 * W51 — the names and versions of the contracts that cross a boundary.
 *
 * <p><b>What this replaces.</b> Before W51 the two serving chains had no shared vocabulary at
 * all: chain A (the Quarkus workbench and the cognitive kernel) and chain B (the brain runtime
 * and its mind) could not call each other, because there was nothing to call with. The
 * repository had a {@code VersionedContract} record in {@code io.matrix.actions} that governed
 * an action schema, but it was a per-action discipline with no wire format behind it.</p>
 *
 * <p><b>The rule this class encodes.</b> A contract crosses a boundary only if its name and
 * version are on both sides of it. A receiver that does not recognise the version refuses the
 * message in an explicit way — it does not parse what it can and hope, and it does not ignore
 * the message. The W51 safety requirement lists "an incompatible contract version" among the
 * failures that must be handled, and this is the class that makes that failure expressible.</p>
 *
 * <p><b>Versions are integers and they advance by one.</b> The compatibility rule is: a
 * receiver accepts the version it implements and any earlier version it has a migration for.
 * A receiver that has no migration refuses. Nothing here decides what a migration is — that is
 * the subject of the compatibility strategy in the W51 ADR — but the refusal is the part that
 * must be mechanical.</p>
 */
public final class CognitiveContract {

    /** The prefix every MATRIX contract name carries. */
    public static final String NAMESPACE = "matrix.cognition";

    /** The command that runs one cognitive cycle. */
    public static final String COMMAND_COGNITIVE_CYCLE = NAMESPACE + ".cognitive-cycle";

    /** The event that reports a completed cognitive cycle. */
    public static final String EVENT_COGNITIVE_CYCLE_COMPLETED = NAMESPACE + ".cognitive-cycle-completed";

    /** The event that reports a refused cognitive cycle. */
    public static final String EVENT_COGNITIVE_CYCLE_REFUSED = NAMESPACE + ".cognitive-cycle-refused";

    /** The event that reports a belief revision. */
    public static final String EVENT_BELIEF_REVISED = NAMESPACE + ".belief-revised";

    /** The event that reports a learned skill. */
    public static final String EVENT_SKILL_LEARNED = NAMESPACE + ".skill-learned";

    /** The request that asks a peer to describe its health and its capabilities. */
    public static final String REQUEST_CAPABILITIES = NAMESPACE + ".capabilities";

    /** The version this build implements for every contract named here. */
    public static final int VERSION = 1;

    private CognitiveContract() {
        throw new AssertionError("no instances");
    }

    /**
     * Whether a receiver that implements {@code supportedVersion} can accept a message of
     * {@code offeredVersion}.
     *
     * @param offeredVersion  the version the sender speaks
     * @param supportedVersion the version the receiver speaks
     * @return true when the message may be processed
     */
    public static boolean compatible(int offeredVersion, int supportedVersion) {
        return offeredVersion >= 1 && offeredVersion <= supportedVersion;
    }
}
