package io.matrix.cognitive;

import java.util.List;

/**
 * W44.1 — a prediction error: what the system expected, what it observed, and who was responsible.
 *
 * <p>The W44 directive requires an explicit error signal WITH PROVENANCE. This record names the
 * model that made the wrong prediction, its version, and the cases that decided the error — so
 * the failure can be attributed rather than absorbed.</p>
 *
 * <p>The disposition is the honest part. A prediction error is not automatically a model failure:
 * the sensor may have been wrong, a condition may have been missing, or the model may be wrong.
 * The first two are recorded as {@code EXTERNAL} and {@code MISSING_CONDITION}; only
 * {@code MODEL_DEFECT} claims the model was at fault, and it never rewrites the previous
 * prediction or deletes the evidence that produced it.</p>
 */
public record ErrorSignal(
        String id,
        String workspaceId,
        String predictionId,
        String expected,
        String observed,
        double magnitude,
        Disposition disposition,
        String responsibleModel,
        String responsibleVersion,
        List<String> caseIds,
        String detectedAt) {

    /** What the system concluded about the cause of the error. */
    public enum Disposition {
        /** The model is the suspect: its prediction was wrong and nothing else explains it. */
        MODEL_DEFECT,
        /** The reading may be wrong: the observation is not trusted as-is. */
        EXTERNAL,
        /** A condition the model did not know about may explain the difference. */
        MISSING_CONDITION,
        /** Not yet decided: more evidence is required. */
        UNRESOLVED
    }

    public ErrorSignal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("error id must not be blank");
        }
        if (predictionId == null || predictionId.isBlank()) {
            throw new IllegalArgumentException("error prediction id must not be blank");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("error disposition must not be null");
        }
        caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
    }

    /** True when the error is still open — no cause has been established yet. */
    public boolean open() {
        return disposition == Disposition.UNRESOLVED;
    }

    @Override
    public String toString() {
        return "ErrorSignal[" + id + " prediction=" + predictionId + " expected=" + expected
                + " observed=" + observed + " disposition=" + disposition + "]";
    }
}
