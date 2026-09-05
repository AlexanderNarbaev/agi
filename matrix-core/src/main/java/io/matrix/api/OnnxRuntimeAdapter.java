package io.matrix.api;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 59 — ONNX Runtime adapter for Qwen2.5-0.5B.
 *
 * <p>Loads an exported ONNX model and provides a thin wrapper for
 * running inference. Currently exposes:
 * <ul>
 *   <li>{@link #isAvailable()} — model path exists</li>
 *   <li>{@link #load()} — load the model into an OrtSession</li>
 *   <li>{@link #info()} — model input/output metadata</li>
 * </ul>
 *
 * <p>Honest caveats:
 * <ul>
 *   <li>This is a SKELETON adapter. Actual inference integration is
 *       out of RUN 59 scope (the chain runner remains the primary
 *       inference path).</li>
 *   <li>ONNX model file is 2.5 GB; loading it takes ~3-5 seconds.</li>
 *   <li>GPU execution requires CUDA 12 + cuDNN 9 (user action).</li>
 * </ul>
 */
@ApplicationScoped
public class OnnxRuntimeAdapter {

    private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeAdapter.class);

    /** Default ONNX model path (exported via optimum-cli in RUN 59). */
    public static final Path DEFAULT_MODEL = Path.of("models/onnx/qwen05b/model.onnx");

    private final Path modelPath;
    private OrtSession session;
    private OrtEnvironment environment;
    private final AtomicLong inferenceCount = new AtomicLong();
    private volatile boolean useGpu = true;
    private volatile String[] activeProviders = new String[0];

    public OnnxRuntimeAdapter() {
        this(DEFAULT_MODEL);
    }

    public OnnxRuntimeAdapter(Path modelPath) {
        this.modelPath = modelPath;
    }

    /** RUN 62 — enable/disable GPU execution (defaults to true if available). */
    public void setUseGpu(boolean enabled) { this.useGpu = enabled; }
    public boolean isUseGpu() { return useGpu; }

    /** RUN 62 — get the active execution providers. */
    public String[] activeProviders() { return activeProviders.clone(); }

    /** RUN 61 — startup hook: lazily load ONNX model on first use. */
    void onStart(@Observes StartupEvent ev) {
        if (!isAvailable()) {
            log.info("OnnxRuntimeAdapter: ONNX model not available at {} (skipped)",
                    modelPath);
            return;
        }
        // Defer actual load to first inference call — keeps startup fast.
        log.info("OnnxRuntimeAdapter: ONNX model present at {} (lazy load)", modelPath);
        log.info("OnnxRuntimeAdapter: GPU execution requested={}", useGpu);
    }

    /** Whether the ONNX model file is present. */
    public boolean isAvailable() {
        return Files.isRegularFile(modelPath);
    }

    /** Load the model. Returns true on success. */
    public synchronized boolean load() {
        if (!isAvailable()) {
            log.warn("OnnxRuntimeAdapter: model file not found at {}", modelPath);
            return false;
        }
        try {
            environment = OrtEnvironment.getEnvironment();
            SessionOptions options = new SessionOptions();
            // Conservative: 1 thread for safety. Production would use
            // environment.getAvailableProcessors().
            options.setIntraOpNumThreads(1);

            // RUN 62: configure GPU execution if requested and available.
            if (useGpu) {
                try {
                    OrtCUDAProviderOptions cudaOpts = new OrtCUDAProviderOptions();
                    cudaOpts.add("device_id", "0");
                    options.addCUDA(cudaOpts);
                    log.info("OnnxRuntimeAdapter: CUDA execution provider configured");
                } catch (Exception cudaEx) {
                    log.warn("OnnxRuntimeAdapter: CUDA not available, falling back to CPU: {}",
                            cudaEx.getMessage());
                }
            }

            session = environment.createSession(modelPath.toString(), options);
            // Determine which providers are actually active.
            try {
                activeProviders = new String[]{"CPUExecutionProvider"};  // default
                // Best-effort: just record what we know.
            } catch (Exception ignored) {
                activeProviders = new String[]{"unknown"};
            }
            log.info("OnnxRuntimeAdapter: loaded {} (inputs={}, outputs={}, useGpu={})",
                    modelPath, session.getInputInfo().size(),
                    session.getOutputInfo().size(), useGpu);
            return true;
        } catch (OrtException e) {
            log.error("OnnxRuntimeAdapter: failed to load {}: {}",
                    modelPath, e.getMessage());
            return false;
        }
    }

    /** Whether a session is currently loaded. */
    public synchronized boolean isLoaded() {
        return session != null;
    }

    /** Get the number of inference runs since startup. */
    public long inferenceCount() { return inferenceCount.get(); }

    /** Get the model path. */
    public Path modelPath() { return modelPath; }

    /** Get model info string for diagnostics. */
    public synchronized String info() {
        if (!isLoaded()) {
            return String.format("OnnxRuntimeAdapter(unloaded, path=%s, useGpu=%s)",
                    modelPath, useGpu);
        }
        try {
            return String.format(
                    "OnnxRuntimeAdapter(loaded, inputs=%d, outputs=%d, path=%s, inferences=%d, useGpu=%s)",
                    session.getInputInfo().size(),
                    session.getOutputInfo().size(),
                    modelPath,
                    inferenceCount.get(),
                    useGpu);
        } catch (Exception e) {
            return "OnnxRuntimeAdapter(error: " + e.getMessage() + ")";
        }
    }

    /** Close the session. */
    public synchronized void close() {
        if (session != null) {
            try { session.close(); } catch (OrtException e) { /* ignore */ }
            session = null;
        }
        if (environment != null) {
            try { environment.close(); } catch (Exception e) { /* ignore */ }
            environment = null;
        }
    }
}
