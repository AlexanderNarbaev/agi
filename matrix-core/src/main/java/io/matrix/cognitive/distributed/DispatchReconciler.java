package io.matrix.cognitive.distributed;

import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * W53 — the pass that finishes what a crash left unfinished.
 *
 * <p><b>Why this exists.</b> The coordinator writes its intent down before it sends, so
 * that a delivery whose answer was lost can be finished afterwards: the unfinished
 * dispatch is still on record, the request that was committed to it is still on record,
 * and the same request can be sent again. The peer recognises the request id and does
 * not execute the task a second time — that is at-least-once delivery with
 * effectively-once logical effects, and it is deliberately not a distributed exactly-once
 * claim.</p>
 *
 * <p><b>What it does not do.</b> It does not invent work. It only looks at the dispatches
 * that were committed to a peer and never finished, and it sends each of them again. A
 * dispatch whose request cannot be read is reported as unfinished, not retried blindly.</p>
 */
public final class DispatchReconciler {

    /**
     * What the pass did.
     *
     * @param examined how many unfinished dispatches were found
     * @param replayed  how many of them were sent again
     * @param finished  how many of them ended in a verdict
     * @param reasons   why the others did not finish, in words
     */
    public record Summary(int examined, int replayed, int finished, List<String> reasons) {
    }

    private final CognitiveStore store;
    private final CognitiveCoordinator coordinator;
    private final String workspaceId;
    private final Supplier<String> clock;

    /**
     * The reconciliation pass.
     *
     * @param store       chain A's durable store
     * @param coordinator the coordinator that sends the request again
     * @param workspaceId the workspace whose dispatches belong to the pass
     */
    public DispatchReconciler(CognitiveStore store, CognitiveCoordinator coordinator,
                              String workspaceId) {
        this(store, coordinator, workspaceId, () -> java.time.Instant.now().toString());
    }

    /**
     * The reconciliation pass, with its own clock.
     *
     * @param store       chain A's durable store
     * @param coordinator the coordinator that sends the request again
     * @param workspaceId the workspace whose dispatches belong to the pass
     * @param clock       the source of the instants the pass writes
     */
    public DispatchReconciler(CognitiveStore store, CognitiveCoordinator coordinator,
                              String workspaceId, Supplier<String> clock) {
        this.store = store;
        this.coordinator = coordinator;
        this.workspaceId = workspaceId;
        this.clock = clock;
    }

    /**
     * Sends every unfinished dispatch of the workspace again.
     *
     * @return what the pass did
     */
    public Summary reconcile() {
        return pass(store.unfinishedDispatches(workspaceId));
    }

    /**
     * Sends the unfinished dispatches that are not in flight again.
     *
     * <p><b>Why the pass has a second mode.</b> The startup pass runs when nothing is in
     * flight: the process has just died, so every unfinished dispatch is work that was
     * lost. The timer pass runs while the process is up, and there the dispatch that is
     * in flight and the dispatch whose answer was lost look the same on the record. The
     * pass asks the coordinator which requests this process is working on right now, and
     * it leaves those alone: a second delivery of a request that is still running spends
     * one retry of the durable budget for nothing.</p>
     *
     * <p><b>What the pass does not decide.</b> It does not read the wall clock to decide
     * what is old. The wall clock decides <i>when</i> the pass runs; the pass decides
     * <i>what</i> to send, and it decides that by the durable record and by what this
     * process is doing right now.</p>
     *
     * @return what the pass did
     */
    public Summary reconcileStale() {
        List<DispatchIntent> stale = new ArrayList<>();
        for (DispatchIntent intent : store.unfinishedDispatches(workspaceId)) {
            if (coordinator.isInFlight(intent.requestId())) {
                continue;
            }
            stale.add(intent);
        }
        return pass(stale);
    }

    /**
     * The work of the pass, over the dispatches it was given.
     *
     * @param unfinished the dispatches to send again
     * @return what the pass did
     */
    private Summary pass(List<DispatchIntent> unfinished) {
        List<String> reasons = new ArrayList<>();
        int replayed = 0;
        int finished = 0;
        for (DispatchIntent intent : unfinished) {
            Optional<CognitiveCoordinator.Outcome> outcome;
            try {
                outcome = coordinator.replayByRequestId(intent.requestId());
            } catch (RuntimeException e) {
                // One unreachable dispatch must not stop the pass: the others may still be
                // finishable, and a peer that is down is a fact, not an exception.
                reasons.add("the dispatch " + intent.requestId() + " could not be sent again: "
                        + e.getMessage());
                continue;
            }
            if (outcome.isEmpty()) {
                reasons.add("the dispatch " + intent.requestId()
                        + " carries no request to send again");
                continue;
            }
            replayed++;
            if (outcome.get().state() == DispatchIntent.State.ADJUDICATED) {
                finished++;
            } else if (outcome.get().state() == DispatchIntent.State.FAILED) {
                // The dispatch will not be sent again: the retry budget is spent. The
                // row is closed, so it owes chain A nothing any more and the pass
                // leaves it alone. Without this the row would stay unfinished for ever
                // and every pass would keep picking it up.
                closeAsFailed(intent, outcome.get().reason());
                finished++;
                reasons.add("the dispatch " + intent.requestId() + " is finished as failed: "
                        + outcome.get().reason());
            } else {
                reasons.add("the dispatch " + intent.requestId() + " is still "
                        + outcome.get().state().name() + ": " + outcome.get().reason());
            }
        }
        return new Summary(unfinished.size(), replayed, finished, List.copyOf(reasons));
    }

    /**
     * Closes a dispatch that will not be sent again.
     *
     * @param intent the dispatch
     * @param reason why it is closed
     */
    private void closeAsFailed(DispatchIntent intent, String reason) {
        if (intent.state() == DispatchIntent.State.FAILED) {
            return;
        }
        store.closeDispatchAsFailed(intent.requestId(), reason, clock.get());
    }
}
