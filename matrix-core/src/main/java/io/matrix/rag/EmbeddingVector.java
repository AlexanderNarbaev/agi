package io.matrix.rag;

/**
 * Float embedding vector wrapper with cosine/Hamming similarity.
 *
 * <p>Thin immutable wrapper around a float[] vector. Provides cosine
 * similarity (standard for dense embeddings) and Hamming distance after
 * thresholding at 0.5 (hybrid similarity that bridges dense and boolean
 * representations).
 *
 * <p>Deterministic: same input → same output. No ML, no LLM, no randomness.
 * Delegates hashing to {@link FloatEmbeddingIndex#fromText(String, int)}.
 *
 * <p>Ref: DIAGNOSIS.md §4.1, H-007, CONSTITUTION VII.1
 *
 * @param values L2-normalized float vector (defensive copy stored)
 */
public record EmbeddingVector(float[] values) {

    public EmbeddingVector {
        if (values == null) throw new NullPointerException("values");
        values = values.clone();  // defensive copy
    }

    /**
     * Creates an EmbeddingVector from text using deterministic BoW hashing
     * (delegates to {@link FloatEmbeddingIndex#fromText}).
     */
    public static EmbeddingVector fromText(String text) {
        return fromText(text, 64);
    }

    /** Creates from text with explicit dimension. */
    public static EmbeddingVector fromText(String text, int dim) {
        return new EmbeddingVector(FloatEmbeddingIndex.fromText(text, dim));
    }

    /** Creates from an integer value (localized position encoding). */
    public static EmbeddingVector fromValue(int value, int dim) {
        return new EmbeddingVector(FloatEmbeddingIndex.fromValue(value, dim));
    }

    /** Cosine similarity between two vectors. */
    public double cosine(EmbeddingVector other) {
        if (other == null) throw new NullPointerException("other");
        float[] a = this.values;
        float[] b = other.values();
        if (a.length != b.length)
            throw new IllegalArgumentException("dimension mismatch: " + a.length + " vs " + b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    /**
     * Hamming distance after thresholding at 0.5.
     * Each element &gt; 0.5 → 1, ≤ 0.5 → 0. Counts differing positions.
     */
    public int hamming(EmbeddingVector other) {
        if (other == null) throw new NullPointerException("other");
        float[] a = this.values;
        float[] b = other.values();
        if (a.length != b.length)
            throw new IllegalArgumentException("dimension mismatch: " + a.length + " vs " + b.length);
        int dist = 0;
        for (int i = 0; i < a.length; i++) {
            if ((a[i] > 0.5f) != (b[i] > 0.5f))
                dist++;
        }
        return dist;
    }

    /** L2 norm of the vector. */
    public double norm() {
        double sum = 0;
        for (float v : values) sum += (double) v * v;
        return Math.sqrt(sum);
    }

    /** Dimension of this embedding. */
    public int dimensions() {
        return values.length;
    }

    /**
     * Returns a defensive copy (the record constructor already clones,
     * but this is an explicit accessor for clarity).
     */
    @Override
    public float[] values() {
        return values.clone();
    }
}
