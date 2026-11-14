package io.matrix.cognitive.algorithms;

import java.util.List;

/**
 * W52 — where the measurement of a method is kept, so that it outlives the process.
 *
 * <p><b>Why the ledger needs a store.</b> The ledger of measured costs is the system's
 * memory of what its own methods actually cost. While it lived in the heap, every restart
 * threw that memory away: the system forgot how fast its own methods were, and the
 * selector fell back to the DECLARED complexity — an estimate written by a person, which
 * is exactly what the measurement was supposed to replace.</p>
 *
 * <p><b>The interface is small on purpose.</b> One method to record a measurement, one to
 * read the profile back. The interface lives in the algorithms package and the
 * implementation lives with the store that owns the database, so that the durable part of
 * the ledger is written by the single writer of that database and by nobody else.</p>
 */
public interface MethodMeasurementStore {

    /**
     * Records one measured call of a method.
     *
     * @param workspaceId the workspace the measurement belongs to
     * @param methodId    the method that was run
     * @param nanos       how long it took, in nanoseconds
     * @param succeeded   whether it completed without throwing
     * @param at          the instant of the measurement
     */
    void recordMeasurement(String workspaceId, String methodId, long nanos, boolean succeeded,
                           String at);

    /**
     * Records the verdict of one call of a method, in the stratum it was observed in.
     *
     * <p>W53 — the durable profile carries the outcome kinds, not only the fact that a call
     * completed: a method that answers and is wrong must not look like a method that answers
     * and is right, and neither must look like a method that merely did not throw. The verdict
     * is keyed by the stratum, because a rate pooled over the strata answers a different
     * question from the one the selection asks.</p>
     *
     * @param workspaceId       the workspace the verdict belongs to
     * @param methodId          the method that was run
     * @param family            the family of the request it answered
     * @param capabilityVersion the version of the capability that ran
     * @param outcome           the kind of outcome the call had
     * @param at                the instant of the call
     */
    void recordVerdict(String workspaceId, String methodId, String family, String capabilityVersion,
                       MethodOutcome outcome, String at);

    /**
     * The verdicts of a workspace, read back after a restart.
     *
     * @param workspaceId the workspace
     * @return the verdicts, never null
     */
    List<MethodVerdict> verdicts(String workspaceId);

    /**
     * The measurements of a workspace, keyed by method.
     *
     * @param workspaceId the workspace
     * @return the measurements, never null
     */
    List<CostLedger.Measurement> measurements(String workspaceId);
}
