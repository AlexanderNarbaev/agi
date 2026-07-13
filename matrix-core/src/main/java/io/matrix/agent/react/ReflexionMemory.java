package io.matrix.agent.react;

import io.matrix.memory.HierarchicalMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Episodic memory buffer for Reflexion-style self-improvement.
 *
 * <p>Stores complete ReAct cycle traces (observation, reasoning, action, result, reflection)
 * as episodic memories. Supports:
 * <ul>
 *   <li>Temporal ordering of episodes</li>
 *   <li>Semantic search over reflections</li>
 *   <li>Integration with {@link HierarchicalMemory} for long-term persistence</li>
 *   <li>Sliding window with configurable capacity</li>
 *   <li>Failure pattern detection across episodes</li>
 * </ul>
 *
 * <p>The memory buffer acts as a working memory that can promote important reflections
 * to the hierarchical long-term memory for cross-session learning.
 *
 * <p>Ref: Reflexion (Shinn et al., 2023) — Language Agents with Verbal Reinforcement Learning
 */
public final class ReflexionMemory {

    private static final Logger log = LoggerFactory.getLogger(ReflexionMemory.class);

    /** Default maximum episodes in the sliding window. */
    public static final int DEFAULT_CAPACITY = 200;

    /** Minimum importance threshold for promotion to hierarchical memory. */
    private static final double PROMOTION_THRESHOLD = 0.7;

    private final ConcurrentLinkedDeque<ReasoningTrace> episodes;
    private final HierarchicalMemory longTermMemory;
    private final int capacity;
    private final AtomicLong totalEpisodes = new AtomicLong(0);
    private final AtomicLong promotionsCount = new AtomicLong(0);

    /**
     * Creates a ReflexionMemory with default capacity and the given long-term memory.
     */
    public ReflexionMemory(HierarchicalMemory longTermMemory) {
        this(longTermMemory, DEFAULT_CAPACITY);
    }

    /**
     * Creates a ReflexionMemory with explicit capacity.
     */
    public ReflexionMemory(HierarchicalMemory longTermMemory, int capacity) {
        this.longTermMemory = Objects.requireNonNull(longTermMemory, "longTermMemory must not be null");
        this.capacity = Math.max(1, capacity);
        this.episodes = new ConcurrentLinkedDeque<>();
    }

    /**
     * Stores a completed reasoning trace as an episodic memory.
     *
     * <p>If the buffer is at capacity, the oldest episode is evicted.
     * If the trace contains a significant failure reflection, it is
     * automatically promoted to long-term memory.
     *
     * @param trace the reasoning trace to store
     */
    public void store(ReasoningTrace trace) {
        Objects.requireNonNull(trace, "trace must not be null");

        episodes.addLast(trace);
        totalEpisodes.incrementAndGet();

        // Evict oldest if over capacity
        while (episodes.size() > capacity) {
            episodes.pollFirst();
        }

        // Auto-promote failure reflections to long-term memory
        if (shouldPromote(trace)) {
            promoteToLongTerm(trace);
        }

        log.debug("Stored episode tick={}, success={}, episodes={}",
                trace.tick(), trace.actionSuccess(), episodes.size());
    }

