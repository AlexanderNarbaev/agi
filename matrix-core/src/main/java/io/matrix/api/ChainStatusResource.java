package io.matrix.api;

import io.matrix.imports.BooleanChainProducer;
import io.matrix.imports.BooleanChainRunner;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Observability endpoint for the loaded boolean chain runner.
 * Exposes layer count, neuron count, source model, and runtime stats.
 */
@Path("/v1/chain-status")
@Produces(MediaType.APPLICATION_JSON)
public class ChainStatusResource {

    @Inject
    BooleanChainRunner chainRunner;

    @GET
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chainRunner.modelName());
        body.put("source", chainRunner.sourcePath());
        body.put("layers", chainRunner.layerCount());
        body.put("totalNeurons", chainRunner.totalNeurons());
        body.put("totalEvals", chainRunner.totalEvalCount());
        body.put("avgEvalMicros", chainRunner.avgEvalMicros());
        body.put("empty", chainRunner.layerCount() == 0);
        return body;
    }
}