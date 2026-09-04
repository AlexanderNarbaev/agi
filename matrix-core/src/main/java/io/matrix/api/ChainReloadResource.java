package io.matrix.api;

import io.matrix.imports.BooleanChainProducer;
import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.ChainStateStore;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RUN 9.7 — Chain reload endpoint.
 *
 * <p>Rebuilds the running boolean chain from safetensors without
 * restarting the JVM. Useful when:
 * <ul>
 *   <li>Training has overfit and you want to start over (RUN 9.5
 *       mode collapse)</li>
 *   <li>Someone replaced the source safetensors on disk</li>
 *   <li>You want to swap between models at runtime</li>
 * </ul>
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>from-source</b> (default) — reload from the original safetensors
 *       path the runner was built from</li>
 *   <li><b>discard-state</b> — delete {@code data/chain_state.json} so
 *       the NEXT restart loads from safetensors</li>
 * </ul>
 *
 * <p>POST /v1/chain/reload?mode=from-source  → rebuilds in place
 * <br>POST /v1/chain/reload?mode=discard-state → deletes state file
 */
@jakarta.ws.rs.Path("/v1/chain/reload")
@Produces(MediaType.APPLICATION_JSON)
public class ChainReloadResource {

    private static final Logger log = LoggerFactory.getLogger(ChainReloadResource.class);

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    BooleanChainProducer chainProducer;

    @Inject
    ChainStateStore chainState;

    @POST
    public Map<String, Object> reload(@QueryParam("mode") String mode) {
        long start = System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            return doReload(mode, body, start);
        } catch (java.io.IOException e) {
            body.put("error", "io: " + e.getMessage());
            return body;
        }
    }

    private Map<String, Object> doReload(String mode, Map<String, Object> body, long start)
            throws java.io.IOException {

        if (mode == null || "from-source".equalsIgnoreCase(mode)) {
            // Build a brand-new chain from safetensors and atomically swap
            BooleanChainRunner fresh = chainProducer.build();
            chainRunner.replaceLayers(fresh.layers());
            body.put("mode", "from-source");
            body.put("source", fresh.sourcePath());
            body.put("model", fresh.modelName());
            body.put("layers", chainRunner.layerCount());
            body.put("neurons", chainRunner.totalNeurons());
            body.put("elapsedMs", (System.nanoTime() - start) / 1_000_000L);
            log.info("Chain reloaded from source: {} layers, {} neurons",
                    chainRunner.layerCount(), chainRunner.totalNeurons());
        } else if ("discard-state".equalsIgnoreCase(mode)) {
            // Delete the persisted chain state file
            java.nio.file.Path stateFile = java.nio.file.Path.of("data/chain_state.json");
            boolean deleted = false;
            if (Files.exists(stateFile)) {
                Files.delete(stateFile);
                deleted = true;
            }
            body.put("mode", "discard-state");
            body.put("deleted", deleted);
            body.put("note", "restart server to load fresh from safetensors");
        } else {
            body.put("error", "unknown mode: " + mode);
            body.put("valid_modes", new String[]{"from-source", "discard-state"});
        }
        return body;
    }
}
