package io.matrix.imports;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CDI producer for {@link BooleanChainRunner} (Wave A).
 *
 * <p>Loads the chain from a configurable safetensors path at startup.
 * If the path is not set or doesn't exist, returns {@link
 * BooleanChainRunner#empty()} — the chat will fall back to
 * PureBirGenerator.
 *
 * <p>Configuration (application.properties):
 * <pre>
 *   matrix.chain.path=/path/to/model.safetensors
 *   matrix.chain.prefix=model          # default: "model"
 *   matrix.chain.budget=16384          # default: 1<<14
 * </pre>
 *
 * <p>The runner is layer-agnostic — it auto-discovers the number of
 * layers from the model's tensor names (no hardcoded "24").
 */
@ApplicationScoped
public class BooleanChainProducer {

    private static final Logger log = LoggerFactory.getLogger(BooleanChainProducer.class);

    @ConfigProperty(name = "matrix.chain.path", defaultValue = "")
    java.util.Optional<String> configuredPath;

    @ConfigProperty(name = "matrix.chain.prefix", defaultValue = "model")
    String prefix;

    @ConfigProperty(name = "matrix.chain.budget", defaultValue = "16384")
    int budget;

    @Produces
    @ApplicationScoped
    public BooleanChainRunner runner() {
        return build();
    }

    /** Build the runner (called by CDI and by tests). */
    public BooleanChainRunner build() {
        if (configuredPath.isEmpty()) {
            return autoDetect();
        }
        Path p = Path.of(configuredPath.get());
        if (!Files.isRegularFile(p)) {
            log.warn("matrix.chain.path does not exist: {} — runner empty", p);
            return BooleanChainRunner.empty();
        }
        return loadSafetensors(p, prefix, budget);
    }

    private BooleanChainRunner autoDetect() {
        // scan /tmp/opencode/matrix-import for known models
        Path[] candidates = {
                Path.of("/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots"),
                Path.of("/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B-Instruct/snapshots"),
                Path.of("/tmp/opencode/matrix-import/models--TinyLlama--TinyLlama-1.1B-Chat-v1.0/snapshots"),
                Path.of("/tmp/opencode/matrix-import/models--HuggingFaceTB--SmolLM2-360M-Instruct/snapshots"),
                Path.of("models/external/qwen2.5-0.5b/snapshots"),
                Path.of("models/external/qwen2.5-0.5b-instruct/snapshots"),
                Path.of("models/external/smollm2-360m/snapshots"),
                Path.of("models/external/tinyllama-1.1b/snapshots"),
        };
        for (Path root : candidates) {
            if (!Files.isDirectory(root)) continue;
            try {
                Path safetensors = Files.walk(root, 3)
                        .filter(p -> p.toString().endsWith(".safetensors"))
                        .filter(Files::isRegularFile)
                        .findFirst().orElse(null);
                if (safetensors == null) continue;
                log.info("auto-detected safetensors: {}", safetensors);
                return loadSafetensors(safetensors, prefix, budget);
            } catch (Exception ignored) {}
        }
        log.warn("no safetensors found; chain runner empty");
        return BooleanChainRunner.empty();
    }

    private static BooleanChainRunner loadSafetensors(Path safetensors, String prefix, int budget) {
        BooleanChainRunner r = BooleanChainRunner.loadFromSafetensors(
                safetensors, prefix, budget);
        log.info("loaded BooleanChainRunner: model={} layers={} neurons={} src={}",
                r.modelName(), r.layerCount(), r.totalNeurons(), r.sourcePath());
        return r;
    }
}