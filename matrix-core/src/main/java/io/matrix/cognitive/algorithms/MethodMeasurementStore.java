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
     * The measurements of a workspace, keyed by method.
     *
     * @param workspaceId the workspace
     * @return the measurements, never null
     */
    List<CostLedger.Measurement> measurements(String workspaceId);
}
