package io.matrix.imports;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Phase Y (RUN 454) — GPU-accelerated matmul for BitLinear.
 *
 * <p>Loads {@code libbitlinear_gpu.so} (CUDA shared library compiled with
 * {@code nvcc}) and provides int8 matmul on the GPU via Project Panama FFM
 * (JEP 424).
 *
 * <p>Per the user directive (2026-09-12): "GPU with 12GB video memory and
 * overall 64GB for CPU memory. So continue research, and implement what
 * can be implemented on current hardware." — this enables real GPU
 * acceleration of the BitLinear matmul hot path.
 *
 * <h2>Use case</h2>
 * {@link io.matrix.neuron.BitLinear#quantizeWeightAbsmean} produces
 * ternary weights in {-1, 0, +1}; {@link BitLinear#quantizeActivationAbsmax}
 * produces int8 activations in [-127, 127]. Encoding these as int8
 * (mapping ternary {-1, 0, +1} → int8 {-1, 0, 1}) enables a single
 * int8 matmul kernel on GPU.
 *
 * <h2>CONTRACT: CONSTITUTION I</h2>
 * Pure function wrapper around CUDA calls. No Random, no wall-clock in
 * runtime paths. Caller supplies all data.
 */
public final class BitLinearGpu {

    private static final MethodHandle MATMUL_I8;
    private static final MethodHandle IS_AVAILABLE;
    private static final MethodHandle LAST_ERROR;
    private static final MethodHandle DEVICE_COUNT;
    private static final MethodHandle DEVICE_NAME;
    private static final boolean NATIVE_AVAILABLE;

    // Hold a reference to keep the lookup arena alive for class lifetime
    @SuppressWarnings("unused")
    private static final java.lang.foreign.Arena LOOKUP_ARENA =
            java.lang.foreign.Arena.ofShared();

    static {
        MethodHandle matmul = null;
        MethodHandle avail = null;
        MethodHandle err = null;
        MethodHandle devcount = null;
        MethodHandle devname = null;
        boolean ok = false;
        try {
            // First try to load libcudart.so (CUDA runtime) explicitly.
            // On Linux, libbitlinear_gpu.so calls into libcudart via dlopen
            // at runtime; loading it explicitly makes that resolution reliable.
            tryLoadCudaRuntime();
            // Load the CUDA shared library
            // user.dir is the working directory at JVM start. We try multiple
            // candidate paths because Gradle's test executor may use a different
            // working directory.
            String libPath = findLibraryPath();
            System.load(libPath);
            Linker linker = Linker.nativeLinker();
            // IMPORTANT: use libraryLookup with explicit path to find symbols
            // in our System.load'd library. defaultLookup() doesn't include
            // user-loaded libraries — only system libraries.
            // Use a shared arena that lives for the class lifetime so the
            // symbol lookup remains valid for all subsequent calls.
            // Try libraryLookup first; fall back to system lookup if not found
            SymbolLookup lookup;
            try {
                lookup = SymbolLookup.libraryLookup(libPath, LOOKUP_ARENA);
                System.err.println("[BitLinearGpu] using libraryLookup for: " + libPath);
            } catch (Throwable t) {
                lookup = linker.defaultLookup();
                System.err.println("[BitLinearGpu] libraryLookup failed: " + t.getMessage()
                        + ", falling back to defaultLookup");
            }

            // int bitlinear_matmul_i8(
            //   const int8_t*, const int8_t*, int32_t*, int, int, int)
            matmul = linker.downcallHandle(
                    lookup.find("bitlinear_matmul_i8").orElseThrow(),
                    FunctionDescriptor.of(
                            /* return */ java.lang.foreign.ValueLayout.JAVA_INT,
                            /* weights */ java.lang.foreign.ValueLayout.ADDRESS,
                            /* activations */ java.lang.foreign.ValueLayout.ADDRESS,
                            /* output */ java.lang.foreign.ValueLayout.ADDRESS,
                            /* out_features */ java.lang.foreign.ValueLayout.JAVA_INT,
                            /* in_features */ java.lang.foreign.ValueLayout.JAVA_INT,
                            /* gpu_device_id */ java.lang.foreign.ValueLayout.JAVA_INT));
            avail = linker.downcallHandle(
                    lookup.find("bitlinear_is_available").orElseThrow(),
                    FunctionDescriptor.of(
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.JAVA_INT));
            err = linker.downcallHandle(
                    lookup.find("bitlinear_last_error").orElseThrow(),
                    FunctionDescriptor.of(
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.ADDRESS,
                            java.lang.foreign.ValueLayout.JAVA_INT));
            devcount = linker.downcallHandle(
                    lookup.find("bitlinear_device_count").orElseThrow(),
                    FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT));
            devname = linker.downcallHandle(
                    lookup.find("bitlinear_device_name").orElseThrow(),
                    FunctionDescriptor.of(
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.ADDRESS,
                            java.lang.foreign.ValueLayout.JAVA_INT));
            ok = true;
        } catch (Throwable t) {
            ok = false;
            System.err.println("[BitLinearGpu] init failed: " + t.getClass().getSimpleName()
                    + " — " + t.getMessage());
            t.printStackTrace();
        }
        MATMUL_I8 = matmul;
        IS_AVAILABLE = avail;
        LAST_ERROR = err;
        DEVICE_COUNT = devcount;
        DEVICE_NAME = devname;
        NATIVE_AVAILABLE = ok;
    }

    private BitLinearGpu() {}

    /**
     * Try to load libcudart.so explicitly from common locations.
     * Failures are silently ignored — the CUDA shared lib may already be
     * findable via dlopen at runtime.
     */
    /**
     * Find the bitlinear_gpu.so library path by trying multiple candidate
     * locations. Returns the first existing path.
     */
    private static String findLibraryPath() {
        String cwd = System.getProperty("user.dir");
        String[] candidates = {
                cwd + "/src/main/c/cuda/libbitlinear_gpu.so",
                cwd + "/matrix-core/src/main/c/cuda/libbitlinear_gpu.so",
                cwd + "/../matrix-core/src/main/c/cuda/libbitlinear_gpu.so",
                "/home/alexandr-narbaev/Projects/agi/matrix-core/src/main/c/cuda/libbitlinear_gpu.so"
        };
        for (String path : candidates) {
            if (new java.io.File(path).exists()) {
                System.err.println("[BitLinearGpu] found library at: " + path);
                return path;
            }
        }
        // Fallback — let System.load fail with a clear error
        System.err.println("[BitLinearGpu] library not found in candidates, falling back to: "
                + candidates[0]);
        return candidates[0];
    }

    private static void tryLoadCudaRuntime() {
        String[] candidates = {
                "/usr/local/cuda-13.1/lib64/libcudart.so.13",
                "/usr/local/cuda-13.1/lib64/libcudart.so",
                "/usr/local/cuda/lib64/libcudart.so.13",
                "/usr/local/cuda/lib64/libcudart.so",
                "/usr/lib/x86_64-linux-gnu/libcudart.so",
                "libcudart.so"
        };
        for (String path : candidates) {
            try {
                if (path.startsWith("/")) {
                    java.io.File f = new java.io.File(path);
                    if (f.exists()) {
                        System.load(path);
                        System.err.println("[BitLinearGpu] loaded cudart from " + path);
                        return;
                    }
                } else {
                    System.loadLibrary(path);
                    System.err.println("[BitLinearGpu] loaded cudart via System.loadLibrary: " + path);
                    return;
                }
            } catch (Throwable t) {
                System.err.println("[BitLinearGpu] failed to load " + path
                        + ": " + t.getMessage());
            }
        }
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    /**
     * Number of available CUDA devices.
     */
    public static int deviceCount() {
        if (!NATIVE_AVAILABLE) {
            System.err.println("[BitLinearGpu] deviceCount: NATIVE_AVAILABLE=false");
            return 0;
        }
        try {
            int count = (int) DEVICE_COUNT.invokeExact();
            System.err.println("[BitLinearGpu] deviceCount returned: " + count);
            return count;
        } catch (Throwable t) {
            System.err.println("[BitLinearGpu] deviceCount threw: " + t);
            return 0;
        }
    }

    /**
     * Whether a specific CUDA device is available.
     */
    public static boolean isDeviceAvailable(int gpuDeviceId) {
        if (!NATIVE_AVAILABLE) return false;
        try {
            return (int) IS_AVAILABLE.invokeExact(gpuDeviceId) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Get the name of a CUDA device.
     */
    public static String deviceName(int gpuDeviceId) {
        if (!NATIVE_AVAILABLE) return "unavailable";
        try {
            java.lang.foreign.MemorySegment buf =
                    java.lang.foreign.Arena.ofConfined().allocate(256);
            int len = (int) DEVICE_NAME.invokeExact(gpuDeviceId, buf, 256);
            if (len <= 0) return "unknown";
            return buf.reinterpret(256).getString(0);
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    /**
     * Last CUDA error message.
     */
    public static String lastError() {
        if (!NATIVE_AVAILABLE) return "native lib unavailable";
        try {
            java.lang.foreign.MemorySegment buf =
                    java.lang.foreign.Arena.ofConfined().allocate(512);
            LAST_ERROR.invokeExact(buf, 512);
            return buf.reinterpret(512).getString(0);
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    /**
     * GPU-accelerated int8 matmul: output = weights @ activations.
     *
     * <p>Caller must ensure {@code gpuDeviceId} is a valid CUDA device.
     * Falls back to Java {@code int8} dot product if GPU unavailable.
     *
     * @param weights      [out_features × in_features] row-major int8 weights
     * @param activations  [in_features] int8 activations
     * @param gpuDeviceId   CUDA device index (typically 0)
     * @return [out_features] int32 accumulator; null on error
     */
    public static int[] matmulI8(byte[] weights, byte[] activations, int gpuDeviceId) {
        if (weights == null || activations == null) {
            throw new IllegalArgumentException("null inputs");
        }
        int outFeatures = weights.length / activations.length;
        int inFeatures = activations.length;
        if (weights.length != outFeatures * inFeatures) {
            throw new IllegalArgumentException("weights.length not multiple of activations.length");
        }
        int[] output = new int[outFeatures];

        if (!NATIVE_AVAILABLE || !isDeviceAvailable(gpuDeviceId)) {
            // Java fallback
            for (int i = 0; i < outFeatures; i++) {
                int sum = 0;
                for (int j = 0; j < inFeatures; j++) {
                    sum += (int) weights[i * inFeatures + j] * (int) activations[j];
                }
                output[i] = sum;
            }
            return output;
        }

        try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
            java.lang.foreign.MemorySegment wSeg =
                    arena.allocate((long) weights.length * java.lang.foreign.ValueLayout.JAVA_BYTE.byteSize());
            java.lang.foreign.MemorySegment aSeg =
                    arena.allocate((long) activations.length * java.lang.foreign.ValueLayout.JAVA_BYTE.byteSize());
            java.lang.foreign.MemorySegment oSeg =
                    arena.allocate((long) outFeatures * java.lang.foreign.ValueLayout.JAVA_INT.byteSize());

            wSeg.copyFrom(java.lang.foreign.MemorySegment.ofArray(weights));
            aSeg.copyFrom(java.lang.foreign.MemorySegment.ofArray(activations));

            int rc = (int) MATMUL_I8.invokeExact(
                    wSeg, aSeg, oSeg, outFeatures, inFeatures, gpuDeviceId);
            if (rc != 0) {
                return null; // CUDA error
            }

            for (int i = 0; i < outFeatures; i++) {
                output[i] = oSeg.get(java.lang.foreign.ValueLayout.JAVA_INT, (long) i * 4);
            }
            return output;
        } catch (Throwable t) {
            return null;
        }
    }
}
