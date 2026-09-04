package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.TruthTableLayer;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/v1/chain-debug")
@Produces(MediaType.APPLICATION_JSON)
public class ChainStructureResource {
    @Inject BooleanChainRunner chainRunner;

    @GET
    @Path("/structure")
    public Map<String, Object> structure() {
        Map<String, Object> resp = new LinkedHashMap<>();
        var layers = chainRunner.layers();
        resp.put("total_layers", layers.size());
        java.util.List<Integer> neuronCounts = new java.util.ArrayList<>();
        java.util.List<Integer> ks = new java.util.ArrayList<>();
        for (TruthTableLayer layer : layers) {
            neuronCounts.add(layer.neuronCount());
            ks.add(layer.k());
        }
        resp.put("layer_neuronCounts", neuronCounts);
        resp.put("layer_ks", ks);
        return resp;
    }
}
