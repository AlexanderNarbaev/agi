package io.matrix.distill;

import java.util.ArrayList;
import java.util.List;

/**
 * RECON-W21 — one captured activation record, replayed from the offline sidecar.
 *
 * <p>Written by {@code scripts/capture_activations.py} (Python onnxruntime) in a
 * SEPARATE PROCESS, because ONNX Runtime's Java binding segfaults on this host.
 * This class is the Java-side reader for the {@code matrix.activation.v1} NDJSON
 * schema. It contains no ONNX dependency at all — that is the whole point: the
 * native library is confined to the offline tool and never loaded by the runtime.
 *
 * <p><b>Article I.</b> Pure data + parsing. No network, no model loading, no
 * native library. It cannot execute a model even in principle.</p>
 * <p><b>Article III.</b> {@link #parse} is a pure function of its argument and
 * returns {@code null} for anything it does not understand, so a malformed capture
 * is skipped and counted rather than half-applied.</p>
 */
public record ActivationRecord(
    String schema,
    String batchId,
    int sampleId,
    String inputText,
    List<String> inputTokens,
    float[] inputFeatures,
    float[] activation,
    float[] logits
) {

    /**
     * Parse one NDJSON line. Returns {@code null} when the line is not a usable
     * activation record — the caller counts these rather than dropping them
     * silently (Article VIII).
     */
    public static ActivationRecord parse(String line) {
        if (line == null || line.isBlank()) return null;
        try {
            // Python's json.dumps emits `"key": value` (space after the colon).
            // Normalise `": "` -> `":"` so the field lookups below are
            // whitespace-insensitive without carrying the spaces in every needle.
            line = line.replace("\": ", "\":");
            String schema = jsonString(line, "schema");
            if (schema == null || !schema.startsWith("matrix.activation")) return null;
            String batchId = jsonString(line, "batch_id");
            int sampleId = jsonInt(line, "sample_id", -1);
            String inputText = jsonString(line, "input_text");
            List<String> tokens = jsonStringArray(line, "input_tokens");
            float[] features = jsonFloatArray(line, "input_features");
            float[] logits = jsonFloatArray(line, "logits");
            // The activation is the FIRST layer tensor; the sidecar writes
            // layer_activations[0] ahead of the logits.
            float[] activation = firstLayerActivation(line);
            if (activation == null || activation.length == 0) return null;
            return new ActivationRecord(schema, batchId, sampleId, inputText,
                tokens == null ? List.of() : tokens,
                features == null ? new float[0] : features,
                activation, logits == null ? new float[0] : logits);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    /**
     * Project this record onto a deterministic bit vector of {@code inputBits}
     * width, suitable for {@link Distiller#capture(long[], float[])}.
     *
     * <p>Derived from the token set and the captured features so the same capture
     * always yields the same vector (Article III).</p>
     */
    public long[] toBitVector(int inputBits) {
        int words = Math.max(1, (inputBits + 63) / 64);
        long[] vec = new long[words];
        List<String> toks = (inputTokens == null || inputTokens.isEmpty())
            ? List.of(String.valueOf(inputText)) : inputTokens;
        int n = toks.size();
        for (int i = 0; i < inputBits; i++) {
            String tok = toks.get(i % Math.max(1, n));
            int h = stableHash(tok, i);
            if ((h & 1) == 1) {
                vec[i / 64] |= (1L << (i % 64));
            }
        }
        return vec;
    }

    /** FNV-1a — deterministic, seeded by the token position. */
    private static int stableHash(String s, int salt) {
        long h = 0x811c9dc5L ^ (salt * 0x9E3779B97F4A7C15L);
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193L;
        }
        return (int) (h ^ (h >>> 32));
    }

    // ---- minimal, dependency-free JSON readers -----------------------------

    private static String jsonString(String s, String key) {
        String needle = "\"" + key + "\":";
        int i = s.indexOf(needle);
        if (i < 0) return null;
        int q1 = s.indexOf('"', i + needle.length());
        if (q1 < 0) return null;
        int q2 = s.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return s.substring(q1 + 1, q2).replace("\\\"", "\"");
    }

    private static int jsonInt(String s, String key, int dflt) {
        String needle = "\"" + key + "\":";
        int i = s.indexOf(needle);
        if (i < 0) return dflt;
        int j = i + needle.length();
        int k = j;
        while (k < s.length() && (Character.isDigit(s.charAt(k)) || s.charAt(k) == '-')) k++;
        try {
            return Integer.parseInt(s.substring(j, k));
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static List<String> jsonStringArray(String s, String key) {
        String raw = jsonArray(s, key);
        if (raw == null) return null;
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2) {
                out.add(p.substring(1, p.length() - 1));
            }
        }
        return out;
    }

    private static float[] jsonFloatArray(String s, String key) {
        String raw = jsonArray(s, key);
        if (raw == null) return null;
        String[] parts = raw.split(",");
        List<Float> vals = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            try {
                vals.add(Float.parseFloat(t));
            } catch (NumberFormatException ignored) {
                // Skip a non-numeric element rather than failing the whole record.
            }
        }
        float[] out = new float[vals.size()];
        for (int i = 0; i < out.length; i++) out[i] = vals.get(i);
        return out;
    }

    /** The first nested array under {@code "layer_activations":[[...],[...]]}. */
    /**
     * Extract the FIRST layer tensor from {@code layer_activations}.
     *
     * <p>RECON-W21 bug fixed: this used to hand {@code jsonFloatArray} the whole
     * {@code [[...]]} value, which stripped only the OUTER brackets and left
     * {@code "[0.9,0.5,..."} — the first element unparseable and silently
     * dropped. The unit test did not catch it because it asserted only that the
     * array was non-empty, never its length. The inner array is now located
     * explicitly, and {@link ActivationRecordTest} pins the element count.</p>
     */
    private static float[] firstLayerActivation(String s) {
        String needle = "\"layer_activations\":";
        int i = s.indexOf(needle);
        if (i < 0) return null;
        int k = i + needle.length();
        while (k < s.length() && Character.isWhitespace(s.charAt(k))) k++;
        if (k >= s.length() || s.charAt(k) != '[') return null;   // outer list
        int innerOpen = s.indexOf('[', k + 1);
        if (innerOpen < 0) {
            // Not nested: a flat numeric array.
            return jsonFloatArray(s.substring(k), "");
        }
        int depth = 0;
        int innerClose = -1;
        for (int p = innerOpen; p < s.length(); p++) {
            char c = s.charAt(p);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) { innerClose = p; break; }
            }
        }
        if (innerClose < 0) return null;
        return jsonFloatArray(s.substring(innerOpen, innerClose + 1), "");
    }

    private static String jsonArray(String s, String key) {
        int from = 0;
        if (!key.isEmpty()) {
            String needle = "\"" + key + "\":";
            int i = s.indexOf(needle);
            if (i < 0) return null;
            from = i + needle.length();
        }
        // Skip whitespace between the colon and the opening bracket. Python's
        // json.dumps emits `"key": [1, 2]`, so requiring adjacency silently
        // rejected every record the real sidecar produced.
        int k = from;
        while (k < s.length() && Character.isWhitespace(s.charAt(k))) k++;
        if (k >= s.length() || s.charAt(k) != '[') return null;
        int open = k;
        int depth = 0;
        int close = -1;
        for (int p = open; p < s.length(); p++) {
            char c = s.charAt(p);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) { close = p; break; }
            }
        }
        if (close < 0) return null;
        return s.substring(open + 1, close).trim();
    }
}
