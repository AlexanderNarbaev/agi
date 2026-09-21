package io.matrix.api;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CDI wrapper around {@link BpeTokenizer} that lazily loads
 * Qwen's tokenizer files on first use. Reports
 * {@link #isAvailable()} so callers can fall back to a hash encoder
 * when the tokenizer files are missing.
 */
@ApplicationScoped
public class BpeTokenizerProvider {

    private static final Path DEFAULT_MODEL_DIR = Path.of(
            "models/external/qwen2.5-0.5b/safetensors_meta/snapshots/"
                    + "060db6499f32faf8b98477b0a26969ef7d8b9987");

    private final AtomicReference<BpeTokenizer> cached = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public boolean isAvailable() {
        return loadIfPossible() != null;
    }

    public String lastLoadError() { return lastError.get(); }

    public boolean[] textToBits(String text, int width) {
        BpeTokenizer t = loadIfPossible();
        if (t == null) {
            // fallback: hash-based bits (preserves a deterministic pattern
            // even when the tokenizer isn't available, so callers always
            // get a non-null result).
            boolean[] out = new boolean[width];
            byte[] bytes = text == null ? new byte[0] : text.getBytes();
            for (int i = 0; i < bytes.length; i++) {
                long h = (long)(bytes[i] ^ i) * 2654435761L;
                out[Math.floorMod((int) (h & 0x7FFFFFFF), width)] = true;
            }
            return out;
        }
        return t.textToBits(text, width);
    }

    /** BPE-encode a string into token ids. Returns empty int[] when tokenizer unavailable. */
    public int[] encode(String text) {
        BpeTokenizer t = loadIfPossible();
        return t == null ? new int[0] : t.encode(text == null ? "" : text);
    }

    /** Look up a single token's text by id. */
    public String tokenAt(int id) {
        BpeTokenizer t = loadIfPossible();
        return t == null ? null : t.reverseVocabFor(id);
    }

    /** BPE-vocabulary size (0 when tokenizer unavailable). */
    public int vocabSize() {
        BpeTokenizer t = loadIfPossible();
        return t == null ? 0 : t.vocabSize();
    }

    /** Underlying BpeTokenizer (for tests / direct manipulation). */
    public BpeTokenizer tokenizer() { return loadIfPossible(); }

    private BpeTokenizer loadIfPossible() {
        BpeTokenizer c = cached.get();
        if (c != null) return c;
        try {
            if (!Files.exists(DEFAULT_MODEL_DIR)) {
                lastError.set("model dir not found: " + DEFAULT_MODEL_DIR);
                return null;
            }
            BpeTokenizer t = BpeTokenizer.fromModelDir(DEFAULT_MODEL_DIR);
            return cached.compareAndSet(null, t) ? t : cached.get();
        } catch (Exception e) {
            lastError.set(e.getMessage());
            return null;
        }
    }
}