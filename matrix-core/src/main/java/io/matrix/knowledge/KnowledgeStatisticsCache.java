package io.matrix.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — the cache of REUSABLE retrieval statistics.
 *
 * <p><b>What it holds.</b> One {@link KnowledgeStatistics} per <b>(workspace, view identity)</b>
 * pair. The view identity is the digest of the texts the statistics are computed from, so a view
 * whose claims changed has a different identity and the older statistics are never served for
 * it — the invalidation is a property of the key, not of a rule somebody has to remember to
 * apply.</p>
 *
 * <p><b>Why the workspace is part of the key.</b> Two workspaces may hold the same texts and
 * therefore the same statistics, and the cache keeps them apart anyway: the entry belongs to
 * the workspace it was computed for, and one workspace's statistics are never served for
 * another. The scope of the reuse is the scope of the workspace.</p>
 *
 * <p><b>Why it is bounded.</b> The statistics of a view are as large as the view, so an
 * unbounded cache is a leak with a workspace in it. The cache keeps the most recently used
 * entries and drops the rest, and the drop costs a recomputation and nothing else — the
 * statistics are a function of the view, not a piece of state the workspace depends on.</p>
 *
 * <p><b>Why it is in memory.</b> The statistics are a cache, not a record: they are derived from
 * the workspace's own content and carry no information the workspace does not already hold. A
 * restart empties the cache, and the first reading after a restart pays the full price again —
 * which is the honest behaviour for a cache whose contents are derivable.</p>
 *
 * <p><b>Why it is synchronized.</b> The workbench asks one question at a time, but the cache is
 * a shared object and a concurrent reader must see a whole entry, never half of one.</p>
 */
public final class KnowledgeStatisticsCache {

    /** The number of entries the cache keeps by default. */
    public static final int DEFAULT_CAPACITY = 8;

    /** The separator between the workspace and the identity in a key. */
    private static final String KEY_SEPARATOR = "\u0000";

    private final int capacity;

    private final Map<String, KnowledgeStatistics> entries;

    /**
     * Creates a cache with the default capacity.
     */
    public KnowledgeStatisticsCache() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a cache with a capacity.
     *
     * @param capacity the number of entries the cache keeps
     */
    public KnowledgeStatisticsCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("the cache must keep at least one entry");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, KnowledgeStatistics> eldest) {
                return size() > KnowledgeStatisticsCache.this.capacity;
            }
        };
    }

    /**
     * The statistics of a view, computed once and reused.
     *
     * <p>The identity of the view is computed here, so a caller that does not know the view's
     * identity still gets the right entry — and a view whose content changed gets a different
     * one.</p>
     *
     * @param workspaceId the workspace the view belongs to
     * @param view        the view
     * @return the statistics
     */
    public synchronized KnowledgeStatistics forView(String workspaceId, KnowledgeSnapshot view) {
        return forView(workspaceId, view, KnowledgeStatistics.identityOf(view));
    }

    /**
     * The statistics of a view, with an identity the caller already holds.
     *
     * @param workspaceId  the workspace the view belongs to
     * @param view         the view
     * @param viewIdentity the identity of the view
     * @return the statistics
     */
    public synchronized KnowledgeStatistics forView(String workspaceId, KnowledgeSnapshot view,
            String viewIdentity) {
        String key = key(workspaceId, viewIdentity);
        KnowledgeStatistics cached = entries.get(key);
        if (cached != null) {
            return cached;
        }
        KnowledgeStatistics computed = KnowledgeStatistics.of(view, viewIdentity);
        entries.put(key, computed);
        return computed;
    }

    /** The number of entries the cache holds. */
    public synchronized int size() {
        return entries.size();
    }

    /** Empties the cache. */
    public synchronized void clear() {
        entries.clear();
    }

    /**
     * The key of an entry.
     *
     * @param workspaceId  the workspace
     * @param viewIdentity the identity of the view
     * @return the key
     */
    private static String key(String workspaceId, String viewIdentity) {
        return (workspaceId == null ? "" : workspaceId) + KEY_SEPARATOR
                + (viewIdentity == null ? "" : viewIdentity);
    }
}
