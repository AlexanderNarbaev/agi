package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;
import io.matrix.brain.runtime.PersistentHdcStore;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * MIND-W2 — Stage 7: HDC vector retrieval (persistent).
 *
 * <p>Hyperdimensional memory backed by {@link PersistentHdcStore} so taught
 * facts survive JVM restarts. The store is loaded eagerly at construction
 * and re-saved atomically on every teach. Cosine similarity is computed
 * bit-by-bit (Hamming/Jaccard) over 256-bit hypervectors.</p>
 *
 * <p>CONSTITUTION Article III — deterministic BitSet hash; identical input
 * yields identical vector; identical store content yields identical NDJSON
 * bytes (sorted indices).</p>
 */
public final class HdcRetrievalStage {

    public static final int DIM = 256;

    private final PersistentHdcStore store;

    /** In-memory mode (no persistence) — used by CI mode and tests. */
    public HdcRetrievalStage() {
        this.store = null;
        seedInMemory();
    }

    /** Persistent mode — wired by MindCycle when a storage path is provided. */
    public HdcRetrievalStage(PersistentHdcStore store) {
        this.store = store;
        if (store != null && store.size() == 0) seedPersistent();
    }

    /** Teach a fact to the persistent store. Returns the contradiction report. */
    public PersistentHdcStore.ContradictionReport teach(String id, String content) {
        if (store == null) throw new IllegalStateException("teach requires persistent store");
        PersistentHdcStore.ContradictionReport r = store.checkContradiction(id, content);
        store.teach(id, content);
        return r;
    }

    /** Number of stored facts (persistent or in-memory). */
    public int size() {
        return store != null ? store.size() : inMemoryVectors.size();
    }

    public record HdcResult(boolean matched, String reply, double confidence) {
        public static HdcResult miss() {
            return new HdcResult(false, "", 0.0);
        }
        public static HdcResult hit(String reply, double confidence) {
            return new HdcResult(true, reply, confidence);
        }
    }

    public HdcResult retrieve(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        if (store != null) return retrievePersistent(input, obs, trace);
        return retrieveInMemory(input, obs, trace);
    }

    private HdcResult retrievePersistent(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        if (store.size() == 0) {
            trace.add(BrcStep.of("HDC_MEMORY", false, 0.50,
                List.of("reason=empty-store", "mode=persistent")));
            return HdcResult.miss();
        }
        BitSet query = obs.features();
        Map<String, BitSet> vectors = store.vectors();
        Map<String, String> contents = store.snapshot();
        double bestScore = 0.0;
        String bestId = null;
        String bestContent = null;
        List<String> topIds = new ArrayList<>();
        for (Map.Entry<String, BitSet> e : vectors.entrySet()) {
            double sim = PersistentHdcStore.cosine(query, e.getValue());
            if (sim > bestScore) {
                bestScore = sim;
                bestId = e.getKey();
                bestContent = contents.get(e.getKey());
            }
            if (topIds.size() < 3) topIds.add(e.getKey() + ":" + String.format("%.2f", sim));
        }
        if (bestScore < 0.55 || bestId == null) {
            trace.add(BrcStep.of("HDC_MEMORY", false, bestScore,
                List.of("mode=persistent", "best=" + bestScore, "top=" + topIds)));
            return HdcResult.miss();
        }
        trace.add(BrcStep.of("HDC_MEMORY", true, bestScore,
            List.of("mode=persistent", "best=" + bestId, "top=" + topIds)));
        return HdcResult.hit(bestContent, bestScore);
    }

    // -----------------------------------------------------------------
    // In-memory mode (CI / tests)
    // -----------------------------------------------------------------

    private final java.util.Map<String, BitSet> inMemoryVectors = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> inMemoryContents = new java.util.concurrent.ConcurrentHashMap<>();

    private void seedInMemory() {
        seed("seed-capital-france", "Paris is the capital of France");
        seed("seed-capital-japan", "Tokyo is the capital of Japan");
        seed("seed-capital-russia", "Moscow is the capital of Russia");
        seed("seed-color-sky", "The sky appears blue during daytime due to Rayleigh scattering");
        seed("seed-planet-count", "There are eight planets in the solar system");
        seed("seed-ai-definition", "AI is the field of building systems that perform tasks requiring intelligence when done by humans");
    }

    private void seedPersistent() {
        // Seed a minimal corpus so the persistent mode also has something to retrieve
        // on first boot. Subsequent teach() calls persist atomically.
        java.util.Map<String, String> seed = new java.util.LinkedHashMap<>();
        seed.put("seed-capital-france", "Paris is the capital of France");
        seed.put("seed-capital-japan", "Tokyo is the capital of Japan");
        seed.put("seed-capital-russia", "Moscow is the capital of Russia");
        seed.put("seed-color-sky", "The sky appears blue during daytime due to Rayleigh scattering");
        seed.put("seed-planet-count", "There are eight planets in the solar system");
        seed.put("seed-ai-definition", "AI is the field of building systems that perform tasks requiring intelligence when done by humans");
        store.teachAll(seed);
    }

    private void seed(String id, String content) {
        inMemoryVectors.put(id, PersistentHdcStore.hashToVector(content, DIM));
        inMemoryContents.put(id, content);
    }

    private HdcResult retrieveInMemory(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        if (inMemoryVectors.isEmpty()) {
            trace.add(BrcStep.of("HDC_MEMORY", false, 0.50,
                List.of("reason=empty-store", "mode=in-memory")));
            return HdcResult.miss();
        }
        BitSet query = obs.features();
        double bestScore = 0.0;
        String bestId = null;
        String bestContent = null;
        List<String> topIds = new ArrayList<>();
        for (Map.Entry<String, BitSet> e : inMemoryVectors.entrySet()) {
            double sim = PersistentHdcStore.cosine(query, e.getValue());
            if (sim > bestScore) {
                bestScore = sim;
                bestId = e.getKey();
                bestContent = inMemoryContents.get(e.getKey());
            }
            if (topIds.size() < 3) topIds.add(e.getKey() + ":" + String.format("%.2f", sim));
        }
        if (bestScore < 0.55 || bestId == null) {
            trace.add(BrcStep.of("HDC_MEMORY", false, bestScore,
                List.of("mode=in-memory", "best=" + bestScore, "top=" + topIds)));
            return HdcResult.miss();
        }
        trace.add(BrcStep.of("HDC_MEMORY", true, bestScore,
            List.of("mode=in-memory", "best=" + bestId, "top=" + topIds)));
        return HdcResult.hit(bestContent, bestScore);
    }
}
