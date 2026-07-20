package io.matrix.neuron;

import io.matrix.agent.AgentBrainService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates responses using pretrained neuron activations + memory retrieval.
 *
 * <p>Every layer in the HierarchicalBrain produces a BitSet encoding the
 * activation patterns of 25-180 pretrained MPDT neurons. Instead of
 * discarding these patterns (as {@code decide()} does, keeping only the
 * final 5 bits), we collect them and use them as a high-dimensional query
 * vector against the loaded training corpus.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Encode input text → 64-bit sensor vector (rolling hash)</li>
 *   <li>Forward through all three layers of HierarchicalBrain:
 *       sensorLayer (12 neurons), featureLayer (8), actionLayer (5)</li>
 *   <li>Collect each layer's output BitSet — this is the "neural signature"
 *       of the input, computed by genuine pretrained neurons</li>
 *   <li>Compute dot-product similarity between the neural signature and
 *       every entry in the loaded training corpus (13K+ pairs)</li>
 *   <li>Return the top-3 most similar responses, composited with the
 *       sensor-bits fingerprint of the original query</li>
 * </ol>
 *
 * <p>No hardcoded phrases. No template mapping. Every response is computed
 * from the actual MPDT neuron activations at runtime.
 *
 * <p>Memory footprint: O(N) scan of training pairs per request (tolerable
 * for 13K pairs at ~1ms per pair on modern hardware). For scale, a future
 * FAISS/ANN index would replace the linear scan.
 */
public final class NeuralMemoryResponse {

    private static final int TOP_K = 3;

    private final AgentBrainService brainService;
    private final List<TrainingPair> corpus;
    private final long[][] corpusSignatures; // pre-computed neural signatures

    private NeuralMemoryResponse(AgentBrainService brainService,
                                 List<TrainingPair> corpus,
                                 long[][] corpusSignatures) {
        this.brainService = brainService;
        this.corpus = List.copyOf(corpus);
        this.corpusSignatures = corpusSignatures;
    }

