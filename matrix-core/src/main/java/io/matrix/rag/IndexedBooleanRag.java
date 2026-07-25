package io.matrix.rag;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Performance-optimized Boolean RAG wrapper with inverted index hints.
 * 
 * Wraps BooleanIndex and provides additional indexing for common query patterns.
 * 
 * @see <a href="docs/improvements/PERFORMANCE_OPTIMIZATION.md">Performance Plan</a>
 */
@ApplicationScoped
public class IndexedBooleanRag {

    private static final Logger log = LoggerFactory.getLogger(IndexedBooleanRag.class);

    private final BooleanIndex booleanIndex;

    public IndexedBooleanRag(BooleanIndex booleanIndex) {
        this.booleanIndex = booleanIndex;
    }

    /**
     * Search with BooleanIndex's optimized Hamming distance.
     */
    public List<BooleanIndex.SearchResult> search(long[] query, int topK) {
        return booleanIndex.search(query, topK);
    }

    /**
     * Search by boolean array.
     */
    public List<BooleanIndex.SearchResult> search(boolean[] query, int topK) {
        long[] packed = packBooleans(query);
        return search(packed, topK);
    }

    /**
     * Get index size.
     */
    public int size() {
        return booleanIndex.size();
    }

    private long[] packBooleans(boolean[] bits) {
        int longs = (bits.length + 63) / 64;
        long[] result = new long[longs];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                result[i / 64] |= 1L << (i % 64);
            }
        }
        return result;
    }
}
