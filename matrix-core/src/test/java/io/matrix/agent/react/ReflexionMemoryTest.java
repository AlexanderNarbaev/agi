package io.matrix.agent.react;

import io.matrix.agent.AgentAction;
import io.matrix.memory.HierarchicalMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReflexionMemoryTest {

    private HierarchicalMemory longTermMemory;
    private ReflexionMemory memory;

    @BeforeEach
    void setUp() {
        longTermMemory = new HierarchicalMemory(100);
        memory = new ReflexionMemory(longTermMemory, 50);
    }

    // ── Storage ──

    @Test
    void shouldStoreEpisode() {
        ReasoningTrace trace = buildTrace(1, true, "success");

        memory.store(trace);

        assertThat(memory.size()).isEqualTo(1);
        assertThat(memory.totalEpisodesStored()).isEqualTo(1);
    }

    @Test
    void shouldEvictOldestWhenOverCapacity() {
        ReflexionMemory smallMemory = new ReflexionMemory(longTermMemory, 3);

        smallMemory.store(buildTrace(1, true, "a"));
        smallMemory.store(buildTrace(2, true, "b"));
        smallMemory.store(buildTrace(3, true, "c"));
        smallMemory.store(buildTrace(4, true, "d"));

        assertThat(smallMemory.size()).isEqualTo(3);
        assertThat(smallMemory.totalEpisodesStored()).isEqualTo(4);

        // Oldest (tick 1) should be evicted
        List<ReasoningTrace> recent = smallMemory.recent(10);
        assertThat(recent).hasSize(3);
        assertThat(recent.stream().mapToLong(ReasoningTrace::tick).max().orElse(0)).isEqualTo(4);
    }

    @Test
    void shouldPromoteFailureReflectionsToLongTermMemory() {
        ReasoningTrace failureTrace = buildTrace(1, false, "action failed because X");

        memory.store(failureTrace);

        assertThat(memory.promotionsCount()).isEqualTo(1);
        // Verify it was stored in long-term memory
        List<HierarchicalMemory.MemoryEntry> entries = longTermMemory.search("reflexion", 10);
        assertThat(entries).isNotEmpty();
        assertThat(entries.get(0).content()).contains("success=false");
        assertThat(entries.get(0).content()).contains("action failed because X");
    }

    @Test
    void shouldNotPromoteSuccessesToLongTermMemory() {
        ReasoningTrace successTrace = buildTrace(1, true, "everything worked");

        memory.store(successTrace);

        assertThat(memory.promotionsCount()).isZero();
    }

    @Test
    void shouldNotPromoteFailuresWithoutReflection() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(1).observation(0L).thought("test")
                .action(new AgentAction(AgentAction.ActionType.MOVE))
                .actionSuccess(false)
                .reflection("")  // empty reflection
                .build();

        memory.store(trace);

        assertThat(memory.promotionsCount()).isZero();
    }

    // ── Search ──

    @Test
    void shouldSearchByThoughtContent() {
        memory.store(buildTraceWithThought(1, "curiosity is high, should explore"));
        memory.store(buildTraceWithThought(2, "energy is low, should eat"));
        memory.store(buildTraceWithThought(3, "explore the northern area"));

        List<ReasoningTrace> results = memory.search("explore", 10);

        assertThat(results).hasSize(2);
    }

    @Test
    void shouldSearchByReflection() {
        memory.store(buildTrace(1, false, "failed because blocked by wall"));
        memory.store(buildTrace(2, true, "successfully mined ore"));

        List<ReasoningTrace> results = memory.search("wall", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).tick()).isEqualTo(1);
    }

    @Test
    void shouldSearchByActionResult() {
        memory.store(buildTraceWithResult(1, true, "found diamond vein"));
        memory.store(buildTraceWithResult(2, false, "nothing found"));

        List<ReasoningTrace> results = memory.search("diamond", 10);

        assertThat(results).hasSize(1);
    }

    @Test
    void shouldReturnRecentWhenQueryEmpty() {
        memory.store(buildTrace(1, true, "a"));
        memory.store(buildTrace(2, true, "b"));
        memory.store(buildTrace(3, true, "c"));

        List<ReasoningTrace> results = memory.search("", 2);

        assertThat(results).hasSize(2);
        // Should be sorted by tick descending (most recent first)
        assertThat(results.get(0).tick()).isGreaterThan(results.get(1).tick());
    }

    // ── Filtering ──

    @Test
    void shouldReturnFailures() {
        memory.store(buildTrace(1, true, "ok"));
        memory.store(buildTrace(2, false, "failed"));
        memory.store(buildTrace(3, true, "ok"));
        memory.store(buildTrace(4, false, "also failed"));

        List<ReasoningTrace> failures = memory.failures();

        assertThat(failures).hasSize(2);
        assertThat(failures).allMatch(t -> !t.actionSuccess());
    }

    @Test
    void shouldReturnSuccesses() {
        memory.store(buildTrace(1, true, "ok"));
        memory.store(buildTrace(2, false, "failed"));
        memory.store(buildTrace(3, true, "ok"));

        List<ReasoningTrace> successes = memory.successes();

        assertThat(successes).hasSize(2);
        assertThat(successes).allMatch(ReasoningTrace::actionSuccess);
    }

    // ── Failure Pattern Detection ──

    @Test
    void shouldDetectFailurePatterns() {
        memory.store(buildTraceWithType(1, false, AgentAction.ActionType.MOVE, "blocked"));
        memory.store(buildTraceWithType(2, false, AgentAction.ActionType.MOVE, "blocked again"));
        memory.store(buildTraceWithType(3, false, AgentAction.ActionType.MINE, "no pickaxe"));
        memory.store(buildTraceWithType(4, true, AgentAction.ActionType.CRAFT, "crafted"));

        Map<String, Long> patterns = memory.detectFailurePatterns();

        assertThat(patterns).containsKey("MOVE");
        assertThat(patterns.get("MOVE")).isEqualTo(2);
        assertThat(patterns).containsKey("MINE");
        assertThat(patterns.get("MINE")).isEqualTo(1);
        // CRAFT succeeded, so not in failure patterns
        assertThat(patterns).doesNotContainKey("CRAFT");
    }

    // ── Reflection Summary ──

    @Test
    void shouldGenerateReflectionSummary() {
        memory.store(buildTrace(1, true, "ok"));
        memory.store(buildTrace(2, false, "wall blocked path"));
        memory.store(buildTrace(3, false, "wall blocked again"));

        String summary = memory.generateReflectionSummary(10);

        assertThat(summary).contains("3 episodes");
        assertThat(summary).contains("1 successes");
        assertThat(summary).contains("2 failures");
        assertThat(summary).contains("33.3% success rate");
    }

    @Test
    void shouldHandleEmptyMemoryForSummary() {
        String summary = memory.generateReflectionSummary(10);

        assertThat(summary).contains("No recent episodes");
    }

    // ── Long-term Memory Retrieval ──

    @Test
    void shouldRetrieveRelevantReflections() {
        // Store a failure that gets promoted
        memory.store(buildTrace(1, false, "important lesson about mining"));

        List<HierarchicalMemory.MemoryEntry> reflections =
                memory.retrieveRelevantReflections("mining", 10);

        assertThat(reflections).isNotEmpty();
    }

    // ── Recent ──

    @Test
    void shouldReturnRecentEpisodesInOrder() {
        memory.store(buildTrace(1, true, "a"));
        memory.store(buildTrace(2, true, "b"));
        memory.store(buildTrace(3, true, "c"));

        List<ReasoningTrace> recent = memory.recent(2);

        assertThat(recent).hasSize(2);
        // Most recent first
        assertThat(recent.get(0).tick()).isEqualTo(3);
        assertThat(recent.get(1).tick()).isEqualTo(2);
    }

    // ── Clear ──

    @Test
    void shouldClearMemory() {
        memory.store(buildTrace(1, true, "a"));
        memory.store(buildTrace(2, false, "b"));

        memory.clear();

        assertThat(memory.size()).isZero();
    }

    // ── Helpers ──

    private ReasoningTrace buildTrace(long tick, boolean success, String reflection) {
        return new ReasoningTrace.Builder()
                .tick(tick)
                .observation(tick * 0x1000L)
                .thought("thought at tick " + tick)
                .action(new AgentAction(AgentAction.ActionType.MOVE))
                .actionResult(success ? "success" : "failure")
                .actionSuccess(success)
                .reflection(reflection)
                .build();
    }

    private ReasoningTrace buildTraceWithThought(long tick, String thought) {
        return new ReasoningTrace.Builder()
                .tick(tick)
                .observation(0L)
                .thought(thought)
                .action(new AgentAction(AgentAction.ActionType.MOVE))
                .actionResult("result")
                .actionSuccess(true)
                .reflection("ok")
                .build();
    }

    private ReasoningTrace buildTraceWithResult(long tick, boolean success, String result) {
        return new ReasoningTrace.Builder()
                .tick(tick)
                .observation(0L)
                .thought("thought")
                .action(new AgentAction(AgentAction.ActionType.MINE))
                .actionResult(result)
                .actionSuccess(success)
                .reflection(success ? "good" : "bad")
                .build();
    }

    private ReasoningTrace buildTraceWithType(long tick, boolean success,
                                               AgentAction.ActionType type, String reflection) {
        return new ReasoningTrace.Builder()
                .tick(tick)
                .observation(0L)
                .thought("thought")
                .action(new AgentAction(type))
                .actionResult(success ? "success" : "failure")
                .actionSuccess(success)
                .reflection(reflection)
                .build();
    }
}
