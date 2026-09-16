package io.matrix.cognitive;

import java.util.Objects;

/**
 * W93 — CognitiveError: concrete failure record (DESIGN-64).
 *
 * <p>Captures one specific way the brain failed at a task. The error
 * signature is deterministic (depends on inputs + state), so re-running
 * with the same state produces the same error record.
 *
 * <p>NOT a generic exception — it is a structured signal that feeds back
 * into learning updates.
 */
public record CognitiveError(
        long cycleCount,
        ErrorKind kind,
        float[] stateSnapshot,
        String description) {

    public CognitiveError {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stateSnapshot, "stateSnapshot");
        Objects.requireNonNull(description, "description");
    }

    /** Categories of cognitive failure tracked by MATRIX. */
    public enum ErrorKind {
        /** Observation didn't match prediction. */
        PREDICTION_ERROR,
        /** Alternative paths explored diverged beyond threshold. */
        EXPLORATION_DIVERGENCE,
        /** Integration metric Φ dropped below configured floor. */
        INTEGRATION_VIOLATION,
        /** Couldn't recall expected context from HDC memory. */
        MEMORY_RETRIEVAL_MISS,
        /** Action did not reduce surprise (no-op signal). */
        ACTION_INEFFECTIVE
    }

    /**
     * Deterministic hash of this error — same inputs produce same hash.
     * Used for deduplication in CognitiveErrorStream.
     */
    public long deterministicHash() {
        long h = 1469598103934665603L;  // FNV-1a offset basis
        h = (h ^ cycleCount) * 1099511628211L;
        h = (h ^ kind.ordinal()) * 1099511628211L;
        for (float v : stateSnapshot) {
            int bits = Float.floatToRawIntBits(v);
            h = (h ^ bits) * 1099511628211L;
        }
        for (int i = 0; i < description.length(); i++) {
            h = (h ^ description.charAt(i)) * 1099511628211L;
        }
        return h;
    }
}
