package io.matrix.bir;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * BIR Registry — central catalog for boolean intermediate representations.
 *
 * <p>Stores BIR artifacts with lineage metadata, content hashes for
 * integrity verification, and monotonic functionals (Φ) for traceability.
 *
 * <p>Per SPEC-002 §5 (DESIGN-01 §5): every BIR artifact is registered
 * with a unique ID, provenance chain, and content hash. The registry
 * enables:
 * <ul>
 *   <li>Lookup by ID, content hash, or function equivalence</li>
 *   <li>Lineage tracking across conversions and compositions</li>
 *   <li>Duplicate detection via hash collision check</li>
 *   <li>Audit trail for CONSTITUTION Article V (genesis protocol)</li>
 * </ul>
 *
 * <p>Thread safety: ConcurrentHashMap for reads/writes. All register
 * operations are atomic. The registry is the single source of truth
 * for BIR identity.
 *
 * <p>Storage: in-memory by default. Future: optional RocksDB persistence
 * via {@code BirRegistryPersistence} interface.
 */
public final class BirRegistry {

    /** Record representing a registered BIR artifact. */
    public record Entry(
            String id,
            Bir bir,
            String name,
            double phi,
            byte[] lineageHash,
            long registeredAt,
            String provenance
    ) {
        /** Content hash of the BIR for integrity verification. */
        public byte[] contentHash() {
            return bir.contentHash();
        }
    }

    private final ConcurrentMap<String, Entry> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> byHash = new ConcurrentHashMap<>();
    private final List<RegistrationListener> listeners = new ArrayList<>();

    /** Functional interface for registration events. */
    @FunctionalInterface
    public interface RegistrationListener {
        void onRegistered(Entry entry);
    }

    /**
     * Registers a BIR artifact.
     *
     * @param id          unique identifier (e.g., "bir:parity-layer0-bit3")
     * @param bir         the BIR artifact
     * @param name        human-readable name
     * @param phi         monotonic functional value (CONSTITUTION Article III)
     * @param lineageHash previous lineage hash (null for genesis)
     * @return the registered entry
     * @throws IllegalStateException if ID already registered
     */
    public Entry register(String id, Bir bir, String name, double phi,
                          byte[] lineageHash) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bir, "bir");
        Objects.requireNonNull(name, "name");

        Entry entry = new Entry(id, bir, name, phi,
                lineageHash != null ? lineageHash.clone() : null,
                System.currentTimeMillis(),
                bir.provenance());

        Entry previous = byId.putIfAbsent(id, entry);
        if (previous != null) {
            throw new IllegalStateException(
                    "BIR already registered: " + id + " (name=" + previous.name() + ")");
        }

        // Index by content hash (hex for readability)
        String hashHex = bytesToHex(bir.contentHash());
        byHash.computeIfAbsent(hashHex, k -> ConcurrentHashMap.newKeySet()).add(id);

        // Notify listeners
        for (RegistrationListener listener : listeners) {
            listener.onRegistered(entry);
        }

        return entry;
    }

    /**
     * Retrieves a registered BIR by ID.
     *
     * @param id unique identifier
     * @return the entry, or null if not found
     */
    public Entry get(String id) {
        return byId.get(id);
    }

    /**
     * Finds all BIRs with the given content hash.
     *
     * @param hash content hash (exact match)
     * @return list of matching entries (empty if none)
     */
    public List<Entry> getByHash(byte[] hash) {
        String hashHex = bytesToHex(hash);
        Set<String> ids = byHash.get(hashHex);
        if (ids == null || ids.isEmpty()) return List.of();
        List<Entry> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            Entry e = byId.get(id);
            if (e != null) result.add(e);
        }
        return List.copyOf(result);
    }

    /**
     * Lists all registered BIRs.
     *
     * @return unordered list of all entries
     */
    public List<Entry> listAll() {
        return List.copyOf(byId.values());
    }

    /**
     * Returns the number of registered BIRs.
     */
    public int size() {
        return byId.size();
    }

    /**
     * Adds a registration listener. Called synchronously on register.
     */
    public void addListener(RegistrationListener listener) {
        listeners.add(listener);
    }

    /**
     * Checks if a BIR with the given content hash is already registered.
     */
    public boolean containsHash(byte[] hash) {
        return byHash.containsKey(bytesToHex(hash));
    }

    /**
     * Clears all entries (testing only).
     */
    public void clear() {
        byId.clear();
        byHash.clear();
    }

    // ─── internals ───

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
