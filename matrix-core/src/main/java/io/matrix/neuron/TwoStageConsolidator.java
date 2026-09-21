package io.matrix.neuron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 469 — TwoStageConsolidator: hippocampus↔neocortex replay (DESIGN-60).
 *
 * <p>Implements the Squire-Alvarez (1995) standard model + McClelland-McNaughton-
 * O'Reilly (1995) Complementary Learning Systems (CLS) framework:
 *
 * <h2>Two-stage architecture</h2>
 * <pre>
 *   Hippocampus (fast, sparse, pattern-separated):
 *     - Stores recent experiences as discrete episodes
 *     - Fast learning, low capacity
 *     - Acts as "index" for neocortex
 *
 *   Neocortex (slow, dense, pattern-completion):
 *     - Stores accumulated knowledge as overlapping patterns
 *     - Slow learning, high capacity
 *     - Builds general semantic representations
 *
 *   Sleep replay (offline consolidation):
 *     - Re-activate hippocampus episodes
 *     - Re-activate corresponding neocortex patterns
 *     - Strengthen neocortex synapses, weaken hippocampus
 * </pre>
 *
 * <h2>MATRIX-specific implementation</h2>
 * <ul>
 *   <li>Hippocampus = small HDC codebook (recent episodes only)</li>
 *   <li>Neocortex = larger HDC codebook (all episodes, pattern-overlapping)</li>
 *   <li>Replay = re-activate hippocampus episodes, store pattern completions
 *       in neocortex, decay hippocampus</li>
 * </ul>
 *
 * <h2>CONSTITUTION I</h2>
 * No wall-clock. Caller supplies RNG for replay ordering.
 */
public final class TwoStageConsolidator {

    private TwoStageConsolidator() {}

    /**
     * Single consolidation cycle. Retrieves a few episodes from hippocampus,
     * pattern-completes in neocortex, then weakens hippocampus representations.
     */
    public static ConsolidationResult consolidate(
            MemoryStore hippocampus,
            MemoryStore neocortex,
            int replayCount,
            Random rng) {
        if (hippocampus == null || neocortex == null) {
            throw new IllegalArgumentException("null stores");
        }
        if (replayCount < 0) {
            throw new IllegalArgumentException("replayCount must be ≥ 0");
        }
        if (rng == null) throw new IllegalArgumentException("null rng");

        List<String> replayedIds = new ArrayList<>();
        double replayError = 0.0;
        int episodesReplayed = 0;

        // Step 1: Sample random episodes from hippocampus
        List<String> hippocampusKeys = new ArrayList<>(hippocampus.keys());
        if (hippocampusKeys.isEmpty()) {
            return new ConsolidationResult(0, 0.0, List.of());
        }

        for (int i = 0; i < replayCount && !hippocampusKeys.isEmpty(); i++) {
            String id = hippocampusKeys.get(rng.nextInt(hippocampusKeys.size()));
            replayedIds.add(id);
            episodesReplayed++;

            // Step 2: Get hippocampal pattern (compressed episode)
            float[] hippocampalPattern = hippocampus.retrieve(id);
            if (hippocampalPattern == null) continue;

            // Step 3: Pattern-complete in neocortex via nearest-neighbor blending
            // Real brains use interleaved replay over many episodes; we approximate
            // with single-episode consolidation.
            String similarId = neocortex.findMostSimilar(hippocampalPattern);
            float[] neocortexPattern;
            if (similarId == null) {
                // Store as new pattern
                neocortexPattern = hippocampalPattern.clone();
            } else {
                neocortexPattern = neocortex.retrieve(similarId);
                if (neocortexPattern == null) {
                    neocortexPattern = hippocampalPattern.clone();
                } else {
                    // Average: 50% hippocampal + 50% neocortex
                    for (int d = 0; d < neocortexPattern.length; d++) {
                        neocortexPattern[d] = 0.5f * neocortexPattern[d]
                                + 0.5f * hippocampalPattern[d];
                    }
                }
            }

            // Step 4: Compute replay error (difference between blended and hippocampal)
            double episodeReplayError = 0.0;
            for (int d = 0; d < neocortexPattern.length; d++) {
                double diff = neocortexPattern[d] - hippocampalPattern[d];
                episodeReplayError += diff * diff;
            }
            episodeReplayError = Math.sqrt(episodeReplayError / neocortexPattern.length);
            replayError += episodeReplayError;

            // Step 5: Update neocortex with blended pattern
            if (similarId != null) {
                neocortex.store(similarId, neocortexPattern);
            } else {
                neocortex.store(id, neocortexPattern);
            }

            // Step 6: Decay hippocampus (transfer to neocortex weakens hippocampal)
            hippocampus.decay(id, 0.1f);
        }

        return new ConsolidationResult(episodesReplayed, replayError, replayedIds);
    }

    /**
     * Generic memory store interface.
     */
    public interface MemoryStore {
        /**
         * Get all keys.
         */
        java.util.Set<String> keys();

        /**
         * Retrieve a pattern by key. Returns null if not found.
         */
        float[] retrieve(String key);

        /**
         * Store a pattern under a key (overwrites if exists).
         */
        void store(String key, float[] pattern);

        /**
         * Decay a pattern by a factor (0 = no decay, 1 = full decay).
         */
        void decay(String key, float factor);

        /**
         * Find the most similar pattern to the given one. Returns null if empty.
         */
        String findMostSimilar(float[] pattern);
    }

    /**
     * Simple HDC-backed memory store for consolidation testing.
     */
    public static final class HdcMemoryStore implements MemoryStore {
        private final Map<String, float[]> store = new HashMap<>();
        private final int dims;

        public HdcMemoryStore(int dims) {
            if (dims < 1) throw new IllegalArgumentException("dims < 1");
            this.dims = dims;
        }

        @Override
        public java.util.Set<String> keys() {
            return store.keySet();
        }

        @Override
        public float[] retrieve(String key) {
            float[] p = store.get(key);
            return p == null ? null : p.clone();
        }

        @Override
        public void store(String key, float[] pattern) {
            if (pattern == null) throw new IllegalArgumentException("null pattern");
            if (pattern.length != dims) {
                throw new IllegalArgumentException("pattern length mismatch: "
                        + pattern.length + " != " + dims);
            }
            store.put(key, pattern.clone());
        }

        @Override
        public void decay(String key, float factor) {
            float[] p = store.get(key);
            if (p != null) {
                for (int i = 0; i < p.length; i++) p[i] *= (1.0f - factor);
            }
        }

        @Override
        public String findMostSimilar(float[] pattern) {
            if (pattern == null || store.isEmpty()) return null;
            String best = null;
            double bestSim = Double.NEGATIVE_INFINITY;
            for (Map.Entry<String, float[]> e : store.entrySet()) {
                float[] p = e.getValue();
                // Cosine similarity
                double dot = 0, normA = 0, normB = 0;
                for (int i = 0; i < pattern.length; i++) {
                    dot += pattern[i] * p[i];
                    normA += pattern[i] * pattern[i];
                    normB += p[i] * p[i];
                }
                double sim = dot / (Math.sqrt(normA * normB) + 1e-9);
                if (sim > bestSim) {
                    bestSim = sim;
                    best = e.getKey();
                }
            }
            return best;
        }
    }

    /**
     * Result of one consolidation cycle.
     */
    public record ConsolidationResult(
            int episodesReplayed,
            double replayError,
            List<String> replayedIds) {}
}
