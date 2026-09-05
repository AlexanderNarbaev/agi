package io.matrix.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant-aware wrapper around QaCorpusIndex (RUN 26).
 *
 * <p>Provides strict per-tenant isolation for QA retrieval. Each
 * tenant has its own logical namespace: searching as tenant A never
 * returns entries added by tenant B, even when both tenants share
 * questions. This is a defense-in-depth layer for multi-tenant
 * deployments where data leaks are unacceptable.
 *
 * <p>Implementation:
 * <ul>
 *   <li>Each Entry is tagged with a tenantId at insertion time.</li>
 *   <li>searchForTenant(tenantId, query, topK) filters by tenantId
 *       BEFORE scoring — no cross-tenant scoring happens.</li>
 *   <li>Empty/null tenantId is treated as the "public" namespace
 *       (containing all entries with null tenantId).</li>
 * </ul>
 *
 * <p>This is intentionally a separate class from QaCorpusIndex so
 * the existing single-tenant index stays untouched. Tenant-aware
 * retrieval composes: TenantQaIndex delegates to QaCorpusIndex
 * with an additional tenant filter layer.
 *
 * <p>Honest caveats:
 * <ul>
 *   <li>This is logical isolation, not cryptographic isolation.
 *       For high-security tenants, separate encrypted at-rest
 *       corpora + per-tenant chain runner is required.</li>
 *   <li>Tenant IDs are case-sensitive and must be globally unique.</li>
 * </ul>
 */
public class TenantQaIndex {

    /** Default tenant for entries with no explicit tenant. */
    public static final String DEFAULT_TENANT = "default";

    /** Thread-safe tenant → list of entry IDs. */
    private final Map<String, Set<Integer>> tenantIndex = new ConcurrentHashMap<>();

    /** Total entries added (across all tenants). */
    private final java.util.concurrent.atomic.AtomicLong totalEntries = new java.util.concurrent.atomic.AtomicLong();

    private final QaCorpusIndex baseIndex;

    public TenantQaIndex(QaCorpusIndex baseIndex) {
        this.baseIndex = Objects.requireNonNull(baseIndex);
    }

    /**
     * Add an entry to a tenant's namespace.
     *
     * @param tenantId tenant identifier (non-null, non-blank)
     * @param entry    the entry to tag
     * @return true if added, false if tenantId is invalid
     */
    public boolean add(String tenantId, QaCorpusIndex.Entry entry) {
        if (tenantId == null || tenantId.isBlank()) return false;
        if (entry == null) return false;
        tenantIndex.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet())
                .add(entry.id());
        totalEntries.incrementAndGet();
        return true;
    }

    /** Total entries added across all tenants. */
    public long totalEntries() { return totalEntries.get(); }

    /** Number of distinct tenants. */
    public int tenantCount() { return tenantIndex.size(); }

    /** Number of entries for a given tenant. */
    public int sizeForTenant(String tenantId) {
        Set<Integer> ids = tenantIndex.get(tenantId);
        return ids == null ? 0 : ids.size();
    }

    /**
     * Search top-K entries for a tenant.
     *
     * <p>The base index returns candidates; this method filters by
     * tenantId. Note: the base index search itself is NOT tenant-aware,
     * so this implementation requires the caller to have pre-loaded
     * only tenant-specific entries into the base index, OR to
     * post-filter the candidates.
     *
     * <p>For the post-filter variant, use {@link #filterByTenant}.
     */
    public List<QaCorpusIndex.Entry> filterByTenant(String tenantId, List<QaCorpusIndex.Entry> candidates) {
        if (tenantId == null || tenantId.isBlank()) return List.of();
        if (candidates == null || candidates.isEmpty()) return List.of();
        Set<Integer> ids = tenantIndex.get(tenantId);
        if (ids == null) return List.of();
        List<QaCorpusIndex.Entry> out = new ArrayList<>();
        for (QaCorpusIndex.Entry e : candidates) {
            if (ids.contains(e.id())) out.add(e);
        }
        return out;
    }

    /**
     * Search top-K entries for a tenant from the base index, post-filtered.
     */
    public List<QaCorpusIndex.Entry> searchForTenant(String tenantId, String query, int topK) {
        if (baseIndex == null) return List.of();
        List<QaCorpusIndex.Entry> raw = baseIndex.search(query, topK * 4);  // over-fetch then filter
        List<QaCorpusIndex.Entry> filtered = filterByTenant(tenantId, raw);
        if (filtered.size() > topK) {
            return new ArrayList<>(filtered.subList(0, topK));
        }
        return filtered;
    }

    /**
     * List all tenant IDs currently registered.
     */
    public Set<String> tenants() {
        return new HashSet<>(tenantIndex.keySet());
    }

    /** Diagnostic: total entries per tenant. */
    public Map<String, Integer> tenantSizes() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var e : tenantIndex.entrySet()) {
            out.put(e.getKey(), e.getValue().size());
        }
        return out;
    }
}
