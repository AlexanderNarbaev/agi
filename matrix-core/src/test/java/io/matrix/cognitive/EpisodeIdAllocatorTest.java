package io.matrix.cognitive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.0 — the allocator of the identities of a cycle.
 *
 * <p>Two properties matter here and both are about uniqueness: the token of a signal is unique
 * per REQUEST and is never a function of the payload, and the arrival number that orders the
 * signals is durable and never repeats.</p>
 */
class EpisodeIdAllocatorTest {

    @TempDir
    Path directory;

    private CognitiveStore store;
    private EpisodeIdAllocator allocator;

    @BeforeEach
    void setUp() {
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        AtomicInteger counter = new AtomicInteger();
        allocator = new EpisodeIdAllocator(() -> "token-" + counter.incrementAndGet(), store);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("two requests receive two different signal ids")
    void twoRequestsReceiveTwoDifferentSignalIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(allocator.nextSignalId());
        }

        assertThat(ids).hasSize(100);
        assertThat(ids).allMatch(id -> id.startsWith(EpisodeIdAllocator.SIGNAL_PREFIX));
    }

    @Test
    @DisplayName("the production source hands out a different id every time")
    void theProductionSourceHandsOutADifferentIdEveryTime() {
        EpisodeIdAllocator production = new EpisodeIdAllocator(IdSource.random(), store);

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(production.nextSignalId());
        }

        assertThat(ids).hasSize(100);
    }

    @Test
    @DisplayName("the episode of a signal follows the derivation the kernel uses")
    void theEpisodeOfASignalFollowsTheDerivationTheKernelUses() {
        assertThat(EpisodeIdAllocator.episodeIdFor("sig-abc")).isEqualTo("ep-sig-abc");
    }

    @Test
    @DisplayName("the arrival number of the signals never repeats, and survives a restart")
    void theArrivalNumberOfTheSignalsNeverRepeats() {
        assertThat(allocator.nextSignalSequence()).isEqualTo(1L);
        assertThat(allocator.nextSignalSequence()).isEqualTo(2L);

        store.close();
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        allocator = new EpisodeIdAllocator(() -> "token-restart", store);

        assertThat(allocator.nextSignalSequence()).isEqualTo(3L);
    }
}
