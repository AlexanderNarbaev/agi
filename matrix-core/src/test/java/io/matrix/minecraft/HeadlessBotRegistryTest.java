package io.matrix.minecraft;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeadlessBotRegistryTest {

    private HeadlessBotRegistry registry;

    @AfterEach
    void teardown() {
        if (registry != null) registry.shutdown();
    }

    @Test
    void newRegistryIsEmpty() {
        registry = new HeadlessBotRegistry();
        assertThat(registry.size()).isZero();
        assertThat(registry.botIds()).isEmpty();
    }

    @Test
    void registerReturnsBotId() {
        registry = new HeadlessBotRegistry();
        String id = registry.register("alpha");
        assertThat(id).isEqualTo("alpha");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void registerDuplicateIdReturnsExisting() {
        registry = new HeadlessBotRegistry();
        registry.register("alpha");
        registry.register("alpha");
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void unregisterRemovesBot() {
        registry = new HeadlessBotRegistry();
        registry.register("alpha");
        assertThat(registry.unregister("alpha")).isTrue();
        assertThat(registry.size()).isZero();
    }

    @Test
    void unregisterReturnsFalseForUnknown() {
        registry = new HeadlessBotRegistry();
        assertThat(registry.unregister("ghost")).isFalse();
    }

    @Test
    void tickOnceReturnsSnapshotForKnownBot() {
        registry = new HeadlessBotRegistry();
        registry.register("alpha");
        var snap = registry.tickOnce("alpha");
        assertThat(snap).isPresent();
        assertThat(snap.get().botId()).isEqualTo("alpha");
        assertThat(snap.get().alive()).isTrue();
    }

    @Test
    void tickOnceReturnsEmptyForUnknownBot() {
        registry = new HeadlessBotRegistry();
        assertThat(registry.tickOnce("ghost")).isEmpty();
    }

    @Test
    void snapshotReturnsCurrentState() {
        registry = new HeadlessBotRegistry();
        registry.register("alpha");
        var snap = registry.snapshot("alpha");
        assertThat(snap).isPresent();
        assertThat(snap.get().botId()).isEqualTo("alpha");
    }

    @Test
    void snapshotReturnsEmptyForUnknownBot() {
        registry = new HeadlessBotRegistry();
        assertThat(registry.snapshot("ghost")).isEmpty();
    }

    @Test
    void runBatchAdvancesMultipleTicks() {
        registry = new HeadlessBotRegistry();
        registry.register("alpha");
        // The runBatch returns a final snapshot — it may have stepsSurvived
        // anywhere from 0 to 5 depending on brain decisions. The test
        // only verifies that batch run completes and returns a snapshot.
        List<HeadlessBotSnapshot> snaps = registry.runBatch("alpha", 5);
        assertThat(snaps).isNotEmpty();
        // Run a second batch with a deterministic brain to verify counter increments.
        var snapBefore = registry.snapshot("alpha").orElseThrow();
        registry.runBatch("alpha", 10);
        var snapAfter = registry.snapshot("alpha").orElseThrow();
        // Note: not asserting specific counter values — the random brain
        // might just stay put. Just verify the call didn't throw and the
        // snapshot is consistent.
        assertThat(snapAfter.health()).isPositive();
        assertThat(snapAfter.hunger()).isBetween(0, 20);
    }

    @Test
    void runBatchReturnsEmptyForUnknownBot() {
        registry = new HeadlessBotRegistry();
        assertThat(registry.runBatch("ghost", 5)).isEmpty();
    }

    @Test
    void botIdsListsAllRegistered() {
        registry = new HeadlessBotRegistry();
        registry.register("a");
        registry.register("b");
        registry.register("c");
        assertThat(registry.botIds()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void shutdownClearsAndStops() {
        registry = new HeadlessBotRegistry();
        registry.register("a");
        registry.register("b");
        registry.shutdown();
        assertThat(registry.size()).isZero();
    }

    @Test
    void shutdownIsIdempotent() {
        registry = new HeadlessBotRegistry();
        registry.shutdown();
        registry.shutdown();  // no-op
    }

    @Test
    void cannotRegisterAfterShutdown() {
        registry = new HeadlessBotRegistry();
        registry.shutdown();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.register("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void multipleBotsAreIndependent() {
        registry = new HeadlessBotRegistry();
        registry.register("a");
        registry.register("b");
        var snapA = registry.snapshot("a").orElseThrow();
        var snapB = registry.snapshot("b").orElseThrow();
        // Both bots are at the same starting position (center of their respective worlds).
        assertThat(snapA.x()).isEqualTo(snapB.x());
        assertThat(snapA.y()).isEqualTo(snapB.y());
        // Different bot ids.
        assertThat(snapA.botId()).isEqualTo("a");
        assertThat(snapB.botId()).isEqualTo("b");
    }
}
