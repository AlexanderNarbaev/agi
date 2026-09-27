package io.matrix.brain.runtime;

import io.matrix.federation.gpu.GpuTaskExecutor;
import io.matrix.federation.gpu.MatrixNativeMath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RECON-W6 — RealGpuKernelEngine rewritten for honest acceleration.
 *
 * <p>Uses {@link MatrixNativeMath} (JDK Vector API when available) to
 * detect capability and report the chosen backend honestly. Three backends:
 * <ul>
 *   <li>CPU_VECTOR — Vector API works (incubator module on the classpath)</li>
 *   <li>CPU_SCALAR — Vector API class loadable but species init failed</li>
 *   <li>UNAVAILABLE — Vector API not on classpath</li>
 * </ul>
 *
 * <p>Article VIII: no simulated success. The {@code runKernel()} returns
 * a real payload computed from the input. The {@code gpuUsed} flag is true
 * only when the CPU_VECTOR path actually performs a vectorized operation.</p>
 */
public final class RealGpuKernelEngine {

    public record KernelResult(Object payload, long latencyNs, boolean gpuUsed) {}

    private final MatrixNativeMath nativeMath;
    private final GpuTaskExecutor executor;
    private final boolean gpuEnabled;

    public RealGpuKernelEngine() {
        this(false);
    }

    public RealGpuKernelEngine(boolean gpuEnabled) {
        this.gpuEnabled = gpuEnabled;
        this.nativeMath = new MatrixNativeMath();
        this.executor = new GpuTaskExecutor(42L, gpuEnabled);
    }

    public MatrixNativeMath.Backend backend() { return nativeMath.backend(); }
    public GpuTaskExecutor.GpuStats stats() { return executor.getStats(); }

    /**
     * Run a bit-cosine kernel between two long[] vectors.
     * The CPU_VECTOR path uses MatrixNativeMath's lane-wise XOR primitive;
     * the CPU_SCALAR path uses the scalar reference. The UNAVAILABLE path
     * returns a structured error — never simulated success.
     */
    public KernelResult runBitCosine(long[] a, long[] b) {
        long startNs = System.nanoTime();
        if (a == null || b == null) {
            return new KernelResult(error("null input"), 0L, false);
        }
        Object payload;
        boolean gpuUsed = false;
        long distance;
        switch (nativeMath.backend()) {
            case CPU_VECTOR -> {
                distance = nativeMath.vectorXorPopCount(a, b);
                gpuUsed = true;
                payload = new Result("cpu-vector", distance);
            }
            case CPU_SCALAR -> {
                distance = MatrixNativeMath.scalarXorPopCount(a, b);
                gpuUsed = false;
                payload = new Result("cpu-scalar", distance);
            }
            case UNAVAILABLE -> {
                distance = -1;
                payload = error("Vector API unavailable; no GPU acceleration possible");
                gpuUsed = false;
            }
            default -> {
                distance = -1;
                payload = error("unknown backend");
                gpuUsed = false;
            }
        }
        long latencyNs = System.nanoTime() - startNs;
        return new KernelResult(payload, latencyNs, gpuUsed);
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "error");
        m.put("message", msg);
        return m;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("backend", nativeMath.backend().name());
        m.put("gpu_enabled", gpuEnabled);
        m.put("vector_available", MatrixNativeMath.isVectorAvailable());
        m.put("engine", "MatrixNativeMath." + backendMethod(nativeMath.backend()));
        m.put("stats", stats());
        return m;
    }

    private static String backendMethod(MatrixNativeMath.Backend b) {
        return switch (b) {
            case CPU_VECTOR -> "vectorXorPopCount(loopBound)";
            case CPU_SCALAR -> "scalarXorPopCount";
            case UNAVAILABLE -> "(no-op; see status:error)";
        };
    }

    /** Result struct exposed via JSON. */
    public record Result(String backend, long distance) {}
}
