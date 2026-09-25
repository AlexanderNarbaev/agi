package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MIND-W1 — Stage 7: HDC vector retrieval.
 *
 * <p>Hyperdimensional memory with bit-level cosine similarity. For MIND-W1
 * the store is in-memory and seeded with a tiny corpus so the BRC trace
 * is meaningful; W2 replaces this with {@code PersistentHierarchicalMemory}
 * backed by SQLite.</p>
 *
 * <p>Each stored fact is a 256-bit hypervector computed from the same
 * FNV-1a hashing used by {@link SignalStage}. Cosine similarity is computed
 * bit-by-bit (Hamming distance over 256 bits).</p>
 */
public final class HdcRetrievalStage {

    public static final int DIM = 256;

    /** id -> hypervector (BitSet) */
    private final Map<String, BitSet> vectors = new ConcurrentHashMap<>();
    /** id -> text content */
    private final Map<String, String> contents = new LinkedHashMap<>();

    public HdcRetrievalStage() {
        seed();
    }

    /** Add a fact taught via /v1/teach. */
    public void teach(String id, String content) {
        BitSet v = hashToVector(content);
        vectors.put(id, v);
        contents.put(id, content);
    }

    public int size() {
        return vectors.size();
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
        if (vectors.isEmpty()) {
            trace.add(BrcStep.of("HDC_MEMORY", false, 0.50,
                List.of("reason=empty-store")));
            return HdcResult.miss();
        }
        BitSet query = obs.features();
        double bestScore = 0.0;
        String bestId = null;
        String bestContent = null;
        List<String> topIds = new ArrayList<>();
        for (Map.Entry<String, BitSet> e : vectors.entrySet()) {
            double sim = cosine(query, e.getValue());
            if (sim > bestScore) {
                bestScore = sim;
                bestId = e.getKey();
                bestContent = contents.get(e.getKey());
            }
            if (topIds.size() < 3) {
                topIds.add(e.getKey() + ":" + String.format("%.2f", sim));
            }
        }
        if (bestScore < 0.55 || bestId == null) {
            trace.add(BrcStep.of("HDC_MEMORY", false, bestScore,
                List.of("best=" + bestScore, "top=" + topIds)));
            return HdcResult.miss();
        }
        trace.add(BrcStep.of("HDC_MEMORY", true, bestScore,
            List.of("best=" + bestId, "top=" + topIds)));
        return HdcResult.hit(bestContent, bestScore);
    }

    // ---------------------------------------------------------------------
    // HDC math
    // ---------------------------------------------------------------------

    public static BitSet hashToVector(String text) {
        BitSet bs = new BitSet(DIM);
        for (String tok : text.toLowerCase().split("\\s+")) {
            if (tok.isBlank()) continue;
            int h = 0x811c9dc5;
            for (int i = 0; i < tok.length(); i++) {
                h ^= tok.charAt(i);
                h *= 0x01000193;
            }
            bs.set((h & 0x7fffffff) % DIM);
        }
        return bs;
    }

    /** Bit-cosine (Jaccard on bitsets). */
    public static double cosine(BitSet a, BitSet b) {
        BitSet inter = (BitSet) a.clone();
        inter.and(b);
        BitSet union = (BitSet) a.clone();
        union.or(b);
        int i = inter.cardinality();
        int u = union.cardinality();
        return u == 0 ? 0.0 : (double) i / (double) u;
    }

    /** Seed a minimal corpus so MIND-W1 demos work. */
    private void seed() {
        teach("seed-capital-france", "Paris is the capital of France");
        teach("seed-capital-japan", "Tokyo is the capital of Japan");
        teach("seed-capital-russia", "Moscow is the capital of Russia");
        teach("seed-color-sky", "The sky appears blue during daytime due to Rayleigh scattering");
        teach("seed-planet-count", "There are eight planets in the solar system");
        teach("seed-ai-definition", "AI is the field of building systems that perform tasks requiring intelligence when done by humans");
    }
}
