package io.matrix.rag;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Float embedding index — deterministic float[] vector storage with cosine similarity search.
 *
 * <p>Thread-safe via ReentrantReadWriteLock. Builder pattern.
 * No LLM, no external API — pure deterministic computation.
 *
 * <p>Ref: DIAGNOSIS.md §4.1, H-007, CONSTITUTION VII.1
 */
public final class FloatEmbeddingIndex {

    private final int dimensions;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, float[]> vectors = new LinkedHashMap<>();

    FloatEmbeddingIndex(int dimensions) { this.dimensions = dimensions; }

    public static Builder builder() { return new Builder(); }

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
