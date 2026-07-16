package io.matrix.noosphere;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Knowledge Index — distributed search engine over published FNLs.
 *
 * <p>Indexes FNLs by type, tags, and description keywords.
 * Supports ranked search with accuracy-sorted results.
 * Certified FNLs receive priority in search rankings.
 *
 * <p>CDI: {@code @ApplicationScoped} — injected via constructor.
 *
 * <p>Ref: L6_Memory.md §6.2
 */
@ApplicationScoped
public class KnowledgeIndex {

    public record SearchResult(FnlPackage fnl, double relevance, UUID entryId) {}

    private final NoosphereRegistry registry;
    private final Map<String, List<UUID>> keywordIndex = new HashMap<>();

    @Inject
    public KnowledgeIndex(NoosphereRegistry registry) {
        this.registry = registry;
    }

    /**
     * Indexes all active entries in the registry.
     */
    public void reindex() {
        keywordIndex.clear();
        for (var entry : registry.activeEntries()) {
            indexEntry(entry.entryId(), entry.fnlPackage());
        }
    }

    /**
     * Indexes a single FNL package.
     */
    public void index(UUID entryId, FnlPackage fnl) {
        indexEntry(entryId, fnl);
    }

    private void indexEntry(UUID entryId, FnlPackage fnl) {
        String text = (fnl.name() + " " + fnl.description() + " " + fnl.type()).toLowerCase();
        for (String tag : fnl.tags()) {
            text += " " + tag.toLowerCase();
        }

        for (String word : text.split("[\\s,]+")) {
            if (word.length() >= 3) {
                keywordIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(entryId);
            }
        }
    }

    /**
     * Searches for FNLs matching the query.
     * Results are ranked by relevance (keyword match) × accuracy × certification bonus.
     */
    public List<SearchResult> search(String query) {
        Map<UUID, Double> scores = new HashMap<>();
        String[] keywords = query.toLowerCase().split("[\\s,]+");

        // Empty query: return all active entries sorted by accuracy
        if (keywords.length == 0 || (keywords.length == 1 && keywords[0].isBlank())) {
            return registry.activeEntries().stream()
                    .map(e -> new SearchResult(e.fnlPackage(),
                            e.fnlPackage().accuracy(), e.entryId()))
                    .sorted(Comparator.comparingDouble(SearchResult::relevance).reversed())
                    .toList();
        }

        for (String keyword : keywords) {
            if (keyword.length() < 3) continue;

            List<UUID> matching = keywordIndex.getOrDefault(keyword, List.of());
            for (UUID entryId : matching) {
                var entry = registry.get(entryId);
                if (entry == null || entry.status() != NoosphereRegistry.EntryStatus.ACTIVE) continue;

                double score = scores.getOrDefault(entryId, 0.0);
                score += 1.0;

                if (entry.fnlPackage().description().toLowerCase().contains(keyword)) {
                    score += 0.5;
                }
                if (entry.fnlPackage().name().toLowerCase().contains(keyword)) {
                    score += 0.5;
                }

                scores.put(entryId, score);
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (var entry : scores.entrySet()) {
            UUID entryId = entry.getKey();
            double keywordScore = entry.getValue() / Math.max(keywords.length, 1);

            var registryEntry = registry.get(entryId);
            double accuracy = registryEntry.fnlPackage().accuracy();
            double certBonus = registryEntry.fnlPackage().certified() ? 1.2 : 1.0;

            double relevance = keywordScore * accuracy * certBonus;
            results.add(new SearchResult(registryEntry.fnlPackage(), relevance, entryId));
        }

        results.sort(Comparator.comparingDouble(SearchResult::relevance).reversed());
        return results;
    }

    /**
     * Finds top-N FNLs matching the query.
     */
    public List<SearchResult> findTop(String query, int limit) {
        return search(query).stream().limit(limit).toList();
    }

    public int indexedCount() { return keywordIndex.size(); }
}
