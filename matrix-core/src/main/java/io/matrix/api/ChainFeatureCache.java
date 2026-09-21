package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cache of question → chain-output (boolean[]) mappings for the LM head
 * trainer (RUN 15).
 *
 * <p>Before RUN 15 the trainer used a hash-based pseudo chain output
 * (~37.5% random density). That was fast but not corpus-aligned: the
 * LM head learned "given this random fingerprint, this token follows"
 * rather than "given the chain's actual response to this question,
 * this token follows".
 *
 * <p>With the cache, the trainer:
 * <ol>
 *   <li>Checks {@link #get(String)} for the question's chain output.</li>
 *   <li>If miss, calls {@code BooleanChainRunner.evaluateWithScore(inputBits)}
 *       (the same path the chat endpoint uses), takes the resulting boolean
 *       vector (truncated/padded to {@link LmHead#totalNeurons()}), and
 *       stores it via {@link #put(String, boolean[])}.</li>
 *   <li>If hit, reuses the cached vector directly.</li>
 * </ol>
 *
 * <p>Cache key: SHA-256 of the question (UTF-8). Cache value: bit-packed
 * bytes. Files live on disk at {@link #CACHE_PATH} so they survive
 * restarts (8,606 questions × 21,960 bits ≈ 24 MB).
 *
 * <p>Determinism: the cache key is content-derived; cache values are
 * the deterministic chain output for the same input bits.
 *
 * <p>Thread-safety: backed by a {@link LinkedHashMap} guarded by
 * {@code synchronized}. Per-pair cache writes during batch training
 * are serialised; concurrent reads from {@link LmHeadTrainer} are safe.
 */
@ApplicationScoped
@Startup
public class ChainFeatureCache {

    private static final Logger log = LoggerFactory.getLogger(ChainFeatureCache.class);

    /** On-disk cache file. */
    public static final String CACHE_PATH = "data/chain_feature_cache.bin";

    /** Soft cap: keep at most N entries in memory. */
    private static final int MAX_ENTRIES = 50_000;

    @Inject
    BooleanChainRunner chainRunner;

    /** sha256(question) -> packed bytes (length-prefixed int + bit vector) */
    private final Map<String, byte[]> store = new LinkedHashMap<>(1024, 0.75f, false);

    private final Object lock = new Object();
    private volatile boolean loaded = false;
    private long hits = 0;
    private long misses = 0;

    void onStart(@Observes StartupEvent ev) {
        // Try to load the cache from disk
        try {
            if (Files.exists(Path.of(CACHE_PATH))) {
                loadFromDisk();
                loaded = true;
                log.info("ChainFeatureCache loaded {} entries from {}", store.size(), CACHE_PATH);
            }
        } catch (Exception e) {
            log.warn("ChainFeatureCache failed to load {}: {}", CACHE_PATH, e.getMessage());
        }
        if (!loaded) {
            log.info("ChainFeatureCache ready (empty — will populate on demand)");
        }
    }

    /**
     * Get cached chain output for a question, or {@code null} if miss.
     * Updates hit/miss counters.
     */
    public boolean[] get(String question) {
        if (question == null || question.isEmpty()) return null;
        String key = sha256(question);
        synchronized (lock) {
            byte[] bytes = store.get(key);
            if (bytes != null) {
                hits++;
                return unpack(bytes);
            }
            misses++;
            return null;
        }
    }

    /**
     * Store the chain output for a question (overwrites if exists).
     * Evicts oldest entry if cache exceeds {@link #MAX_ENTRIES}.
     */
    public void put(String question, boolean[] chainOutput) {
        if (question == null || question.isEmpty() || chainOutput == null) return;
        String key = sha256(question);
        byte[] bytes = pack(chainOutput);
        synchronized (lock) {
            // LRU-ish eviction via LinkedHashMap accessOrder
            store.put(key, bytes);
            while (store.size() > MAX_ENTRIES) {
                String oldest = store.keySet().iterator().next();
                store.remove(oldest);
            }
        }
    }

    /**
     * Get chain output for question, computing it via the boolean chain
     * runner if absent. This is the main entry point used by
     * {@link LmHeadTrainer}.
     *
     * <p>Encoding: question → text2vec → 64 bits → boolean[64] →
     * {@code BooleanChainRunner.evaluateWithScore()} → boolean[totalNeurons].
     */
    public boolean[] getOrCompute(String question) {
        boolean[] cached = get(question);
        if (cached != null) return cached;

        // Compute fresh: convert question to bits via Text2VecService,
        // then run the chain. The chain is fast (≤ ~1 ms / 174 μs p50
        // per RUN 11.1 benchmarks).
        Text2VecService text2vec = new Text2VecService();
        long sensorBits = text2vec.textToBits(question);
        int inputWidth = 64;
        boolean[] input = new boolean[inputWidth];
        for (int i = 0; i < inputWidth; i++) {
            input[i] = ((sensorBits >>> i) & 1L) != 0L;
        }

        boolean[] chainOutput;
        try {
            chainOutput = chainRunner.evaluate(input);
        } catch (Exception e) {
            log.warn("ChainFeatureCache: chain evaluation failed for '{}': {}",
                    truncate(question, 60), e.getMessage());
            return null;
        }

        // Truncate or pad to lmHead.totalNeurons() so the LmHead sees
        // a consistent dimension.
        int targetN = chainOutput.length;
        LmHead lmHeadRef = LmHeadTrainerHolder.lmHead();
        if (lmHeadRef != null && lmHeadRef.totalNeurons() > 0) {
            targetN = lmHeadRef.totalNeurons();
        }
        boolean[] sized = new boolean[targetN];
        int n = Math.min(chainOutput.length, targetN);
        System.arraycopy(chainOutput, 0, sized, 0, n);

        put(question, sized);
        return sized;
    }

    public int size() {
        synchronized (lock) {
            return store.size();
        }
    }

    public long hits() { return hits; }
    public long misses() { return misses; }

    /** Save cache to disk. */
    public synchronized void save() {
        try {
            Path p = Path.of(CACHE_PATH);
            Files.createDirectories(p.getParent());
            try (var out = Files.newOutputStream(p)) {
                synchronized (lock) {
                    out.write(intBytes(store.size()));
                    for (Map.Entry<String, byte[]> e : store.entrySet()) {
                        byte[] key = e.getKey().getBytes(StandardCharsets.UTF_8);
                        out.write(intBytes(key.length));
                        out.write(key);
                        byte[] val = e.getValue();
                        out.write(intBytes(val.length));
                        out.write(val);
                    }
                }
            }
            log.info("ChainFeatureCache: saved {} entries to {}", size(), CACHE_PATH);
        } catch (IOException e) {
            log.warn("ChainFeatureCache: failed to save: {}", e.getMessage());
        }
    }

    private void loadFromDisk() throws IOException {
        try (var in = Files.newInputStream(Path.of(CACHE_PATH))) {
            byte[] buf = in.readAllBytes();
            int pos = 0;
            int n = readInt(buf, pos); pos += 4;
            for (int i = 0; i < n; i++) {
                int keyLen = readInt(buf, pos); pos += 4;
                String key = new String(buf, pos, keyLen, StandardCharsets.UTF_8); pos += keyLen;
                int valLen = readInt(buf, pos); pos += 4;
                byte[] val = new byte[valLen];
                System.arraycopy(buf, pos, val, 0, valLen); pos += valLen;
                synchronized (lock) {
                    store.put(key, val);
                }
            }
        }
    }

    private static byte[] pack(boolean[] bits) {
        // Bit-pack into bytes, MSB first. Length-prefix not needed for
        // single entries (length is inferred from total size / 8).
        int n = bits.length;
        int byteLen = (n + 7) / 8;
        byte[] out = new byte[byteLen];
        for (int i = 0; i < n; i++) {
            if (bits[i]) out[i / 8] |= (byte) (0x80 >>> (i % 8));
        }
        return out;
    }

    private static boolean[] unpack(byte[] bytes) {
        boolean[] out = new boolean[bytes.length * 8];
        for (int i = 0; i < out.length; i++) {
            out[i] = (bytes[i / 8] & (0x80 >>> (i % 8))) != 0;
        }
        return out;
    }

    /** SHA-256 hex of UTF-8 question. */
    private static String sha256(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JDK — should never happen.
            throw new IllegalStateException(e);
        }
    }

    private static byte[] intBytes(int v) {
        return new byte[]{
                (byte) ((v >>> 24) & 0xFF),
                (byte) ((v >>> 16) & 0xFF),
                (byte) ((v >>> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
    }

    private static int readInt(byte[] buf, int pos) {
        return ((buf[pos] & 0xFF) << 24)
                | ((buf[pos + 1] & 0xFF) << 16)
                | ((buf[pos + 2] & 0xFF) << 8)
                | (buf[pos + 3] & 0xFF);
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * Static accessor for {@link LmHead} — breaks the CDI circular dependency
     * between {@link LmHeadTrainer} and this cache. {@link LmHeadTrainer} holds
     * the canonical {@code LmHead}; the cache asks for the current instance
     * via this holder. The holder is initialised by {@link LmHeadTrainer} at
     * startup.
     */
    public static final class LmHeadTrainerHolder {
        private static volatile LmHead lmHead;

        public static void lmHead(LmHead h) { lmHead = h; }
        public static LmHead lmHead() { return lmHead; }
    }
}
