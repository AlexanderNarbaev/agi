package io.matrix.nativekernel;

/**
 * W51 — the status codes of the native kernel library.
 *
 * <p>Every code here mirrors a code in
 * {@code matrix-core/src/main/c/matrixkernel/matrix_kernel.h}. The two sets
 * must stay in step: the Java side maps a refusal code to a name, and a name
 * that does not exist on the C side would turn a refusal into a mystery.
 *
 * <p>The contract is the same on both sides: {@link #OK} means the call
 * happened, and every other value is a refusal that the caller can act on.
 * There is no "it crashed" value, because the C side never crashes on a
 * malformed argument — it refuses.
 */
public final class NativeKernelStatus {

    /** The call succeeded. */
    public static final int OK = 0;

    /** A required pointer argument was NULL on the native side. */
    public static final int ERR_NULL = -1;

    /** The arity was outside the supported range. */
    public static final int ERR_ARITY = -2;

    /** The truth table was shorter than the arity requires. */
    public static final int ERR_TABLE = -3;

    /** The batch count was negative or above the supported maximum. */
    public static final int ERR_COUNT = -4;

    /** A buffer was not aligned on a 64-bit boundary. */
    public static final int ERR_ALIGNMENT = -5;

    private NativeKernelStatus() {
    }

    /**
     * The name of a status code.
     *
     * @param status a status code, native or Java.
     * @return a short, stable, human-readable name; never null.
     */
    public static String describe(int status) {
        return switch (status) {
            case OK -> "ok";
            case ERR_NULL -> "null argument";
            case ERR_ARITY -> "arity out of range";
            case ERR_TABLE -> "truth table too short";
            case ERR_COUNT -> "batch count out of range";
            case ERR_ALIGNMENT -> "buffer not aligned";
            default -> "unknown status";
        };
    }

    /**
     * Whether a status code means success.
     *
     * @param status a status code.
     * @return true when the call succeeded.
     */
    public static boolean isOk(int status) {
        return status == OK;
    }
}
