package io.matrix.api;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RUN 54 — HuggingFace model fetcher.
 *
 * <p>Wraps the `hf` CLI to download Qwen2.5-0.5B (and other models)
 * into a local cache directory. Used at startup to ensure the
 * boolean chain distillation source is available.
 *
 * <p>Why this class exists:
 * <ul>
 *   <li>The boolean chain is DISTILLED from Qwen2.5-0.5B (per the
 *       CONSTITUTION principle "Matrix just distill data and forget
 *       which model it came from").</li>
 *   <li>The distillation process needs access to the source model
 *       weights, tokenizer, and config.</li>
 *   <li>The HuggingFace CLI (`hf`) provides authenticated access to
 *       these artifacts.</li>
 * </ul>
 *
 * <p>Honest caveats:
 * <ul>
 *   <li>This is a thin wrapper around the `hf` CLI — the actual
 *       download is delegated to the CLI process.</li>
 *   <li>If `hf` is not on PATH, the fetch is a no-op and the caller
 *       must provide the model artifacts via some other means.</li>
 *   <li>For production, a Java HTTP client + HF Hub library would be
 *       more robust than shelling out to the CLI.</li>
 * </ul>
 */
public class HuggingFaceFetcher {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceFetcher.class);

    /** Default model: Qwen2.5-0.5B-Instruct (per CONSTITUTION). */
    public static final String DEFAULT_MODEL = "Qwen/Qwen2.5-0.5B-Instruct";

    /** Default local cache directory. */
    public static final Path DEFAULT_CACHE = Path.of("models/hf_cache/qwen05b");

    private final Path cacheDir;
    private final String modelId;
    private final AtomicReference<String> lastOutput = new AtomicReference<>();
    private final AtomicLong downloadCount = new AtomicLong();
    private final AtomicLong fetchFailureCount = new AtomicLong();
    private volatile boolean available = false;

    public HuggingFaceFetcher() {
        this(DEFAULT_MODEL, DEFAULT_CACHE);
    }

    public HuggingFaceFetcher(String modelId, Path cacheDir) {
        this.modelId = modelId;
        this.cacheDir = cacheDir;
    }


    /**
     * Check whether the local cache has the model artifacts.
     *
     * @return true if config.json + model.safetensors (or model.bin)
     *         are present
     */
    public synchronized boolean isAvailable() {
        if (available) return true;
        Path config = cacheDir.resolve("config.json");
        Path safetensors = cacheDir.resolve("model.safetensors");
        Path bin = cacheDir.resolve("model.bin");
        boolean hasConfig = Files.isRegularFile(config);
        boolean hasWeights = Files.isRegularFile(safetensors) || Files.isRegularFile(bin);
        available = hasConfig && hasWeights;
        return available;
    }

    /**
     * Download the model via the `hf` CLI. Returns true on success.
     *
     * <p>This shells out to `hf download`, which respects the user's
     * stored credentials (login token, etc.).
     */
    public synchronized boolean fetch() {
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            lastOutput.set("mkdir failed: " + e.getMessage());
            fetchFailureCount.incrementAndGet();
            return false;
        }

        ProcessBuilder pb = new ProcessBuilder(
                "hf", "download", modelId,
                "--local-dir", cacheDir.toString());
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exitCode = proc.waitFor();
            lastOutput.set(output);
            if (exitCode == 0) {
                downloadCount.incrementAndGet();
                available = isAvailable();
                return available;
            }
            fetchFailureCount.incrementAndGet();
            return false;
        } catch (IOException | InterruptedException e) {
            lastOutput.set("fetch failed: " + e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            fetchFailureCount.incrementAndGet();
            return false;
        }
    }

    public Path cacheDir() { return cacheDir; }
    public String modelId() { return modelId; }
    public String lastOutput() { return lastOutput.get(); }
    public long downloadCount() { return downloadCount.get(); }
    public long fetchFailureCount() { return fetchFailureCount.get(); }

    /** RUN 56 — startup hook: ensure model is downloaded before serving requests. */
    void onStart(@Observes StartupEvent ev) {
        if (isAvailable()) {
            log.info("HuggingFaceFetcher: model already cached at {}", cacheDir);
            return;
        }
        log.info("HuggingFaceFetcher: model not cached, attempting fetch of {} → {}",
                modelId, cacheDir);
        boolean ok = fetch();
        if (ok) {
            log.info("HuggingFaceFetcher: download successful");
        } else {
            log.warn("HuggingFaceFetcher: download failed (output={})",
                    lastOutput() == null ? "<none>" : lastOutput().substring(
                            0, Math.min(200, lastOutput().length())));
        }
    }
}
