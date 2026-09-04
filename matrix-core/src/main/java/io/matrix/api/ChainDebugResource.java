package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.TruthTableLayer;
import io.matrix.neuron.TruthTable;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/v1/chain-debug")
@Produces(MediaType.APPLICATION_JSON)
public class ChainDebugResource {
    @Inject BooleanChainRunner chainRunner;

    @GET
    @Path("/neuron")
    public Map<String, Object> inspect(
            @QueryParam("layer") int layerIdx,
            @QueryParam("neuron") int neuronIdx) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("layer", layerIdx);
        resp.put("neuron", neuronIdx);
        var layers = chainRunner.layers();
        if (layerIdx < 0 || layerIdx >= layers.size()) {
            resp.put("error", "layer out of range");
            return resp;
        }
        TruthTableLayer layer = layers.get(layerIdx);
        if (neuronIdx < 0 || neuronIdx >= layer.neuronCount()) {
            resp.put("error", "neuron out of range");
            return resp;
        }
        TruthTable tt = layer.neurons().get(neuronIdx);
        BitSet bs = tt.table();
        int k = tt.k();
        int cardinality = bs.cardinality();
        int cells = 1 << k;
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < Math.min(8, (cells + 63) / 64); i++) {
            String h = Long.toHexString(bs.toLongArray().length > i ? bs.toLongArray()[i] : 0);
            hex.append(h);
        }
        resp.put("k", k);
        resp.put("cardinality", cardinality);
        resp.put("cells", cells);
        resp.put("table_hash_hex", hex.toString());
        resp.put("table_cardinality_pct", String.format("%.1f%%", 100.0 * cardinality / cells));
        return resp;
    }

    @GET
    @Path("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total_layers", chainRunner.layers().size());
        resp.put("total_neurons", chainRunner.totalNeurons());

        int empty = 0, totalCards = 0, totalCells = 0;
        for (TruthTableLayer layer : chainRunner.layers()) {
            for (TruthTable tt : layer.neurons()) {
                int cells = 1 << tt.k();
                int card = tt.table().cardinality();
                totalCards += card;
                totalCells += cells;
                if (card == 0) empty++;
            }
        }
        resp.put("empty_neurons", empty);
        resp.put("total_cardinality", totalCards);
        resp.put("total_cells", totalCells);
        resp.put("avg_density_pct", String.format("%.1f%%",
                totalCells == 0 ? 0.0 : 100.0 * totalCards / totalCells));
        return resp;
    }

    @GET
    @Path("/evaluate")
    public Map<String, Object> eval(@QueryParam("input") String input) {
        boolean[] in = new boolean[input == null ? 0 : input.length()];
        for (int i = 0; i < in.length; i++) in[i] = input.charAt(i) == '1';
        boolean[] out = chainRunner.evaluate(in);
        int card = 0;
        int total = out.length;
        StringBuilder bits = new StringBuilder();
        for (int i = 0; i < Math.min(64, total); i++) {
            bits.append(out[i] ? '1' : '0');
            if (out[i]) card++;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("input_bits", total);
        resp.put("output_bits", total);
        resp.put("output_cardinality", card);
        resp.put("output_preview", bits.toString());
        return resp;
    }

    @GET
    @Path("/evaluate-java")
    public Map<String, Object> evalJava(@QueryParam("input") String input) {
        boolean[] in = new boolean[input == null ? 0 : input.length()];
        for (int i = 0; i < in.length; i++) in[i] = input.charAt(i) == '1';
        // force Java path by calling evaluateWithScore directly
        BooleanChainRunner.ChainResult result = chainRunner.evaluateWithScore(in);
        boolean[] out = result.bits();
        int card = 0;
        int total = out.length;
        StringBuilder bits = new StringBuilder();
        for (int i = 0; i < Math.min(64, total); i++) {
            bits.append(out[i] ? '1' : '0');
            if (out[i]) card++;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("input_bits", total);
        resp.put("output_bits", total);
        resp.put("output_cardinality", card);
        resp.put("output_preview", bits.toString());
        resp.put("neurons_fired", result.neuronsFired());
        resp.put("weighted_score", result.weightedScore());
        return resp;
    }
}
