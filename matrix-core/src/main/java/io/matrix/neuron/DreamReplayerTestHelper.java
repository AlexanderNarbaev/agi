package io.matrix.neuron;

// Test-only wrapper to expose noosphere.DreamReplayer without
// requiring the test to directly import noosphere package.
public final class DreamReplayerTestHelper {
    private DreamReplayerTestHelper() {}

    public static java.util.List<io.matrix.noosphere.FnlEntry> replayBatch(
            java.util.List<io.matrix.noosphere.FnlEntry> entries, long now) {
        return io.matrix.noosphere.DreamReplayer.replayBatch(entries, now);
    }
}
