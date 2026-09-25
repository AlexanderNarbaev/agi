package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * MIND-W2 — Persistent HDC knowledge store.
 *
 * <p>Replaces the in-memory ConcurrentHashMap-based HDC store with a
 * disk-backed NDJSON file. The store is loaded eagerly on construction
 * and re-saved on every {@link #teach} call (atomic write via tmp+rename).
 *
 * <p>NDJSON format (one record per line):</p>
 * <pre>
 *   {"id":"doc-1","content":"Paris is the capital of France","hdc_bits":4096,"hdc_set_indices":[3,17,42,...]}
 * </pre>
 *
 * <p>CONSTITUTION Article III — reproducibility: deterministic BitSet
 * representation (sorted indices) so the same content produces the
 * same NDJSON byte-for-byte across processes.</p>
 */
public final class PersistentHdcStore {

    /** Schema version for forward compat. */
    public static final int SCHEMA_VERSION = 1;

    /** In-memory maps (after load). */
    private final Map<String, BitSet> vectors = new ConcurrentHashMap<>();
    /**
     * Insertion-ordered contents map. We use LinkedHashMap (not ConcurrentHashMap)
     * because iteration order is required for stable NDJSON output AND for tests
     * that verify teach/learn preserve order across reopens.
     * All access is guarded by {@link #lock}.
     */
    private final Map<String, String> contents = new LinkedHashMap<>();

    private final Path storagePath;
    private final int dim;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Open or create a persistent HDC store at {@code storagePath}.
     * If the file exists, all records are loaded; otherwise the file is
     * created on first {@link #teach}.
     */
    public PersistentHdcStore(Path storagePath, int dim) {
        this.storagePath = storagePath;
        this.dim = dim;
        loadFromDisk();
    }

    /** Number of stored facts. */
    public int size() {
        lock.readLock().lock();
        try { return vectors.size(); }
        finally { lock.readLock().unlock(); }
    }

    /** Insert (or overwrite) a fact; persists to disk before returning. */
    public void teach(String id, String content) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (content == null) content = "";
        BitSet v = hashToVector(content, dim);
        lock.writeLock().lock();
        try {
            vectors.put(id, v);
            contents.put(id, content);
            persistAtomically();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Bulk-load: useful for tests and migrations; not used on the hot path. */
    public void teachAll(Map<String, String> docs) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, String> e : docs.entrySet()) {
                vectors.put(e.getKey(), hashToVector(e.getValue(), dim));
                contents.put(e.getKey(), e.getValue());
            }
            persistAtomically();
        } finally {
            lock.writeLock().unlock(); }
    }

    /** Snapshot of all contents, insertion-ordered. */
    public Map<String, String> snapshot() {
        lock.readLock().lock();
        try { return new LinkedHashMap<>(contents); }
        finally { lock.readLock().unlock(); }
    }

    /** All (id, hypervector) pairs, snapshot. */
    public Map<String, BitSet> vectors() {
        lock.readLock().lock();
        try { return new LinkedHashMap<>(vectors); }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Lightweight contradiction / duplication check.
     *
     * <p>Bit-cosine thresholds are tuned for sparse word-overlap HDC vectors:</p>
     * <ul>
     *   <li>cosine >= 0.65 -> DUPLICATE (very high overlap, same fact)</li>
     *   <li>cosine >= 0.40 -> POTENTIAL_CONFLICT (related but different surface)</li>
     *   <li>otherwise -> NOVEL</li>
     * </ul>
     */
    public ContradictionReport checkContradiction(String newId, String newContent) {
        lock.readLock().lock();
        try {
            BitSet query = hashToVector(newContent, dim);
            double bestSim = 0.0;
            String bestId = null;
            String bestContent = null;
            for (Map.Entry<String, BitSet> e : vectors.entrySet()) {
                if (e.getKey().equals(newId)) continue;
                double sim = cosine(query, e.getValue());
                if (sim > bestSim) {
                    bestSim = sim;
                    bestId = e.getKey();
                    bestContent = contents.get(e.getKey());
                }
            }
            if (bestSim >= 0.65) {
                return new ContradictionReport(ContradictionLevel.DUPLICATE,
                    bestSim, bestId, bestContent);
            }
            if (bestSim >= 0.40 && bestContent != null
                && !bestContent.equalsIgnoreCase(newContent)) {
                return new ContradictionReport(ContradictionLevel.POTENTIAL_CONFLICT,
                    bestSim, bestId, bestContent);
            }
            return new ContradictionReport(ContradictionLevel.NOVEL, bestSim, bestId, bestContent);
        } finally {
            lock.readLock().unlock();
        }
    }

    public enum ContradictionLevel { NOVEL, POTENTIAL_CONFLICT, DUPLICATE }

    public record ContradictionReport(
        ContradictionLevel level, double similarity, String existingId, String existingContent
    ) {}

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /** Load all records from the NDJSON file (if any). */
    private void loadFromDisk() {
        if (!Files.exists(storagePath)) return;
        try {
            List<String> lines = Files.readAllLines(storagePath);
            int loaded = 0;
            for (String line : lines) {
                if (line.isBlank()) continue;
                Record r = Record.fromJson(line);
                if (r == null) continue;
                vectors.put(r.id, r.toBitSet(dim));
                contents.put(r.id, r.content);
                loaded++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load HDC store from " + storagePath, e);
        }
    }

    /** Atomic write: serialize to .tmp then rename. */
    private void persistAtomically() {
        try {
            Files.createDirectories(storagePath.getParent() == null
                ? Path.of(".") : storagePath.getParent());
            Path tmp = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : contents.entrySet()) {
                Record r = new Record(e.getKey(), e.getValue(),
                    bitSetToIndices(vectors.get(e.getKey()), dim));
                sb.append(r.toJson()).append('\n');
            }
            Files.writeString(tmp, sb.toString());
            Files.move(tmp, storagePath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist HDC store to " + storagePath, e);
        }
    }

    // ------------------------------------------------------------------
    // HDC math (deterministic; mirrors HdcRetrievalStage.hashToVector)
    // ------------------------------------------------------------------

    public static BitSet hashToVector(String text, int dim) {
        BitSet bs = new BitSet(dim);
        if (text == null || text.isBlank()) return bs;
        for (String tok : text.toLowerCase().split("\\s+")) {
            if (tok.isBlank()) continue;
            int h = 0x811c9dc5;
            for (int i = 0; i < tok.length(); i++) {
                h ^= tok.charAt(i);
                h *= 0x01000193;
            }
            bs.set((h & 0x7fffffff) % dim);
        }
        return bs;
    }

    /** Jaccard cosine on bit sets. */
    public static double cosine(BitSet a, BitSet b) {
        if (a == null || b == null) return 0.0;
        BitSet inter = (BitSet) a.clone();
        inter.and(b);
        BitSet union = (BitSet) a.clone();
        union.or(b);
        int i = inter.cardinality();
        int u = union.cardinality();
        return u == 0 ? 0.0 : (double) i / (double) u;
    }

    private static List<Integer> bitSetToIndices(BitSet bs, int dim) {
        if (bs == null) return List.of();
        List<Integer> out = new java.util.ArrayList<>();
        for (int i = bs.nextSetBit(0); i >= 0 && i < dim; i = bs.nextSetBit(i + 1)) {
            out.add(i);
        }
        return out;
    }

    private static BitSet indicesToBitSet(List<Integer> indices, int dim) {
        BitSet bs = new BitSet(dim);
        if (indices == null) return bs;
        for (Integer i : indices) {
            if (i != null && i >= 0 && i < dim) bs.set(i);
        }
        return bs;
    }

    /** Wire format. */
    private record Record(String id, String content, List<Integer> bits) {
        String toJson() {
            // Stable, minimal JSON. We avoid Jackson here to keep the persistence
            // path dependency-free for the runtime module.
            StringBuilder sb = new StringBuilder(64 + content.length() + bits.size() * 4);
            sb.append("{\"id\":\"").append(escape(id))
              .append("\",\"content\":\"").append(escape(content))
              .append("\",\"bits\":[");
            boolean first = true;
            for (Integer b : bits) {
                if (!first) sb.append(',');
                sb.append(b);
                first = false;
            }
            sb.append("]}");
            return sb.toString();
        }
        BitSet toBitSet(int dim) {
            return indicesToBitSet(bits, dim);
        }
        static Record fromJson(String line) {
            // Tiny parser sufficient for our schema (no nested objects, no escapes).
            int idStart = line.indexOf("\"id\":\"");
            if (idStart < 0) return null;
            int idVal = idStart + 6;
            int idEnd = line.indexOf("\"", idVal);
            String id = line.substring(idVal, idEnd);

            int contentMarker = line.indexOf("\"content\":\"", idEnd);
            if (contentMarker < 0) return null;
            int contentVal = contentMarker + 11;
            int contentEnd = line.indexOf("\"", contentVal);
            String content = line.substring(contentVal, contentEnd);

            int bitsMarker = line.indexOf("\"bits\":[", contentEnd);
            if (bitsMarker < 0) return null;
            int bitsVal = bitsMarker + 8;
            int bitsEnd = line.indexOf(']', bitsVal);
            String bitsBody = line.substring(bitsVal, bitsEnd);
            List<Integer> indices = new java.util.ArrayList<>();
            if (!bitsBody.isBlank()) {
                for (String tok : bitsBody.split(",")) {
                    tok = tok.trim();
                    if (!tok.isEmpty()) indices.add(Integer.parseInt(tok));
                }
            }
            return new Record(id, content, indices);
        }
        private static String escape(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() + 4);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') sb.append('\\').append(c);
                else if (c == '\n') sb.append("\\n");
                else if (c == '\r') sb.append("\\r");
                else sb.append(c);
            }
            return sb.toString();
        }
    }
}
