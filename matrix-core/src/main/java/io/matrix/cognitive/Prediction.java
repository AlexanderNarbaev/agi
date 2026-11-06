package io.matrix.cognitive;

import java.util.List;

/**
 * W44.1 — a prediction the system made BEFORE the outcome was known.
 *
 * <p>This is the record that makes "a prediction" distinguishable from "an observation", which the
 * W44 directive requires explicitly. A prediction carries the method that produced it and the
 * basis it was made on; an observation carries what was actually seen. Conflating them is how a
 * system ends up citing its own guess as evidence.</p>
 *
 * <p>{@code confirmed} is deliberately NOT a field. Whether a prediction held is decided by the
 * comparison with an {@link ErrorSignal}, not by the prediction itself. A prediction that is
 * never compared stays unresolved, and that is a fact worth recording rather than hiding.</p>
 *
 * <p>{@code basis} names the model or procedure that produced the prediction, with its VERSION.
 * A prediction whose basis cannot be named is not auditable and must not be made.</p>
 */
public record Prediction(
        String id,
        String workspaceId,
        String episodeId,
        String subject,
        String predictedValue,
        String basis,
        String basisVersion,
        double confidence,
        boolean observedOnly,
        List<String> caseIds,
        String madeAt) {

    public Prediction {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("prediction id must not be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("prediction subject must not be blank");
        }
        if (basis == null || basis.isBlank()) {
            throw new IllegalArgumentException("prediction basis must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("prediction confidence must be within [0,1]");
        }
        caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
    }

    /** A prediction that was checked against an observation. */
    public static Prediction predicted(String id, String workspaceId, String episodeId, String subject,
                                       String value, String basis, String basisVersion,
                                       double confidence, String madeAt) {
        return new Prediction(id, workspaceId, episodeId, subject, value, basis, basisVersion,
                confidence, false, List.of(), madeAt);
    }
}
