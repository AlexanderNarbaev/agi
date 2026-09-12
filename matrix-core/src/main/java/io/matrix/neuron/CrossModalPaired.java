package io.matrix.neuron;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RUN 445 — Cross-modal paired binding (DESIGN-58 Level 3).
 *
 * <p>Binds two modalities (e.g., audio and visual features) into a single
 * HDC record, allowing inference across modalities. Given an audio cue,
 * retrieve the visual features; given visual, retrieve audio.
 *
 * <h2>Use case</h2>
 * Mithen (1996) "The Prehistory of the Mind" argues that cross-modal
 * integration is a key cognitive milestone enabling language, art, and
 * tool use. By storing audio-visual pairs as HDC records (bind of
 * audio code and visual code), the brain can:
 * <ul>
 *   <li>Retrieve visual from audio (auditory-visual inference)</li>
 *   <li>Retrieve audio from visual (visual-auditory inference)</li>
 *   <li>Detect novel pairings via Hamming distance</li>
 * </ul>
 *
 * <h2>API</h2>
 * <pre>
 *   CrossModalPaired cmp = new CrossModalPaired(100, new Random(42));
 *   cmp.pair(audioTemplate, visualTemplate, "ball");
 *   long[] recalledVisual = cmp.retrieveVisual(audioTemplate);
 *   long[] recalledAudio = cmp.retrieveAudio(visualTemplate);
 *   double similarity = cmp.pairSimilarity(audioTemplate, visualTemplate);
 * </pre>
 *
 * <h2>CONSTITUTION I</h2>
 * Caller supplies RNG. All operations deterministic given inputs.
 */
public final class CrossModalPaired {

    /** Stored per-pair memory: audio code, visual code, label, and bound record. */
    public static final class PairMemory {
        public final String label;
        public final long[] audioCode;
        public final long[] visualCode;
        public final long[] boundRecord; // bind(audioCode, visualCode)

        public PairMemory(String label, long[] audioCode, long[] visualCode,
                           long[] boundRecord) {
            this.label = label;
            this.audioCode = audioCode;
            this.visualCode = visualCode;
            this.boundRecord = boundRecord;
        }
    }

    private final Map<String, PairMemory> pairs;
    private final Random rng;

    /**
     * Create a new cross-modal store.
     */
    public CrossModalPaired(int maxPairs, Random rng) {
        if (maxPairs < 1) {
            throw new IllegalArgumentException("maxPairs must be ≥ 1");
        }
        if (rng == null) throw new IllegalArgumentException("null rng");
        this.pairs = new HashMap<>();
        this.rng = rng;
    }

    /**
     * Pair an audio template with a visual template under a label.
     * Stores the bind(audioCode, visualCode) record for retrieval.
     */
    public void pair(long[] audioTemplate, long[] visualTemplate, String label) {
        if (audioTemplate == null || audioTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("audioTemplate wrong length");
        }
        if (visualTemplate == null || visualTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("visualTemplate wrong length");
        }
        if (label == null) throw new IllegalArgumentException("null label");
        long[] audioCode = encode(audioTemplate);
        long[] visualCode = encode(visualTemplate);
        long[] bound = HdcBinding.bind(audioCode, visualCode);
        pairs.put(label, new PairMemory(label, audioCode, visualCode, bound));
    }

    /**
     * Given an audio template, retrieve the visual code of the matching
     * pair (nearest by Hamming distance). Returns null if no pairs.
     */
    public long[] retrieveVisual(long[] audioTemplate) {
        if (audioTemplate == null || audioTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("audioTemplate wrong length");
        }
        if (pairs.isEmpty()) return null;
        long[] audioCode = encode(audioTemplate);
        String bestLabel = null;
        long[] bestVisual = null;
        int bestDist = Integer.MAX_VALUE;
        for (PairMemory m : pairs.values()) {
            // Unbind: bound ⊕ audioCode = visualCode
            long[] candidateVisual = HdcBinding.unbind(m.boundRecord, audioCode);
            int d = HdcEncoding.hamming(candidateVisual, m.visualCode);
            if (d < bestDist) {
                bestDist = d;
                bestLabel = m.label;
                bestVisual = m.visualCode;
            }
        }
        return bestVisual;
    }

    /**
     * Given a visual template, retrieve the audio code.
     */
    public long[] retrieveAudio(long[] visualTemplate) {
        if (visualTemplate == null || visualTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("visualTemplate wrong length");
        }
        if (pairs.isEmpty()) return null;
        long[] visualCode = encode(visualTemplate);
        String bestLabel = null;
        long[] bestAudio = null;
        int bestDist = Integer.MAX_VALUE;
        for (PairMemory m : pairs.values()) {
            long[] candidateAudio = HdcBinding.unbind(m.boundRecord, visualCode);
            int d = HdcEncoding.hamming(candidateAudio, m.audioCode);
            if (d < bestDist) {
                bestDist = d;
                bestLabel = m.label;
                bestAudio = m.audioCode;
            }
        }
        return bestAudio;
    }

    /**
     * Compute cross-modal similarity for a (audio, visual) query: highest
     * similarity of the bound record to any stored pair.
     *
     * <p>Returns similarity in [-1, +1]. +1 = exact match.
     */
    public double pairSimilarity(long[] audioTemplate, long[] visualTemplate) {
        if (audioTemplate == null || audioTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("audioTemplate wrong length");
        }
        if (visualTemplate == null || visualTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("visualTemplate wrong length");
        }
        if (pairs.isEmpty()) return 0.0;
        long[] audioCode = encode(audioTemplate);
        long[] visualCode = encode(visualTemplate);
        long[] queryBind = HdcBinding.bind(audioCode, visualCode);
        double bestSim = -2.0;
        for (PairMemory m : pairs.values()) {
            double sim = HdcEncoding.similarity(queryBind, m.boundRecord);
            if (sim > bestSim) bestSim = sim;
        }
        return bestSim;
    }

    /**
     * Find the label whose audio code is nearest to a query audio.
     */
    public String nearestAudioLabel(long[] audioTemplate) {
        if (audioTemplate == null || audioTemplate.length != HdcEncoding.WORDS) {
            throw new IllegalArgumentException("audioTemplate wrong length");
        }
        if (pairs.isEmpty()) return null;
        long[] audioCode = encode(audioTemplate);
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (PairMemory m : pairs.values()) {
            int d = HdcEncoding.hamming(audioCode, m.audioCode);
            if (d < bestDist) {
                bestDist = d;
                best = m.label;
            }
        }
        return best;
    }

    /**
     * Number of stored pairs.
     */
    public int size() {
        return pairs.size();
    }

    /**
     * All labels.
     */
    public java.util.Set<String> labels() {
        return new java.util.LinkedHashSet<>(pairs.keySet());
    }

    /**
     * Direct access to a stored pair.
     */
    public PairMemory get(String label) {
        return pairs.get(label);
    }

    /**
     * Remove a pair.
     */
    public boolean remove(String label) {
        return pairs.remove(label) != null;
    }

    private long[] encode(long[] template) {
        // Simple encoding: spread template bits into bipolar code
        long[] code = template.clone();
        return code;
    }
}
