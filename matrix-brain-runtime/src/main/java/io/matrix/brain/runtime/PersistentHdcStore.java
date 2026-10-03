package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger LOG = Logger.getLogger(PersistentHdcStore.class.getName());

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

    /**
     * Insert (or overwrite) a fact; persists to disk before returning.
     *
     * <p>RECON-W31.1 / EPI-1 + EPI-4. This used to write unconditionally, which is how
     * 36 of 52 records in the shipped store came to be frozen EvalBattery probes and
     * adversarial-safety inputs — including "Tell me a racist joke", "how to kill" and
     * "rm -rf /" — each carrying the same provenance and confidence as a real fact. A
     * safety probe stored as knowledge is one similarity away from being served as an
     * answer, so this is a correctness property, not a tidiness one.</p>
     *
     * @throws PromotionRejectedException when the content may not become knowledge
     */
    public void teach(String id, String content) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (content == null) content = "";
        PromotionGate.Decision d = PromotionGate.evaluate(
            PromotionGate.Candidate.forRetrieval(content, content, 1.0, "mat:hdc:teach"));
        if (!d.promoted()) {
            throw new PromotionRejectedException(d.reason().name(), d.trace());
        }
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

    /** Thrown when {@link #teach} refuses a fact. Carries the gate's reason and trace. */
    public static final class PromotionRejectedException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String reason;
        private final String trace;
        public PromotionRejectedException(String reason, String trace) {
            super("promotion rejected: " + reason + " (" + trace + ")");
            this.reason = reason; this.trace = trace;
        }
        public String reason() { return reason; }
        public String trace() { return trace; }
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
            // RECON-W31.3 HW-1: the HdcVector kernel replaces the per-comparison BitSet
            // clone. This method runs on every teach() and scores the whole store, so
            // bulk ingest is O(n^2) — at 10k facts roughly 50M comparisons, which is
            // what W31.4 needs to survive. Value is bit-exact with cosine() (proved by
            // HdcVectorTest over randomized and adversarial inputs), so the
            // DUPLICATE / POTENTIAL_CONFLICT / NOVEL decision is unchanged.
            HdcVector query = HdcVector.from(hashToVector(newContent, dim));
            double bestSim = 0.0;
            String bestId = null;
            String bestContent = null;
            for (Map.Entry<String, BitSet> e : vectors.entrySet()) {
                if (e.getKey().equals(newId)) continue;
                double sim = HdcVector.jaccard(query, HdcVector.from(e.getValue()));
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
    /**
     * Load every record, and REFUSE to run if any line could not be parsed.
     *
     * <p><b>Why this is now fatal rather than lenient.</b> The previous version did
     * {@code if (r == null) continue;} with no counter and no log, then let
     * {@link #persistAtomically()} overwrite the file with whatever had loaded. During
     * W32 a record reformatting wrote 2 927 lines that this parser did not accept; the
     * load silently kept 17 of them and the next persist destroyed the other 2 910.
     *
     * <p>A skip that is invisible is a data-loss mechanism, not a tolerance. The
     * pattern recurs across this campaign — W31.1 the HDC store missed while the
     * episodic log was audited, W32.1 a refused file that looked like an absent one —
     * and this instance destroyed real data rather than merely hiding it.</p>
     *
     * @throws IllegalStateException when any non-blank line fails to parse, naming the
     *         file and the count, so the file is left untouched and recoverable
     */
    private void loadFromDisk() {
        if (!Files.exists(storagePath)) return;
        try {
            List<String> lines = Files.readAllLines(storagePath);
            int loaded = 0;
            int skipped = 0;
            int firstBadLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) continue;
                Record r = Record.fromJson(line);
                if (r == null) {
                    skipped++;
                    if (firstBadLine < 0) firstBadLine = i + 1;
                    continue;
                }
                vectors.put(r.id, r.toBitSet(dim));
                contents.put(r.id, r.content);
                loaded++;
            }
            if (skipped > 0) {
                throw new IllegalStateException(
                    "Refusing to load " + storagePath + ": " + skipped + " of "
                        + lines.size() + " lines failed to parse (first at line "
                        + firstBadLine + "). The file has NOT been modified. Repair or "
                        + "quarantine the offending lines; loading a subset and then "
                        + "persisting it would destroy the records that did parse.");
            }
            LOG.log(Level.INFO, "HDC store loaded {0} records from {1}",
                new Object[]{loaded, storagePath});
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

    /**
     * Jaccard cosine on bit sets — the reference implementation.
     *
     * <p>RECON-W31.3: no longer on any hot path. {@link #checkContradiction} now scores
     * via {@link HdcVector}, which is bit-exact with this method. Retained deliberately
     * as the <em>oracle</em> for the equivalence tests: replacing it would remove the
     * only statement of what the kernel is supposed to compute, leaving the tests to
     * assert agreement with themselves. Do not use it in new code.</p>
     */
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
