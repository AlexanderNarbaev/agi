package io.matrix.cognitive.distributed;

import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /**
     * The reconciliation pass.
     *
     * @param store       chain A's durable store
     * @param coordinator the coordinator that sends the request again
     * @param workspaceId the workspace whose dispatches belong to the pass
     */
    public DispatchReconciler(CognitiveStore store, CognitiveCoordinator coordinator,
                              String workspaceId) {
        this.store = store;
        this.coordinator = coordinator;
        this.workspaceId = workspaceId;
    }

    /**
     * Sends every unfinished dispatch of the workspace again.
     *
     * @return what the pass did
     */
    public Summary reconcile() {
        List<DispatchIntent> unfinished = store.unfinishedDispatches(workspaceId);
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
            } else {
                reasons.add("the dispatch " + intent.requestId() + " is still "
                        + outcome.get().state().name() + ": " + outcome.get().reason());
            }
        }
        return new Summary(unfinished.size(), replayed, finished, List.copyOf(reasons));
    }
}
