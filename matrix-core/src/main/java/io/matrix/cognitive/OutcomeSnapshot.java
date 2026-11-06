package io.matrix.cognitive;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * W45.0 — the durable form of the outcome of a cycle.
 *
 * <p>When a caller delivers the same request twice under one idempotency key, the second
 * delivery must return the ORIGINAL outcome and must not do any work again. Returning "the
 * outcome" therefore means returning the SAME outcome, with the same status, the same answer,
 * the same prediction, the same error, the same decision and the same trace — not an
 * approximation of it, and not an empty shell.</p>
 *
 * <p>The snapshot is that outcome in a form that can be written into a column and read back
 * after a restart. The store keeps it as JSON, exactly as the knowledge store keeps its own
 * JSON-valued columns: the encoding is an implementation detail of the registry, and nothing
 * else in the stack reads it.</p>
 *
 * <p>The codec is deliberately small and total: an outcome that cannot be encoded is an error,
 * never a silently empty outcome. A caller that receives a replayed outcome must be able to
 * trust it as much as the original.</p>
 */
public final class OutcomeSnapshot {

    private static final ObjectMapper JSON = new ObjectMapper();

    private OutcomeSnapshot() {
    }

    /**
     * Encodes an outcome.
     *
     * @param outcome the outcome of a cycle
     * @return the durable form of the outcome
     */
    public static String encode(CognitiveOutcome outcome) {
        try {
            return JSON.writeValueAsString(outcome);
        } catch (Exception e) {
            throw new IllegalStateException("failed to encode the outcome of episode "
                    + (outcome == null ? "?" : outcome.episodeId()), e);
        }
    }

    /**
     * Decodes an outcome.
     *
     * @param snapshot the durable form of an outcome
     * @return the outcome
     */
    public static CognitiveOutcome decode(String snapshot) {
        try {
            return JSON.readValue(snapshot, CognitiveOutcome.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to decode a stored outcome", e);
        }
    }
}
