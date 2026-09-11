package io.matrix.imports;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Phase X (RUN 416) — C extension via Project Panama FFM.
 *
 * <p>Loads {@code libtruthy_hamming.so} and provides a high-speed
 * Hamming distance function. Falls back to Java implementation if
 * the native lib is unavailable.
 *
 * <p>Per the user directive (2026-09-11): "use c++ as powerfull
 * implementation and java call's" — this is the C-via-FFM hot path.
 */
public final class HammingNative {

    private static final MethodHandle HAMMING_U64;
    private static final MethodHandle HAMMING_BATCH;
    private static final boolean NATIVE_AVAILABLE;

    static {
        MethodHandle u64 = null, batch = null;
        boolean ok = false;
        try {
            // Load the C library
            System.load(System.getProperty("user.dir")
                    + "/matrix-core/src/main/c/libtruthy/libtruthy_hamming.so");
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = linker.defaultLookup();
            // hamming_distance_u64(uint64_t, uint64_t) -> int
            MethodHandle h = linker.downcallHandle(
                    lookup.find("hamming_distance_u64").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
            // hamming_distance_batch(uint64_t*, uint64_t*, int, int*) -> void
            MethodHandle b = linker.downcallHandle(
                    lookup.find("hamming_distance_batch").orElseThrow(),
                    FunctionDescriptor.ofVoid(
                            java.lang.foreign.ValueLayout.ADDRESS,
                            java.lang.foreign.ValueLayout.ADDRESS,
                            JAVA_INT,
                            java.lang.foreign.ValueLayout.ADDRESS));
            u64 = h;
            batch = b;
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        HAMMING_U64 = u64;
        HAMMING_BATCH = batch;
        NATIVE_AVAILABLE = ok;
    }

    private HammingNative() {}

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * Hamming distance between two long values.
     * Returns Long.bitCount(a ^ b) — native if available, Java fallback.
     */
    public static int hamming(long a, long b) {
        if (NATIVE_AVAILABLE) {
            try {
                return (int) HAMMING_U64.invokeExact(a, b);
            } catch (Throwable t) {
                // Fall through to Java
            }
        }
        return Long.bitCount(a ^ b);
    }
}
