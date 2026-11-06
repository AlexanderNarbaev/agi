package io.matrix.cognitive.algorithms;

/**
 * W44.2 — the marker for a method that is a validated procedure rather than an algorithm.
 *
 * <p>{@link AlgorithmDecision.Status#REUSED_PROCEDURE} is a distinct status because a reused
 * procedure is not the same kind of thing as a computed answer: it was validated once, against a
 * reference, on held-out cases, and it is being replayed inside the applicability domain it was
 * validated in. The trace has to be able to say which happened.</p>
 *
 * <p>The marker is an interface rather than a class check on {@code ProcedureMethod} so that the
 * selector depends on the ROLE, not on the implementation: a different kind of procedure-backed
 * method would be recognised without touching the selector.</p>
 */
public interface ProcedureBacked {

    /** The id of the procedure this method replays. */
    String procedureId();

    /** The id of the reference method the procedure was validated against, and the fallback. */
    String referenceAlgorithmId();
}
