package io.matrix.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W41 §5.1 — an episode is not a fact.
 *
 * <p>The audit found the loop: the pipeline searched long-term memory for
 * the input, prepended the hits to the prompt, generated, and wrote the
 * generated answer back into the same memory. The system's own output became
 * part of its next input, and nothing in the store could tell an
 * interaction from a learned fact.
 *
 * <p>The mission's rule: "A conversational answer may be preserved as an
 * episodic record of what was said. It may not become a verified fact
 * without the corresponding evidence process."
 */
class EpisodeSeparationTest {

    @Test
    @DisplayName("an episode is kept, and marked")
    void anEpisodeIsKeptAndMarked() {
        HierarchicalMemory memory = new HierarchicalMemory();

        memory.store(HierarchicalMemory.Level.L2_MODULE,
                "Q: what is photosynthesis? | A: it is how plants make sugar",
                "brain", Set.of("pipeline", "auto", HierarchicalMemory.EPISODE_TAG));

        // The record is preserved: an interaction is a legitimate episode.
        assertThat(memory.entriesAtLevel(HierarchicalMemory.Level.L2_MODULE)).hasSize(1);
        assertThat(memory.entriesAtLevel(HierarchicalMemory.Level.L2_MODULE).get(0).tags())
                .contains(HierarchicalMemory.EPISODE_TAG);
    }

    @Test
    @DisplayName("the retrieval that feeds generation withholds the episodes")
    void theGenerationRetrievalWithholdsTheEpisodes() {
        HierarchicalMemory memory = new HierarchicalMemory();

        memory.store(HierarchicalMemory.Level.L2_MODULE,
                "Q: photosynthesis | A: the plant makes sugar from light",
                "brain", Set.of("pipeline", "auto", HierarchicalMemory.EPISODE_TAG));
        memory.store(HierarchicalMemory.Level.L3_QUANTUM,
                "Photosynthesis converts light energy into chemical energy stored in sugars",
                "knowledge", Set.of("ingested"));

        List<HierarchicalMemory.MemoryEntry> plain = memory.search("photosynthesis", 5);
        assertThat(plain).hasSize(2);

        List<HierarchicalMemory.MemoryEntry> forGeneration = memory.search("photosynthesis", 5,
                Set.of(HierarchicalMemory.EPISODE_TAG));
        assertThat(forGeneration).hasSize(1);
        assertThat(forGeneration.get(0).content())
                .contains("converts light energy into chemical energy");
        assertThat(forGeneration.get(0).content()).doesNotContain("the plant makes sugar from light");
    }

    @Test
    @DisplayName("excluding nothing changes nothing")
    void excludingNothingChangesNothing() {
        HierarchicalMemory memory = new HierarchicalMemory();
        memory.store(HierarchicalMemory.Level.L1_PATTERN, "a learned pattern", "k", Set.of("x"));

        assertThat(memory.search("learned", 5)).hasSize(1);
        assertThat(memory.search("learned", 5, Set.of())).hasSize(1);
    }
}
