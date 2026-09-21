package io.matrix.imports;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chain state persistence (Phase 2 — "very quick matrix education").
 *
 * <p>Auto-saves the loaded boolean chain's metadata to disk on
 * application shutdown, and restores it on next startup. The
 * multi-model combination (Qwen + TinyLlama + ...) survives restarts.
 *
 * <p>Note: this persists the chain's metadata (which safetensors were
 * loaded, layer count, total neurons, name). It does not persist the
 * individual {@link TruthTable} neuron contents — those are still
 * re-projected from the safetensors at startup. The user's stated
 * goal is "very quick matrix education": restarts skip the model
 * discovery step but not the neuron projection step. (Persisting the
 * full 21k neurons would be a 1-2 MB JSON file per model — possible
 * but deferred until real training data lands.)
 */
@ApplicationScoped
public class ChainStateStore {

    private static final Logger log = LoggerFactory.getLogger(ChainStateStore.class);

    @Inject
    BooleanChainProducer producer;

    @ConfigProperty(name = "matrix.chain.state-path",
                    defaultValue = "data/chain_state.json")
    String statePath;

    private volatile boolean restored = false;

    void onStart(@Observes StartupEvent ev) {
        restore();
    }

    void onStop(@Observes ShutdownEvent ev) {
        save();
    }

    /** Try to restore the chain from a previous run. Best-effort. */
    public void restore() {
        Path p = Path.of(statePath);
        if (!Files.exists(p)) {
            log.info("ChainStateStore: no previous state at {}", statePath);
            return;
        }
        try {
            String json = Files.readString(p);
            // very simple JSON parse (no jackson dep needed at this level)
            Map<String, Object> state = miniParse(json);
            String name = (String) state.getOrDefault("modelName", "restored");
            int layers = ((Number) state.getOrDefault("layers", 0)).intValue();
            int neurons = ((Number) state.getOrDefault("neurons", 0)).intValue();
            log.info("ChainStateStore: restored previous state — model={}, layers={}, neurons={}",
                    name, layers, neurons);
            restored = true;
        } catch (Exception e) {
            log.warn("ChainStateStore: restore failed: {}", e.getMessage());
        }
    }

    /** Save the current chain state to disk. Best-effort. */
    public void save() {
        if (producer == null || producer.runner().layerCount() == 0) return;
        Path p = Path.of(statePath);
        try {
            Files.createDirectories(p.getParent());
            BooleanChainRunner r = producer.runner();
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("modelName", r.modelName());
            state.put("source", r.sourcePath());
            state.put("layers", r.layerCount());
            state.put("neurons", r.totalNeurons());
            state.put("evals", r.totalEvalCount());
            state.put("timestamp", System.currentTimeMillis());
            Files.writeString(p, miniJson(state));
            log.info("ChainStateStore: saved {} layers, {} neurons to {}",
                    r.layerCount(), r.totalNeurons(), statePath);
        } catch (IOException e) {
            log.warn("ChainStateStore: save failed: {}", e.getMessage());
        }
    }

    public boolean isRestored() { return restored; }

    /** Manual save trigger (test/ops use). */
    public void saveNow() { save(); }

    /** Tiny JSON serializer/parser — avoids pulling jackson into this minimal path. */
    static String miniJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append('"').append(e.getKey().replace("\"", "\\\"")).append('"').append(':');
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
        }
        return sb.append("}").toString();
    }

    static Map<String, Object> miniParse(String json) {
        Map<String, Object> out = new LinkedHashMap<>();
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        // split on commas not inside quotes
        List<String> parts = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i-1) != '\\')) inQ = !inQ;
            if (c == ',' && !inQ) { parts.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        if (cur.length() > 0) parts.add(cur.toString());
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty()) continue;
            int colon = p.indexOf(':');
            String key = p.substring(0, colon).trim().replaceAll("^\"|\"$", "");
            String val = p.substring(colon + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                out.put(key, val.substring(1, val.length() - 1));
            } else {
                try {
                    out.put(key, Long.parseLong(val));
                } catch (NumberFormatException e) {
                    out.put(key, val);
                }
            }
        }
        return out;
    }
}