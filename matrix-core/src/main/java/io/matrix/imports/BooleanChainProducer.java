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
        BooleanChainRunner r = build();
        log.info("[DEBUG] BooleanChainProducer.runner() — layers={} neurons={}",
                r.layerCount(), r.totalNeurons());
        return r;
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
        // Wave I: load ALL transformer blocks via FullChainLoader
        return FullChainLoader.loadAll(p, budget, prefix);
    }

    private BooleanChainRunner autoDetect() {
        // Wave I + Phase 1: scan the canonical safetensors roots and
        // combine ALL models into ONE matrix instance. Layer count is
        // auto-discovered from the loaded models' tensor names — not
        // hardcoded "24".
        Path[] candidates = {
                Path.of("/tmp/opencode/matrix-import"),
                Path.of("models/external"),
                Path.of("models/external/qwen2.5-0.5b"),
        };
        for (Path root : candidates) {
            if (!Files.isDirectory(root)) continue;
            MultiModelLoader.LoadResult result = MultiModelLoader.loadFromDirectory(root);
            if (result.isEmpty()) continue;
            log.info("loaded {} model(s) — totalLayers={} totalNeurons={}",
                    result.entries().size(), result.totalLayers(), result.totalNeurons());
            return result.chain();
        }
        log.warn("no safetensors found; chain runner empty");
        return BooleanChainRunner.empty();
    }

    private static BooleanChainRunner loadSafetensors(Path safetensors, String prefix, int budget) {
        // delegate to FullChainLoader for the actual loading (Wave I)
        BooleanChainRunner r = FullChainLoader.loadAll(safetensors, budget, prefix);
        log.info("loaded BooleanChainRunner: model={} layers={} neurons={} src={}",
                r.modelName(), r.layerCount(), r.totalNeurons(), r.sourcePath());
        return r;
    }
}