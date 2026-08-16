package io.matrix.memory.query;

import io.matrix.api.Text2VecService;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.memory.SqliteMemoryBackend;

import java.util.*;

/**
 * Semantic query over the memory backend using text-to-bits Hamming distance.
 *
 * <p>Converts query text to a 20-bit vector via {@link Text2VecService},
 * then ranks stored entries by Hamming distance (fewer differing bits = more relevant).
 *
 * <p>No LLM, no ML — deterministic bit-level similarity.
 */
public final class SemanticQuery {

    private final SqliteMemoryBackend backend;
    private final Text2VecService text2Vec;

    public SemanticQuery(SqliteMemoryBackend backend) {
        this.backend = Objects.requireNonNull(backend);
        this.text2Vec = new Text2VecService();
    }

    /**
     * Searches all memory entries by semantic similarity to the query text.
     *
     * @param queryText natural language query
     * @param limit     max results
     * @return entries ranked by similarity (most similar first)
     */
    public List<RankedEntry> search(String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) return Collections.emptyList();
        long queryBits = text2Vec.textToBits(queryText);
        if (queryBits == 0) return Collections.emptyList();

        Map<String, HierarchicalMemory.MemoryEntry> all = backend.loadAll();
        List<RankedEntry> ranked = new ArrayList<>();

        for (var entry : all.values()) {
            long contentBits = text2Vec.textToBits(entry.content());
            if (contentBits == 0) continue;
            int hamming = Long.bitCount(queryBits ^ contentBits);
            double score = 1.0 - (double) hamming / Text2VecService.VECTOR_BITS;
            ranked.add(new RankedEntry(entry, score));
        }

        ranked.sort(Comparator.comparingDouble(RankedEntry::score).reversed());
        return ranked.subList(0, Math.min(limit, ranked.size()));
    }

    /**
     * Searches within a specific hierarchy level.
     */
    public List<RankedEntry> searchByLevel(String queryText, HierarchicalMemory.Level level, int limit) {
        return search(queryText, limit).stream()
                .filter(r -> r.entry().level() == level)
                .limit(limit)
                .toList();
    }

    /**
     * A memory entry with its similarity score.
     */
    public record RankedEntry(HierarchicalMemory.MemoryEntry entry, double score) {}
}
