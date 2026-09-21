package io.matrix.api;

import io.matrix.imports.BooleanChainProducer;
import io.matrix.imports.ChainStateStore;
import io.matrix.memory.HierarchicalMemory;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * State endpoint (Priority 4 — Knowledge UI): exposes the
 * matrix's internal state for visibility. Lets the user see what
 * models are loaded, how many neurons are active, how many LTM
 * entries exist, whether the chain has been trained, etc.
 *
 * <p>Designed to make the "distilled weights = MATRIX's internal
 * data" architecture visible: every loaded model contributes to
 * the same chain; this endpoint shows that contribution.
 */
@Path("/v1/state")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class StateEndpoint {

    @Inject
    BooleanChainProducer chainProducer;

    @Inject
    HierarchicalMemory ltm;

    @Inject
    ChainStateStore chainState;

    void onStart(@Observes StartupEvent ev) {
        // no-op
    }

    @GET
    public Map<String, Object> state(@QueryParam("limit") Integer limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chain_model", chainProducer.runner().modelName());
        body.put("chain_source", chainProducer.runner().sourcePath());
        body.put("chain_layers", chainProducer.runner().layerCount());
        body.put("chain_neurons", chainProducer.runner().totalNeurons());
        body.put("chain_evals", chainProducer.runner().totalEvalCount());
        body.put("chain_avg_eval_micros", chainProducer.runner().avgEvalMicros());
        body.put("chain_restored_from_disk", chainState.isRestored());
        body.put("ltm_corpus_size", ltm != null ? ltm.size() : 0);
        body.put("ltm_levels", ltm != null ? ltm.levelStats() : Map.of());
        body.put("ltm_recent", recentLtmEntries(limit != null ? Math.min(limit, 50) : 10));
        return body;
    }

    @GET
    @Path("/compact")
    public Map<String, Object> compact() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chain", String.format("%d layers / %d neurons",
                chainProducer.runner().layerCount(),
                chainProducer.runner().totalNeurons()));
        body.put("ltm", ltm != null ? ltm.size() + " entries" : "0 entries");
        body.put("restored", chainState.isRestored());
        return body;
    }

    private java.util.List<String> recentLtmEntries(int limit) {
        if (ltm == null) return java.util.List.of();
        return java.util.List.of();
    }
}