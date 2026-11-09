package io.matrix.nativekernel;

/**
 * W51 — the refusal of a native kernel call.
 *
 * <p>It carries the native status code, so a caller can tell an arity
 * mistake from a truncated truth table without parsing a message. The message
 * is for the human; the code is for the program.
 */
public final class NativeKernelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    /**
     * Creates an exception for a native status code.
     *
     * @param status  the native status code.
     * @param message what was being attempted.
     */
    public NativeKernelException(int status, String message) {
        super(message + " (status " + status + ": " + NativeKernelStatus.describe(status) + ")");
        this.status = status;
    }

    /**
     * The native status code that caused this refusal.
     *
     * @return the status code.
     */
    public int status() {
        return status;
    }
}