    /**
     * Searches for episodes matching the given query in reflections and thoughts.
     *
     * @param query text to search for in reflections and thoughts
     * @param limit maximum number of results
     * @return matching episodes sorted by recency (most recent first)
     */
    public List<ReasoningTrace> search(String query, int limit) {
        if (query == null || query.isEmpty()) {
            return recent(limit);
        }

        String lowerQuery = query.toLowerCase();
        return episodes.stream()
                .filter(t -> matchesQuery(t, lowerQuery))
                .sorted(Comparator.comparingLong(ReasoningTrace::tick).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns the N most recent episodes.
     */
    public List<ReasoningTrace> recent(int count) {
        List<ReasoningTrace> list = new ArrayList<>(episodes);
        Collections.reverse(list); // newest first
        return list.stream().limit(count).collect(Collectors.toList());
    }

    /**
     * Returns episodes where the action failed.
     */
    public List<ReasoningTrace> failures() {
        return episodes.stream()
                .filter(t -> !t.actionSuccess())
                .sorted(Comparator.comparingLong(ReasoningTrace::tick).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Returns episodes where the action succeeded.
     */
    public List<ReasoningTrace> successes() {
        return episodes.stream()
                .filter(ReasoningTrace::actionSuccess)
                .sorted(Comparator.comparingLong(ReasoningTrace::tick).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Detects recurring failure patterns by grouping failures by action type
     * and counting occurrences.
     *
     * @return map of action type name → failure count, sorted by count descending
     */
    public Map<String, Long> detectFailurePatterns() {
        return episodes.stream()
                .filter(t -> !t.actionSuccess() && t.action() != null)
                .collect(Collectors.groupingBy(
                        t -> t.action().type().name(),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    /**
     * Generates a verbal summary of recent failures for self-improvement.
     * This is the core Reflexion mechanism: converting episodic experience
     * into actionable verbal feedback.
     *
     * @param lookback number of recent episodes to analyze
     * @return textual summary of failure patterns and lessons learned
     */
    public String generateReflectionSummary(int lookback) {
        List<ReasoningTrace> recentEpisodes = recent(lookback);
        if (recentEpisodes.isEmpty()) {
            return "No recent episodes to reflect on.";
        }

        long failureCount = recentEpisodes.stream().filter(t -> !t.actionSuccess()).count();
        long successCount = recentEpisodes.size() - failureCount;
        double successRate = (double) successCount / recentEpisodes.size();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Recent %d episodes: %d successes, %d failures (%.1f%% success rate). ",
                recentEpisodes.size(), successCount, failureCount, successRate * 100));

        Map<String, Long> patterns = recentEpisodes.stream()
                .filter(t -> !t.actionSuccess() && t.action() != null)
                .collect(Collectors.groupingBy(
                        t -> t.action().type().name(),
                        Collectors.counting()
                ));

        if (!patterns.isEmpty()) {
            sb.append("Failure patterns: ");
            patterns.forEach((action, count) ->
                    sb.append(String.format("%s(%d) ", action, count)));
        }

        // Extract unique reflections from failures
        List<String> reflections = recentEpisodes.stream()
                .filter(t -> !t.actionSuccess() && !t.reflection().isEmpty())
                .map(ReasoningTrace::reflection)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());

        if (!reflections.isEmpty()) {
            sb.append("Key reflections: ");
            reflections.forEach(r -> sb.append("[").append(r).append("] "));
        }

        return sb.toString().trim();
    }

    /**
     * Retrieves relevant past reflections from long-term memory.
     * Used before action selection to inform current decision.
     *
     * @param context description of current situation
     * @param limit   maximum reflections to retrieve
     * @return relevant long-term reflections
     */
    public List<HierarchicalMemory.MemoryEntry> retrieveRelevantReflections(String context, int limit) {
        return longTermMemory.search(context, limit);
    }

    /**
     * Returns the current number of episodes in the buffer.
     */
    public int size() {
        return episodes.size();
    }

    /**
     * Returns the total number of episodes ever stored (including evicted).
     */
    public long totalEpisodesStored() {
        return totalEpisodes.get();
    }

    /**
     * Returns the number of episodes promoted to long-term memory.
     */
    public long promotionsCount() {
        return promotionsCount.get();
    }

    /**
     * Clears all episodes from the buffer.
     */
    public void clear() {
        episodes.clear();
        log.info("ReflexionMemory cleared");
    }

    // ── Internal ──

    private boolean matchesQuery(ReasoningTrace trace, String lowerQuery) {
        return (trace.thought() != null && trace.thought().toLowerCase().contains(lowerQuery))
                || (trace.reflection() != null && trace.reflection().toLowerCase().contains(lowerQuery))
                || (trace.actionResult() != null && trace.actionResult().toLowerCase().contains(lowerQuery));
    }

    private boolean shouldPromote(ReasoningTrace trace) {
        // Promote failures with non-empty reflections
        return !trace.actionSuccess()
                && trace.reflection() != null
                && !trace.reflection().isEmpty();
    }

    private void promoteToLongTerm(ReasoningTrace trace) {
        String content = String.format(
                "[Reflexion] tick=%d action=%s success=%s reflection='%s'",
                trace.tick(),
                trace.action() != null ? trace.action().type() : "NONE",
                trace.actionSuccess(),
                trace.reflection()
        );

        Set<String> tags = new HashSet<>();
        tags.add("reflexion");
        tags.add(trace.actionSuccess() ? "success" : "failure");
        if (trace.action() != null) {
            tags.add(trace.action().type().name().toLowerCase());
        }

        longTermMemory.store(
                HierarchicalMemory.Level.L1_PATTERN,
                content,
                "agent-reflexion",
                tags
        );
        promotionsCount.incrementAndGet();

        log.debug("Promoted episode tick={} to long-term memory", trace.tick());
    }
}
