package io.matrix.rag;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ai.onnxruntime.*;

/**
 * Float embedding index — deterministic float[] vector storage with cosine similarity search.
 *
 * <p>Thread-safe via ReentrantReadWriteLock. Builder pattern.
 * Supports both deterministic hash-based embeddings and real ONNX model embeddings.
 *
 * <p>Ref: DIAGNOSIS.md §4.1, H-007, CONSTITUTION VII.1
 */
public final class FloatEmbeddingIndex implements AutoCloseable {

    private final int dimensions;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, float[]> vectors = new LinkedHashMap<>();
    private volatile OrtEnvironment env;
    private volatile OrtSession session;
    private volatile boolean modelLoaded = false;

    FloatEmbeddingIndex(int dimensions) { this.dimensions = dimensions; }

    public static Builder builder() { return new Builder(); }

    /**
     * Load ONNX model for real embeddings (Text2Vec, BERT, etc.).
     * @param modelPath path to ONNX model file
     * @throws OrtException if model loading fails
     */
    public void loadModel(String modelPath) throws OrtException {
        lock.writeLock().lock();
        try {
            if (env != null) env.close();
            if (session != null) session.close();
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelPath);
            modelLoaded = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if ONNX model is loaded.
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * Generate real embedding using loaded ONNX model.
     * @param text input text
     * @return float[] embedding vector
     * @throws OrtException if inference fails
     * @throws IllegalStateException if no model loaded
     */
    public float[] embedWithModel(String text) throws OrtException {
        if (!modelLoaded) throw new IllegalStateException("No ONNX model loaded");
        Objects.requireNonNull(text, "text");

        lock.readLock().lock();
        try {
            // Tokenize text (simple whitespace + subword approximation)
            long[] inputIds = tokenize(text);
            long[] attentionMask = new long[inputIds.length];
            Arrays.fill(attentionMask, 1L);

            // Create input tensors
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, new long[][]{inputIds});
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, new long[][]{attentionMask});

            // Run inference
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);

            OrtSession.Result result = session.run(inputs);

            // Extract embeddings (mean pooling)
            float[][][] output = (float[][][]) result.get(0).getValue();
            float[] embedding = meanPooling(output[0], attentionMask);

            // Resize to target dimensions
            if (embedding.length != dimensions) {
                embedding = resizeEmbedding(embedding, dimensions);
            }

            // Clean up
            inputIdsTensor.close();
            attentionMaskTensor.close();
            result.close();

            return embedding;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Simple tokenizer (whitespace + hash-based subword approximation).
     * Deterministic, no external dependencies.
     */
    private long[] tokenize(String text) {
        String[] tokens = text.toLowerCase().split("\\s+");
        long[] ids = new long[tokens.length + 2]; // +2 for [CLS] and [SEP]
        ids[0] = 101L; // [CLS] token
        for (int i = 0; i < tokens.length; i++) {
            ids[i + 1] = Math.abs(tokens[i].hashCode()) % 30000 + 1000;
        }
        ids[ids.length - 1] = 102L; // [SEP] token
        return ids;
    }

    /**
     * Mean pooling over token embeddings.
     */
    private float[] meanPooling(float[][] tokenEmbeddings, long[] attentionMask) {
        int dim = tokenEmbeddings[0].length;
        float[] pooled = new float[dim];
        int count = 0;
        for (int i = 0; i < tokenEmbeddings.length; i++) {
            if (attentionMask[i] == 1) {
                for (int j = 0; j < dim; j++) {
                    pooled[j] += tokenEmbeddings[i][j];
                }
                count++;
            }
        }
        if (count > 0) {
            for (int j = 0; j < dim; j++) {
                pooled[j] /= count;
            }
        }
        return pooled;
    }

