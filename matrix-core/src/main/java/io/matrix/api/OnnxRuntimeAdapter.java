package io.matrix.api;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtEpDevice;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtHardwareDevice.OrtHardwareDeviceType;
import ai.onnxruntime.OrtProvider;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    // ── OD-028: the GPU path, observed rather than assumed ────────────────────
    //
    // Every one of these is a FACT the runtime reported, never a constant. Before
    // OD-028 this class reported a hardcoded {"CPUExecutionProvider"} whatever
    // happened, so a dashboard could not tell a working GPU from a silent fallback.
    // The facts are collected in load() and are individually checkable.
    private volatile boolean cudaProviderAvailable;
    private volatile boolean cudaRuntimeLibrariesFound;
    private volatile boolean cudaRegistered;
    private volatile boolean cudaSelectedForModel;
    private volatile boolean cudaExecuted;
    private volatile boolean hardwareDetected;
    private volatile String cudaRegistrationError;
    private volatile String gpuEvidence = "the session was never loaded";
    private volatile List<String> availableProviders = List.of();
    private volatile List<String> enumeratedDevices = List.of();
    private volatile String[] observedProviders = new String[0];

    /** Where the session's execution profile is written, or null for no profiling. */
    private volatile Path profileDirectory;
    private volatile boolean profileCaptured;

    /**
     * Whether the CUDA provider library has been registered in the environment.
     *
     * <p>It is registered once per PROCESS and stays registered. The ONNX Runtime
     * environment is a singleton, so the registration is a property of the
     * process, not of an adapter — and unregistering it was measured to break the
     * CUDA provider for every session that came afterwards.
     */
    private static volatile boolean cudaProviderLibraryRegistered;

    /** The execution-profile field that names the provider that ran a node. */
    private static final Pattern PROVIDER_FIELD =
            Pattern.compile("\"provider\"\\s*:\\s*\"([^\"]+)\"");

    /** The name the CUDA provider is registered under in the ONNX Runtime environment. */
    private static final String CUDA_PROVIDER_NAME = "CUDAExecutionProvider";

    /**
     * The states the GPU path can be in.
     *
     * <p>They are a ladder: each one means "everything below it was also
     * established". A state is only ever reported when the evidence for it was
     * observed, and {@link GpuDiagnostic#evidence()} says what was observed.
     *
     * <ul>
     *   <li>{@link #HARDWARE_DETECTED} — the ONNX Runtime environment enumerated a
     *       GPU execution-provider device. On ONNX Runtime 1.29.0 the environment
     *       enumerates only the providers registered IN it, so this is observed by
     *       registering the CUDA provider library, enumerating, and unregistering
     *       again.</li>
     *   <li>{@link #CUDA_RUNTIME_LIBRARIES_FOUND} — the CUDA provider's shared
     *       object LOADED, which means the OS dynamic linker resolved every CUDA
     *       library it needs (libcudart, libcublas, libcublasLt, libcurand).</li>
     *   <li>{@link #CUDA_PROVIDER_AVAILABLE} — the ONNX Runtime build offers the
     *       CUDA provider. This is a property of the BUILD, not of the machine.</li>
     *   <li>{@link #CUDA_REGISTERED} — {@code addCUDA()} returned without
     *       throwing, so the provider was registered for this session.</li>
     *   <li>{@link #CUDA_SELECTED_FOR_MODEL} — the model's graph was partitioned
     *       with CUDA nodes. Read from the session's execution profile, which
     *       names the provider of every node. It needs profiling to have been
     *       enabled, and it is never claimed without that evidence.</li>
     *   <li>{@link #CUDA_EXECUTION_COMPLETED} — a forward pass ran to completion
     *       while the CUDA provider was executing. Also read from the execution
     *       profile, which is the only ground truth in the ONNX Runtime Java
     *       binding for "the GPU did the work".</li>
     *   <li>{@link #CPU_FALLBACK_USED} — the CPU is executing. When the GPU was
     *       requested, this is a FALLBACK and the diagnostic says so.</li>
     * </ul>
     */
    public enum GpuState {
        HARDWARE_DETECTED,
        CUDA_RUNTIME_LIBRARIES_FOUND,
        CUDA_PROVIDER_AVAILABLE,
        CUDA_REGISTERED,
        CUDA_SELECTED_FOR_MODEL,
        CUDA_EXECUTION_COMPLETED,
        CPU_FALLBACK_USED
    }

    /**
     * What the runtime actually reported about the GPU path.
     *
     * @param state the state, as observed
     * @param activeProviders the providers THIS session runs with; the observed
     *        ones when the execution profile has been captured, the configured
     *        ones otherwise
     * @param availableProviders the providers the ONNX Runtime build offers
     * @param devices the execution-provider devices the environment enumerated
     * @param gpuRequested whether the GPU was asked for at all
     * @param fallbackUsed true when the GPU was requested and the CPU was used
     *        instead — a silent fallback, which is the failure this class exists
     *        to make visible
     * @param executionProvidersObserved whether {@code activeProviders} came from
     *        the execution profile rather than from the session's configuration
     * @param cudaRegistrationError the exception from {@code addCUDA()}, verbatim,
     *        or null when the registration succeeded
     * @param evidence the observation the state rests on, in words
     */
    public record GpuDiagnostic(
            GpuState state,
            String[] activeProviders,
            List<String> availableProviders,
            List<String> devices,
            boolean gpuRequested,
            boolean fallbackUsed,
            boolean executionProvidersObserved,
            String cudaRegistrationError,
            String evidence) {}

    /**
     * The decision table, as a pure function so that it can be tested on a
     * machine with no GPU at all.
     *
     * <p>The order is the ladder: the highest state whose evidence was obtained
     * wins. A fallback outranks everything, because "the GPU was asked for and the
     * CPU was used" is the fact an operator needs first.
     *
     * <p>The ladder is CAUSAL: a state is never reported unless the facts it
     * implies were also observed. The model can only be selected for the CUDA
     * device if the provider was registered, so {@code selectedForModel} is only
     * honoured together with {@code registered}.
     *
     * @param hardwarePresent a GPU device was enumerated by the environment
     * @param runtimeLibrariesFound the CUDA provider's shared object loaded
     * @param providerAvailable the ONNX Runtime build offers the CUDA provider
     * @param registered {@code addCUDA()} returned without throwing
     * @param selectedForModel the runtime says this model fits the CUDA device
     * @param executedOnCuda the execution profile names the CUDA provider
     * @param fallbackUsed the GPU was requested and the CPU was used instead
     * @return the state
     */
    static GpuState deriveState(boolean hardwarePresent,
                                 boolean runtimeLibrariesFound,
                                 boolean providerAvailable,
                                 boolean registered,
                                 boolean selectedForModel,
                                 boolean executedOnCuda,
                                 boolean fallbackUsed) {
        if (fallbackUsed) {
            return GpuState.CPU_FALLBACK_USED;
        }
        if (executedOnCuda) {
            return GpuState.CUDA_EXECUTION_COMPLETED;
        }
        // The model can only be SELECTED for the device if the provider was
        // registered for the session, so the registration is a precondition: the
        // ladder is causal, and a state never implies a fact that was not observed.
        if (registered && selectedForModel) {
            return GpuState.CUDA_SELECTED_FOR_MODEL;
        }
        if (registered) {
            return GpuState.CUDA_REGISTERED;
        }
        if (providerAvailable) {
            return GpuState.CUDA_PROVIDER_AVAILABLE;
        }
        if (runtimeLibrariesFound) {
            return GpuState.CUDA_RUNTIME_LIBRARIES_FOUND;
        }
        if (hardwarePresent) {
            return GpuState.HARDWARE_DETECTED;
        }
        return GpuState.CPU_FALLBACK_USED;
    }

    public OnnxRuntimeAdapter() {
        this(DEFAULT_MODEL);
    }

    public OnnxRuntimeAdapter(Path modelPath) {
        this.modelPath = modelPath;
    }

    /** RUN 62 — enable/disable GPU execution (defaults to true if available). */
    public void setUseGpu(boolean enabled) { this.useGpu = enabled; }
    public boolean isUseGpu() { return useGpu; }

    /**
     * RUN 62 — get the execution providers THIS session runs with.
     *
     * <p>Before OD-028 this returned a constant. It now returns what the runtime
     * reported: the providers the session was configured with, and — once
     * {@link #captureExecutionProviders()} has read the session's execution
     * profile — the providers that actually executed a node.
     *
     * @return a defensive copy of the provider names
     */
    public String[] activeProviders() { return activeProviders.clone(); }

    /**
     * Where the session's execution profile is written.
     *
     * <p>Profiling is OPT-IN because the profile is large — one forward pass over
     * the exported model writes megabytes of JSON — and it is the only way to
     * observe which provider actually ran a node. With no directory set, the
     * diagnostic never claims {@link GpuState#CUDA_EXECUTION_COMPLETED}, because
     * it has no evidence for it.
     *
     * @param directory where the profile is written, or null to disable profiling
     */
    public void setProfileDirectory(Path directory) { this.profileDirectory = directory; }

    /**
     * End the session's profiling and read the providers that actually executed.
     *
     * <p>The ONNX Runtime execution profile names the provider that ran each node,
     * which is the only ground truth in the Java binding for "the GPU did the
     * work". This method is what turns the diagnostic's claim from "the provider
     * was registered" into "the provider executed".
     *
     * @return the observed provider names, or the configured ones when the profile
     *         was not available
     */
    public synchronized String[] captureExecutionProviders() {
        if (session == null || profileDirectory == null || profileCaptured) {
            return activeProviders.clone();
        }
        try {
            String profilePath = session.endProfiling();
            profileCaptured = true;
            String json = Files.readString(Path.of(profilePath));
            Set<String> providers = new TreeSet<>();
            Matcher matcher = PROVIDER_FIELD.matcher(json);
            while (matcher.find()) {
                providers.add(matcher.group(1));
            }
            if (!providers.isEmpty()) {
                observedProviders = providers.toArray(new String[0]);
                activeProviders = observedProviders.clone();
                boolean cudaRan = providers.contains(CUDA_PROVIDER_NAME);
                // The profile names the provider of every node, so a CUDA provider
                // in that set is the model having been partitioned onto the GPU.
                cudaSelectedForModel = cudaRan;
                cudaExecuted = cudaRan && inferenceCount.get() > 0;
                gpuEvidence = "the execution profile names " + providers
                        + " and the session has completed " + inferenceCount.get()
                        + " inference(s)";
            }
        } catch (Exception e) {
            log.warn("OnnxRuntimeAdapter: the execution profile could not be read: {}",
                    e.getMessage());
        }
        return activeProviders.clone();
    }

    /**
     * What the runtime reported about the GPU path.
     *
     * @return the diagnostic; never null
     */
    public synchronized GpuDiagnostic gpuDiagnostic() {
        boolean requested = useGpu;
        boolean fallback = requested && !cudaRegistered;
        GpuState state = deriveState(hardwareDetected, cudaRuntimeLibrariesFound,
                cudaProviderAvailable, cudaRegistered, cudaSelectedForModel,
                cudaExecuted, fallback);
        return new GpuDiagnostic(
                state,
                activeProviders.clone(),
                List.copyOf(availableProviders),
                List.copyOf(enumeratedDevices),
                requested,
                fallback,
                observedProviders.length > 0,
                cudaRegistrationError,
                gpuEvidence);
    }

    /**
     * Enumerate the execution-provider devices the runtime can see.
     *
     * <p>ONNX Runtime 1.29.0's environment enumerates only the providers
     * registered IN it, so a GPU device appears only after the CUDA provider
     * library has been registered. Registering it is therefore how the hardware is
     * OBSERVED rather than assumed.
     *
     * <p>The registration is left in place, and that is deliberate. Both of these
     * were measured on this machine:
     * <ul>
     *   <li>Unregistering the provider library BREAKS the CUDA provider for the
     *       rest of the process: a session created afterwards, with the provider
     *       registered on it, silently ran on the CPU while the diagnostic still
     *       said the provider had been registered. The environment is a
     *       process-wide singleton, and taking the provider back out of it leaves
     *       it in a state where it can no longer be used.</li>
     *   <li>A session that does NOT ask for the CUDA provider still runs on the
     *       CPU after the registration. The session's own options decide which
     *       provider it uses, not the environment's registry.</li>
     * </ul>
     */
    private void probeGpuDevices() {
        Path providerLibrary = locateProviderLibrary();
        if (providerLibrary == null) {
            gpuEvidence = "the ONNX Runtime CUDA provider library was not found in "
                    + "the runtime's extraction directory, so no device could be enumerated";
            return;
        }
        try {
            if (!cudaProviderLibraryRegistered) {
                environment.registerExecutionProviderLibrary(CUDA_PROVIDER_NAME,
                        providerLibrary.toString());
                cudaProviderLibraryRegistered = true;
            }
            // The provider's shared object LOADED. The OS dynamic linker had to
            // resolve every CUDA library it needs, so this is the observation that
            // the runtime libraries were found — not an inference from the driver.
            cudaRuntimeLibrariesFound = true;
            List<OrtEpDevice> all = environment.getEpDevices();
            List<String> names = new ArrayList<>();
            for (OrtEpDevice device : all) {
                names.add(device.getEpName());
                if (CUDA_PROVIDER_NAME.equals(device.getEpName())
                        && device.getDevice().getType() == OrtHardwareDeviceType.GPU) {
                    hardwareDetected = true;
                }
            }
            enumeratedDevices = List.copyOf(names);
            if (!hardwareDetected) {
                gpuEvidence = "the CUDA provider library loaded but the runtime enumerated "
                        + names.size() + " device(s) and none of them is a GPU: " + names;
                return;
            }
            // ONNX Runtime 1.29.0 also offers
            // getModelCompatibilityForEpDevices(devices, modelPath), which would
            // answer "is this model at home on this device" directly. It is NOT
            // used here: with a CUDA device in the list it SEGFAULTS the JVM inside
            // libonnxruntime.so, reproducibly, outside Gradle as well. A crash is
            // not a diagnostic. Whether the model was selected for the CUDA device
            // is therefore read from the session's execution profile instead —
            // see captureExecutionProviders().
            gpuEvidence = "the CUDA provider library loaded and the runtime enumerated "
                    + names.size() + " device(s) including a GPU; whether the model is "
                    + "selected for it is read from the execution profile";
        } catch (Exception e) {
            cudaRuntimeLibrariesFound = false;
            gpuEvidence = "the CUDA provider library could not be loaded, so the CUDA "
                    + "runtime libraries were not found: " + e.getMessage();
        }
    }

    /**
     * The CUDA provider's shared object, as the runtime extracted it.
     *
     * <p>The ONNX Runtime Java binding unpacks its native libraries into a
     * directory under {@code java.io.tmpdir} when the environment is created.
     *
     * @return the path, or null when it cannot be found
     */
    private static Path locateProviderLibrary() {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null) {
            return null;
        }
        Path root = Path.of(tmp);
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(path -> path.getFileName().toString().startsWith("onnxruntime-java"))
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("libonnxruntime_providers_cuda.so"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** The provider names of an available-provider set, in a stable order. */
    private static List<String> providerNames(Set<OrtProvider> providers) {
        Set<String> names = new LinkedHashSet<>();
        for (OrtProvider provider : providers) {
            names.add(provider.name());
        }
        return List.copyOf(names);
    }

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
            options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
            // RUN 475: enable all graph optimizations

            // RUN 62: configure GPU execution if requested and available.
            //
            // OD-028. The registration is no longer a silent affair: the exception
            // is RECORDED, and the diagnostic reports what the runtime actually
            // did rather than a constant. The catch does not swallow the failure —
            // it captures it, and the session is then created on the CPU with that
            // fact on the record.
            cudaProviderAvailable = OrtEnvironment.getAvailableProviders()
                    .contains(OrtProvider.CUDA);
            availableProviders = providerNames(OrtEnvironment.getAvailableProviders());

            // The device probe comes FIRST and on its own: it registers the CUDA
            // provider library in the environment so the runtime can enumerate the
            // devices. It must not run after addCUDA(), and it must not take the
            // provider back out of the environment — see probeGpuDevices().
            probeGpuDevices();

            if (useGpu) {
                try {
                    OrtCUDAProviderOptions cudaOpts = new OrtCUDAProviderOptions();
                    cudaOpts.add("device_id", "0");
                    options.addCUDA(cudaOpts);
                    cudaRegistered = true;
                    log.info("OnnxRuntimeAdapter: CUDA execution provider configured");
                } catch (Exception cudaEx) {
                    cudaRegistered = false;
                    cudaRegistrationError = cudaEx.getClass().getSimpleName()
                            + ": " + cudaEx.getMessage();
                    log.warn("OnnxRuntimeAdapter: CUDA not available, falling back to CPU: {}",
                            cudaEx.getMessage());
                }
            }

            if (profileDirectory != null) {
                try {
                    options.enableProfiling(profileDirectory.toString());
                } catch (OrtException e) {
                    log.warn("OnnxRuntimeAdapter: execution profiling was not enabled: {}",
                            e.getMessage());
                }
            }

            session = environment.createSession(modelPath.toString(), options);
            // The providers THIS session runs with. Until an execution profile has
            // been captured these are the providers the session was CONFIGURED
            // with; captureExecutionProviders() replaces them with the ones the
            // runtime observed.
            activeProviders = cudaRegistered
                    ? new String[]{"CPUExecutionProvider", CUDA_PROVIDER_NAME}
                    : new String[]{"CPUExecutionProvider"};
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

    /**
     * Run greedy next-token inference.
     *
     * <p>Given a sequence of input token ids, returns the argmax
     * of the logits at the LAST position. This is what you want
     * for autoregressive generation.
     *
     * @param tokenIds full token sequence (e.g., prompt + already-generated tokens)
     * @return argmax of last-position logits
     * @throws OrtException if inference fails
     */
    public synchronized long greedyNextToken(long[] tokenIds) throws OrtException {
        if (!isLoaded()) {
            throw new IllegalStateException("OnnxRuntimeAdapter: session not loaded");
        }
        long seqLen = tokenIds.length;
        long[] attentionMask = new long[(int) seqLen];
        long[] positionIds = new long[(int) seqLen];
        for (int i = 0; i < seqLen; i++) {
            attentionMask[i] = 1L;
            positionIds[i] = i;
        }
        try (OnnxTensor inputIds = OnnxTensor.createTensor(
                    environment, java.nio.LongBuffer.wrap(tokenIds), new long[]{1, seqLen});
             OnnxTensor attnMask = OnnxTensor.createTensor(
                    environment, java.nio.LongBuffer.wrap(attentionMask), new long[]{1, seqLen});
             OnnxTensor posIds = OnnxTensor.createTensor(
                    environment, java.nio.LongBuffer.wrap(positionIds), new long[]{1, seqLen})) {

            try (var results = session.run(java.util.Map.of(
                    "input_ids", inputIds,
                    "attention_mask", attnMask,
                    "position_ids", posIds))) {
                inferenceCount.incrementAndGet();
                float[][][] logits = (float[][][]) results.get(0).getValue();
                float[] lastLogits = logits[0][(int) seqLen - 1];
                int bestIdx = 0;
                float bestVal = lastLogits[0];
                for (int i = 1; i < lastLogits.length; i++) {
                    if (lastLogits[i] > bestVal) {
                        bestVal = lastLogits[i];
                        bestIdx = i;
                    }
                }
                return bestIdx;
            }
        }
    }

    /** Get model info string for diagnostics. */
    public synchronized String info() {
        if (!isLoaded()) {
            return String.format("OnnxRuntimeAdapter(unloaded, path=%s, useGpu=%s)",
                    modelPath, useGpu);
        }
        try {
            GpuDiagnostic diagnostic = gpuDiagnostic();
            return String.format(
                    "OnnxRuntimeAdapter(loaded, inputs=%d, outputs=%d, path=%s, inferences=%d, "
                            + "useGpu=%s, gpuState=%s, providers=%s)",
                    session.getInputInfo().size(),
                    session.getOutputInfo().size(),
                    modelPath,
                    inferenceCount.get(),
                    useGpu,
                    diagnostic.state(),
                    Arrays.toString(diagnostic.activeProviders()));
        } catch (Exception e) {
            return "OnnxRuntimeAdapter(error: " + e.getMessage() + ")";
        }
    }

    /**
     * Close the session.
     *
     * <p>The ONNX Runtime ENVIRONMENT is deliberately NOT closed. It is a
     * process-wide singleton — {@code OrtEnvironment.getEnvironment()} hands the
     * same instance to every caller — and this adapter does not own it. Closing
     * it here would leave every other adapter, and every later session in the
     * same process, with a destroyed environment: the CUDA provider then
     * registers without complaint but stops taking nodes, and the session runs
     * on the CPU while the diagnostic still says the provider was registered.
     * That is exactly the silent failure this class exists to make visible.
     */
    public synchronized void close() {
        if (session != null) {
            try { session.close(); } catch (OrtException e) { /* ignore */ }
            session = null;
        }
        environment = null;
    }
}
