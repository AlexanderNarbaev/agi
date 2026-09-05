package io.matrix.api;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public class OnnxRuntimeAdapter {

    private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeAdapter.class);

    /** Default ONNX model path (exported via optimum-cli in RUN 59). */
    public static final Path DEFAULT_MODEL = Path.of("models/onnx/qwen05b/model.onnx");

    private final Path modelPath;
    private OrtSession session;
    private OrtEnvironment environment;
    private final AtomicLong inferenceCount = new AtomicLong();

    public OnnxRuntimeAdapter() {
        this(DEFAULT_MODEL);
    }

    public OnnxRuntimeAdapter(Path modelPath) {
        this.modelPath = modelPath;
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
            session = environment.createSession(modelPath.toString(), options);
            log.info("OnnxRuntimeAdapter: loaded {} (inputs={}, outputs={})",
                    modelPath, session.getInputInfo().size(), session.getOutputInfo().size());
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
        if (!isLoaded()) return "OnnxRuntimeAdapter(unloaded, path=" + modelPath + ")";
        try {
            return String.format(
                    "OnnxRuntimeAdapter(loaded, inputs=%d, outputs=%d, path=%s, inferences=%d)",
                    session.getInputInfo().size(),
                    session.getOutputInfo().size(),
                    modelPath,
                    inferenceCount.get());
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
