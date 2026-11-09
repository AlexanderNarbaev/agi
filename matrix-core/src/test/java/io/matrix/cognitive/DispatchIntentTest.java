package io.matrix.cognitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — chain A's durable intent survives the crash it was written for.
 *
 * <p><b>What these tests hold.</b> The intent to send a task is written before the task
 * is sent, so that a crash between the two leaves a record that says what was meant. The
 * record is chain A's own, in chain A's own database, and it is written by chain A's own
 * single writer — no second writer, no cross-database transaction, no shared file.</p>
 *
 * <p><b>What the unfinished rows are for.</b> After a restart, the dispatches that still
 * owe an answer are visible: the same request id can be sent again, and the peer
 * deduplicates it, so the logical effect still happens once. That is the whole of the
 * recovery story, and it is what makes the delivery at-least-once without the effect being
 * applied twice.</p>
 */
class DispatchIntentTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    @TempDir
    Path directory;

    /** A dispatch intent in its first state. */
    private static DispatchIntent intent(String requestId, DispatchIntent.State state) {
        return new DispatchIntent(requestId, "tsk-" + requestId, "ep-" + requestId, WORKSPACE,
                "brain.cognitive-inference", "hash-" + requestId, state, null, null, null,
                null, null, null, null, AT, AT);
    }

    @Test
    @DisplayName("the intent is durable: it is still there after the store is reopened")
    void theIntentSurvivesTheRestart() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent("req-1", DispatchIntent.State.CREATED));
            store.markDispatchState("req-1", DispatchIntent.State.DISPATCH_PENDING, AT);
        }
        try (CognitiveStore reopened = new CognitiveStore(database)) {
            assertTrue(reopened.findDispatch("req-1").isPresent());
            assertEquals(DispatchIntent.State.DISPATCH_PENDING,
                    reopened.findDispatch("req-1").orElseThrow().state());
        }
    }

    @Test
    @DisplayName("the unfinished dispatches are visible after a restart: that is the "
            + "reconciliation queue")
    void theUnfinishedDispatchesAreVisible() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent("req-1", DispatchIntent.State.DISPATCH_PENDING));
            store.recordDispatchIntent(intent("req-2", DispatchIntent.State.DISPATCHED));
        }
        try (CognitiveStore reopened = new CognitiveStore(database)) {
            List<DispatchIntent> unfinished = reopened.unfinishedDispatches(WORKSPACE);
            assertEquals(2, unfinished.size());
            assertEquals(List.of("req-1", "req-2"),
                    unfinished.stream().map(DispatchIntent::requestId).toList());
        }
    }

    @Test
    @DisplayName("the peer's answer and chain A's verdict are both recorded")
    void theAnswerAndTheVerdictAreRecorded() {
        try (CognitiveStore store = new CognitiveStore(directory.resolve("cognitive.sqlite"))) {
            store.recordDispatchIntent(intent("req-1", DispatchIntent.State.DISPATCH_PENDING));
            store.markDispatchState("req-1", DispatchIntent.State.DISPATCHED, AT);
            store.recordDispatchAnswer("req-1", DispatchIntent.State.COMPLETED,
                    "chain-b-brain-runtime", "COMPLETED", "{\"status\":\"COMPLETED\"}",
                    "{\"relation\":\"INDEPENDENT\"}", AT);
            store.recordDispatchDisposition("req-1", "QUALIFIED",
                    "the report rests on what this process supplied", AT);

            DispatchIntent dispatch = store.findDispatch("req-1").orElseThrow();
            assertEquals(DispatchIntent.State.ADJUDICATED, dispatch.state());
            assertEquals("chain-b-brain-runtime", dispatch.remoteResponder());
            assertEquals("COMPLETED", dispatch.remoteStatus());
            assertEquals("QUALIFIED", dispatch.disposition());
            assertTrue(dispatch.dispositionReason().contains("this process supplied"));
            assertTrue(store.unfinishedDispatches(WORKSPACE).isEmpty(),
                    "an adjudicated dispatch owes nobody an answer");
        }
    }

    @Test
    @DisplayName("the same request id cannot be dispatched twice")
    void theSameRequestIdCannotBeDispatchedTwice() {
        try (CognitiveStore store = new CognitiveStore(directory.resolve("cognitive.sqlite"))) {
            store.recordDispatchIntent(intent("req-1", DispatchIntent.State.CREATED));
            assertThrows(CognitiveConflictException.class,
                    () -> store.recordDispatchIntent(
                            intent("req-1", DispatchIntent.State.CREATED)));
        }
    }

    @Test
    @DisplayName("the peer's own vocabulary is mapped explicitly, and an unknown status is "
            + "not a success")
    void theRemoteVocabularyIsMappedExplicitly() {
        assertEquals(DispatchIntent.State.COMPLETED,
                DispatchIntent.State.fromRemote("COMPLETED"));
        assertEquals(DispatchIntent.State.COMPLETED,
                DispatchIntent.State.fromRemote("PARTIAL"));
        assertEquals(DispatchIntent.State.REFUSED,
                DispatchIntent.State.fromRemote("REFUSED"));
        assertEquals(DispatchIntent.State.UNSUPPORTED,
                DispatchIntent.State.fromRemote("UNSUPPORTED"));
        assertEquals(DispatchIntent.State.FAILED,
                DispatchIntent.State.fromRemote("FAILED"));
        assertEquals(DispatchIntent.State.TIMED_OUT,
                DispatchIntent.State.fromRemote("TIMED_OUT"));
        assertEquals(DispatchIntent.State.DISPATCHED,
                DispatchIntent.State.fromRemote("IN_PROGRESS"));
        assertEquals(null, DispatchIntent.State.fromRemote("WHATEVER"));
        assertEquals(null, DispatchIntent.State.fromRemote(null));
    }

    @Test
    @DisplayName("the terminal and the unfinished states are disjoint and cover the life")
    void theStatesPartitionTheLife() {
        for (DispatchIntent.State state : DispatchIntent.State.values()) {
            if (state == DispatchIntent.State.ADJUDICATED) {
                assertTrue(state.terminal());
                assertFalse(state.unfinished());
                continue;
            }
            assertFalse(state.terminal() && state.unfinished(),
                    "a state cannot be both finished and unfinished: " + state);
        }
    }
}
