package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W207 — Profile KV Cache (PagedAttention-style storage).
 *
 * <p>Inspired by vLLM PagedAttention (Kwon et al. 2023): store cognitive
 * profiles in fixed-size pages, allocated non-contiguously, to maximize
 * memory utilization.
 *
 * <p>Each page holds {@code pageSize} profiles. Pages can be evicted
 * (LRU) to make room for new profiles.
 *
 * <p>Use cases:
 * - Manage large cognitive profile histories
 * - Efficient retrieval of recent + sink profiles
 * - Memory-bounded cognitive state
 *
 * <p>CONSTITUTION VI compliance: efficient storage of cognitive
 * profiles, not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random instances are seeded.
 */
public final class ProfileKVCache {

    /** Default page size (profiles per page). */
    public static final int DEFAULT_PAGE_SIZE = 4;
    /** Default cache capacity (number of pages). */
    public static final int DEFAULT_NUM_PAGES = 16;

    private final int pageSize;
    private final int numPages;
    private final long seed;
    private final List<List<CognitiveGenesisProfile>> pages;
    private final List<Long> pageAccessTimestamps;
    private long accessCounter = 0;

    /**
     * Construct KV cache with default page size and capacity.
     */
    public ProfileKVCache() {
        this(DEFAULT_PAGE_SIZE, DEFAULT_NUM_PAGES, 0xC09F1107L);
    }

    public ProfileKVCache(int pageSize, int numPages, long seed) {
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be >= 1");
        if (numPages < 1) throw new IllegalArgumentException("numPages must be >= 1");
        this.pageSize = pageSize;
        this.numPages = numPages;
        this.seed = seed;
        this.pages = new ArrayList<>();
        this.pageAccessTimestamps = new ArrayList<>();
    }

    /**
     * Append a profile to the cache. Evicts LRU page if full.
     */
    public void append(CognitiveGenesisProfile profile) {
        if (profile == null) return;
        // Find a page with space, or create new
        if (pages.isEmpty() || pages.get(pages.size() - 1).size() >= pageSize) {
            if (pages.size() >= numPages) {
                evictLRU();
            }
            pages.add(new ArrayList<>());
            pageAccessTimestamps.add(accessCounter++);
        }
        List<CognitiveGenesisProfile> lastPage = pages.get(pages.size() - 1);
        lastPage.add(profile);
        // Update access timestamp for last page
        pageAccessTimestamps.set(pages.size() - 1, accessCounter++);
    }

    /**
     * Get all profiles in the cache (flat list, in order of insertion).
     */
    public List<CognitiveGenesisProfile> all() {
        List<CognitiveGenesisProfile> result = new ArrayList<>();
        for (List<CognitiveGenesisProfile> page : pages) {
            result.addAll(page);
        }
        return result;
    }

    /**
     * Number of profiles currently in cache.
     */
    public int size() {
        int sum = 0;
        for (List<CognitiveGenesisProfile> page : pages) sum += page.size();
        return sum;
    }

    /**
     * Number of pages (occupied).
     */
    public int pageCount() {
        return pages.size();
    }

    /**
     * Maximum capacity (pageSize × numPages).
     */
    public int capacity() {
        return pageSize * numPages;
    }

    /**
     * Get profile at index.
     */
    public CognitiveGenesisProfile get(int index) {
        int current = 0;
        for (List<CognitiveGenesisProfile> page : pages) {
            if (index < current + page.size()) {
                return page.get(index - current);
            }
            current += page.size();
        }
        return null;
    }

    /**
     * Evict least-recently-used page.
     */
    private void evictLRU() {
        if (pages.isEmpty()) return;
        long minAccess = Long.MAX_VALUE;
        int lruIndex = -1;
        for (int i = 0; i < pageAccessTimestamps.size(); i++) {
            if (pageAccessTimestamps.get(i) < minAccess) {
                minAccess = pageAccessTimestamps.get(i);
                lruIndex = i;
            }
        }
        if (lruIndex >= 0) {
            pages.remove(lruIndex);
            pageAccessTimestamps.remove(lruIndex);
        }
    }

    /** Page size. */
    public int pageSize() { return pageSize; }

    /** Max number of pages. */
    public int numPages() { return numPages; }

    /** Random seed used. */
    public long seed() { return seed; }

    /**
     * Compute hit rate: ratio of pages that are not the LRU-evicted page.
     * For benchmarking cache efficiency.
     */
    public double utilization() {
        if (pages.isEmpty()) return 0.0;
        int totalSlots = pages.size() * pageSize;
        int usedSlots = size();
        return (double) usedSlots / totalSlots;
    }
}
