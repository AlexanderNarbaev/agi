package io.matrix.plugin;

import java.util.Map;

/**
 * W51 — what a sandbox produced when it ran a plugin.
 *
 * <p><b>A refusal and a failure are different facts.</b> A refusal means the sandbox declined
 * to run the artefact at all — the isolation was not the one the artefact needs, the
 * artefact's digest did not match, the budget was exhausted before anything ran. A failure
 * means the artefact ran and the program was wrong. Collapsing them would let a caller
 * believe that a plugin was tried and failed when in fact it was never run.</p>
 *
 * <p><b>The steps are counted.</b> {@link #steps()} is the number of evaluation steps the
 * interpreter performed. It is the measure of the budget, and it is what lets a test prove
 * that a runaway program was stopped rather than that it happened to finish.</p>
 *
 * @param status the disposition of the run
 * @param output the output of the program, when it succeeded
 * @param error  the reason the run did not produce an output
 * @param steps  the number of evaluation steps performed
 */
public record SandboxResult(
        Status status,
        Map<String, Object> output,
        String error,
        int steps) {

    /** The disposition of a sandbox run. */
    public enum Status {
        /** The artefact ran and produced an output. */
        OK,
        /** The artefact ran and the program failed. */
        FAILED,
        /** The sandbox declined to run the artefact. */
        REFUSED
    }

    public SandboxResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    /** A run that produced an output. */
    public static SandboxResult ok(Map<String, Object> output, int steps) {
        return new SandboxResult(Status.OK, output, null, steps);
    }

    /** A run in which the program failed. */
    public static SandboxResult failed(String error, int steps) {
        return new SandboxResult(Status.FAILED, Map.of(), error, steps);
    }

    /** A run the sandbox declined to perform. */
    public static SandboxResult refused(String reason) {
        return new SandboxResult(Status.REFUSED, Map.of(), reason, 0);
    }

    /** True when the artefact ran and succeeded. */
    public boolean succeeded() {
        return status == Status.OK;
    }

    @Override
    public String toString() {
        return "SandboxResult[" + status + " steps=" + steps
                + (error == null ? "" : " error=" + error) + "]";
    }
}
