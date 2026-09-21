package io.matrix.neuron;

import java.util.List;

/**
 * RUN 426 — TF-IDF text-similarity scoring.
 * <p>{@code tf(t, d)} = occurrences in document / words in document.
 * {@code idf(t)} = ln(N / df(t)) where N = total docs and df(t) = docs
 * containing t. Cosine similarity over the L2-normalised TF-IDF vectors.
 * Pure function. CONSTITUTION I-safe.
 */
public final class TfIdf {

    private TfIdf() {}

    public record Vocab(List<String> vocabulary, double[][] docVectors) {}

    /** Tokenise by whitespace + lowercase. */
    public static Vocab fit(List<String> documents) {
        // Build vocabulary
        java.util.Map<String, Integer> termToIdx = new java.util.LinkedHashMap<>();
        for (String doc : documents) {
            for (String t : doc.toLowerCase().split("\\s+")) {
                if (t.isEmpty()) continue;
                termToIdx.putIfAbsent(t, termToIdx.size());
            }
        }
        int V = termToIdx.size();
        int N = documents.size();
        double[][] vecs = new double[N][V];
        // Compute TF and document-frequency
        int[] df = new int[V];
        double[] docLen = new double[N];
        for (int d = 0; d < N; d++) {
            String doc = documents.get(d).toLowerCase();
            String[] tokens = doc.split("\\s+");
            docLen[d] = tokens.length;
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                Integer idx = termToIdx.get(t);
                if (idx == null) continue;
                vecs[d][idx] += 1.0;
                df[idx] += 1;
            }
        }
        // Apply TF*IDF + L2 normalise
        for (int d = 0; d < N; d++) {
            if (docLen[d] == 0) continue;
            double norm = 0;
            for (int i = 0; i < V; i++) {
                if (vecs[d][i] > 0) {
                    double idf = Math.log((N + 1.0) / (df[i] + 1.0)) + 1.0;  // smoothed idf
                    vecs[d][i] = (vecs[d][i] / docLen[d]) * idf;
                }
                norm += vecs[d][i] * vecs[d][i];
            }
            if (norm > 0) {
                norm = Math.sqrt(norm);
                for (int i = 0; i < V; i++) vecs[d][i] /= norm;
            }
        }
        return new Vocab(new java.util.ArrayList<>(termToIdx.keySet()), vecs);
    }

    /** Cosine similarity between two L2-normalised vectors. */
    public static double cosine(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double s = 0;
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return s;
    }
}