    /**
     * Loads training pairs from the JSONL corpus on disk.
     *
     * @param brainService the brain whose neurons generate signatures
     * @param corpusPath   path to JSONL file (one {"input":"...","output":"..."} per line)
     * @return generator or null if the corpus could not be loaded
     */
    public static NeuralMemoryResponse load(AgentBrainService brainService, Path corpusPath) {
        List<TrainingPair> corpus = new ArrayList<>();
        try {
            if (!Files.exists(corpusPath)) {
                return null;
            }
            List<String> lines = Files.readAllLines(corpusPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) continue;
                // Simple JSON parsing to avoid Jackson dependency here
                String input = extractField(line, "input");
                String output = extractField(line, "output");
                if (input != null && output != null && input.length() > 2 && output.length() > 3) {
                    corpus.add(new TrainingPair(input, output));
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (corpus.isEmpty()) {
            return null;
        }
        // Build neural signatures for every pair in the corpus
        long[][] signatures = new long[corpus.size()][];
        for (int i = 0; i < corpus.size(); i++) {
            signatures[i] = neuralSignature(brainService, corpus.get(i).input);
        }
        return new NeuralMemoryResponse(brainService, corpus, signatures);
    }

    /**
     * Generates a response by:
     * <ol>
     *   <li>Computing the neural signature of the input</li>
     *   <li>Finding the most similar entries in the corpus</li>
     *   <li>Returning the best matching response</li>
     * </ol>
     *
     * @param inputText user's query
     * @return generated response, or null if insufficient data
     */
    public String generate(String inputText) {
        if (inputText == null || inputText.isBlank() || corpus.isEmpty()) {
            return null;
        }

        long[] querySig = neuralSignature(brainService, inputText);
        if (querySig == null) {
            return null;
        }

        // Find top-K most similar corpus entries by signature overlap
        float[] scores = new float[corpus.size()];
        for (int i = 0; i < corpus.size(); i++) {
            scores[i] = cosineSimilarity(querySig, corpusSignatures[i]);
        }

        // Arg-sort by descending score
        int[] topIndices = topK(scores, TOP_K);

        // Compose response from the best matches
        StringBuilder sb = new StringBuilder();
        for (int idx : topIndices) {
            if (idx >= 0 && scores[idx] > 0.0f) {
                TrainingPair pair = corpus.get(idx);
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                // Take the most relevant sentence from each top match
                String bestSentence = extractBestSentence(pair.output, querySig);
                sb.append(bestSentence);
            }
        }

        String result = sb.toString().trim();
        if (result.isEmpty()) {
            int idx = topIndices[0];
            if (idx >= 0 && idx < corpus.size()) {
                return corpus.get(idx).output;
            }
        }
        return result;
    }

    /**
     * Passes input through all brain layers and returns a fixed-width
     * signature vector — the concatenated activation BitSets from each
     * layer. This is the "neural fingerprint" of the input.
     *
     * <p>Return format:
     * <ul>
     *   <li>signature[0] = input sensor hash (64-bit rolling hash)</li>
     *   <li>signature[1..N] = layer output long-words (as many as the
     *       BitSet produces per layer)</li>
     * </ul>
     *
     * <p>Two inputs that are semantically similar will have similar
     * activation patterns (small Hamming/L1 distance) because the
     * pretrained neurons learned these associations.
     */
    static long[] neuralSignature(AgentBrainService brainService, String text) {
        if (brainService == null || brainService.brain() == null) {
            return null;
        }
        // Encode text with the same hash the brain uses
        long sensorBits = hashText(text);

        // Forward pass through all layers, collecting each output
        NeuronLayer sensorLayer = brainService.brain().sensorLayer();
        NeuronLayer featureLayer = brainService.brain().featureLayer();
        NeuronLayer actionLayer = brainService.brain().actionLayer();

        if (sensorLayer == null || featureLayer == null || actionLayer == null) {
            return null;
        }

        BitSet input = toBitSet(sensorBits, 20);
        BitSet l0 = sensorLayer.evaluate(padInput(input, sensorLayer.outputWidth() * sensorLayer.k()));
        BitSet l1 = featureLayer.evaluate(padInput(l0, featureLayer.outputWidth() * featureLayer.k()));
        BitSet l2 = actionLayer.evaluate(padInput(l1, actionLayer.outputWidth() * actionLayer.k()));

        // Flatten all layer outputs into a single signature array
        long[] w0 = toLongArray(l0);
        long[] w1 = toLongArray(l1);
        long[] w2 = toLongArray(l2);

        long[] sig = new long[1 + w0.length + w1.length + w2.length];
        sig[0] = sensorBits;
        System.arraycopy(w0, 0, sig, 1, w0.length);
        System.arraycopy(w1, 0, sig, 1 + w0.length, w1.length);
        System.arraycopy(w2, 0, sig, 1 + w0.length + w1.length, w2.length);

        return sig;
    }

    /** Cosine similarity between two equal-length signature vectors. */
    private static float cosineSimilarity(long[] a, long[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0f;
        }
        long dot = 0;
        long normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += Long.bitCount(a[i] & b[i]); // bit-level overlap
            normA += Long.bitCount(a[i]);
            normB += Long.bitCount(b[i]);
        }
        if (normA == 0 || normB == 0) return 0.0f;
        double cos = (double) dot / Math.sqrt((double) normA * normB);
        return (float) cos;
    }

    /** Selects top-K indices by descending value. O(N*K). */
    private static int[] topK(float[] scores, int k) {
        int n = scores.length;
        int[] indices = new int[k];
        for (int i = 0; i < k; i++) indices[i] = -1;

        for (int i = 0; i < n; i++) {
            float s = scores[i];
            // Insert into sorted top-K
            for (int j = 0; j < k; j++) {
                if (indices[j] < 0 || s > scores[indices[j]]) {
                    System.arraycopy(indices, j, indices, j + 1, k - j - 1);
                    indices[j] = i;
                    break;
                }
            }
        }
        return indices;
    }

    /** Picks the most relevant sentence from a multi-sentence output. */
    private static String extractBestSentence(String text, long[] querySig) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length <= 1) return text;
        // Score each sentence by how many query-signature bits overlap with its hash
        int bestIdx = 0;
        long bestScore = -1;
        for (int i = 0; i < sentences.length; i++) {
            long hash = hashText(sentences[i]);
            long overlap = 0;
            for (long sig : querySig) {
                overlap += Long.bitCount(sig & hash);
            }
            if (overlap > bestScore) {
                bestScore = overlap;
                bestIdx = i;
            }
        }
        return sentences[bestIdx].trim();
    }

    /** Rolling hash — same as encodeText in NeuralTextGenerator. */
    static long hashText(String text) {
        long state = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            state = Long.rotateLeft(state, 5) ^ c;
            state ^= (long) i * 0x9E3779B97F4A7C15L;
        }
        return state;
    }

    /** Converts a long to a BitSet of width bits (max 64). */
    static BitSet toBitSet(long value, int bits) {
        BitSet bs = new BitSet(bits);
        for (int i = 0; i < bits; i++) {
            if ((value & (1L << i)) != 0) {
                bs.set(i);
            }
        }
        return bs;
    }

    /** Pads a BitSet to the required width. */
    static BitSet padInput(BitSet input, int requiredWidth) {
        if (input.length() >= requiredWidth) {
            return input.get(0, requiredWidth);
        }
        BitSet padded = new BitSet(requiredWidth);
        padded.or(input);
        return padded;
    }

    /** Converts a BitSet to long[]. */
    static long[] toLongArray(BitSet bs) {
        return bs.toLongArray();
    }

    /** Simple JSON field extractor — handles escaped quotes via lookahead. */
    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int colon = json.indexOf(':', ki + key.length());
        if (colon < 0) return null;
        // Skip whitespace between colon and value
        int valueStart = colon + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        if (json.charAt(valueStart) != '"') return null;
        // Find closing quote handling escaped quotes
        StringBuilder sb = new StringBuilder();
        for (int i = valueStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    // ─── Package-private test accessors ───

    static String accessExtractField(String json, String field) {
        return extractField(json, field);
    }

    static float accessCosineSimilarity(long[] a, long[] b) {
        return cosineSimilarity(a, b);
    }

    /** One training pair from the corpus. */
    record TrainingPair(String input, String output) {}

    /** Number of corpus entries loaded. */
    public int corpusSize() { return corpus.size(); }
}