    /**
     * Resize embedding to target dimensions (interpolation).
     */
    private float[] resizeEmbedding(float[] embedding, int targetDim) {
        float[] resized = new float[targetDim];
        for (int i = 0; i < targetDim; i++) {
            float pos = (float) i / targetDim * embedding.length;
            int idx = (int) pos;
            float frac = pos - idx;
            if (idx + 1 < embedding.length) {
                resized[i] = embedding[idx] * (1 - frac) + embedding[idx + 1] * frac;
            } else {
                resized[i] = embedding[idx];
            }
        }
        // L2 normalize
        double norm = 0;
        for (float f : resized) norm += (double) f * f;
        if (norm > 0) {
            double s = 1.0 / Math.sqrt(norm);
            for (int i = 0; i < targetDim; i++) resized[i] = (float) (resized[i] * s);
        }
        return resized;
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            if (session != null) {
                try { session.close(); } catch (OrtException e) { /* ignore */ }
            }
            if (env != null) {
                env.close();
            }
            modelLoaded = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void add(String id, float[] vector) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vector, "vector");
        if (vector.length != dimensions) throw new IllegalArgumentException("dimension mismatch: expected " + dimensions + ", got " + vector.length);
        lock.writeLock().lock();
        try { vectors.put(id, vector.clone()); }
        finally { lock.writeLock().unlock(); }
    }

    public float[] get(String id) {
        Objects.requireNonNull(id, "id");
        lock.readLock().lock();
        try { float[] v = vectors.get(id); return v != null ? v.clone() : null; }
        finally { lock.readLock().unlock(); }
    }

    public int size() {
        lock.readLock().lock();
        try { return vectors.size(); }
        finally { lock.readLock().unlock(); }
    }

    public List<ScoredItem> search(float[] query, int k) {
        Objects.requireNonNull(query, "query");
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        lock.readLock().lock();
        try {
            if (vectors.isEmpty()) return Collections.emptyList();
            List<ScoredItem> scored = new ArrayList<>(vectors.size());
            for (var e : vectors.entrySet())
                scored.add(new ScoredItem(e.getKey(), cosineSimilarity(query, e.getValue())));
            scored.sort(Comparator.comparingDouble(ScoredItem::score).reversed().thenComparing(ScoredItem::id));
            return Collections.unmodifiableList(scored.subList(0, Math.min(k, scored.size())));
        } finally { lock.readLock().unlock(); }
    }

    public int dimensions() { return dimensions; }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("length mismatch");
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += (double)a[i]*b[i]; na += (double)a[i]*a[i]; nb += (double)b[i]*b[i]; }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    /** Deterministic embedding from integer value — localized position encoding with neighbors, L2-normalized. */
    public static float[] fromValue(int value, int dim) {
        if (dim < 1) throw new IllegalArgumentException("dim >= 1 required");
        float[] v = new float[dim];
        int c = Math.floorMod(value, dim);
        v[c] = 1.0f; v[Math.floorMod(c+1, dim)] = 0.7f; v[Math.floorMod(c-1, dim)] = 0.7f;
        double norm = 0; for (float f : v) norm += (double)f*f;
        double s = 1.0/Math.sqrt(norm);
        for (int i = 0; i < dim; i++) v[i] = (float)(v[i]*s);
        return v;
    }

    /** Deterministic embedding from text — BoW hash → log-scale → L2-normalized. No ML, no LLM. */
    public static float[] fromText(String text, int dim) {
        if (dim < 1) throw new IllegalArgumentException("dim >= 1 required");
        if (text == null) text = "";
        float[] v = new float[dim];
        for (String tok : text.split("[^\\p{L}\\p{N}]+")) {
            if (tok.isEmpty()) continue;
            v[Math.floorMod(tok.toLowerCase().hashCode(), dim)] += 1.0f;
        }
        for (int i = 0; i < dim; i++) if (v[i] > 0) v[i] = (float)Math.log1p(v[i]);
        double norm = 0; for (float f : v) norm += (double)f*f;
        if (norm > 0) { double s = 1.0/Math.sqrt(norm); for (int i = 0; i < dim; i++) v[i] = (float)(v[i]*s); }
        return v;
    }

    public record ScoredItem(String id, double score) {}

    public static final class Builder {
        private int dimensions = 64;
        public Builder dimensions(int d) { if (d < 1) throw new IllegalArgumentException(); this.dimensions = d; return this; }
        public FloatEmbeddingIndex build() { return new FloatEmbeddingIndex(dimensions); }
    }
}
