package io.matrix.cognitive.distributed;

import io.matrix.cognitive.DispatchIntent;

import java.util.Optional;
import java.util.Set;

/**
 * W53 — the coordinator seen through the kernel's port.
 *
 * <p>It is a thin adapter: it names the capabilities that may be executed remotely, turns the
 * kernel's task into the coordinator's task, and reports what the coordinator concluded. It
 * holds no logic of its own — the durable intent, the dispatch, the evaluation and the
 * verdict all stay in the coordinator, so the kernel and the HTTP entry point drive exactly
 * the same path.</p>
 *
 * <p>The capability set is explicit. A capability that is not in it cannot be delegated, and
 * no class name, no reflection and no plugin id crosses this boundary: the peer resolves the
 * capability id through its own allowlisted registry.</p>
 */
public final class CoordinatorRemoteExecutor implements RemoteExecutor {

    /** The capability chain B really serves. */
    public static final String COGNITIVE_INFERENCE = "brain.cognitive-inference";

    /** The capabilities that may be delegated. */
    private static final Set<String> DELEGABLE = Set.of(COGNITIVE_INFERENCE);

    private final CognitiveCoordinator coordinator;
    private final String workspaceId;

    /**
     * The adapter.
     *
     * @param coordinator  the coordinator that does the work
     * @param workspaceId  the workspace the dispatched tasks belong to
     */
    public CoordinatorRemoteExecutor(CognitiveCoordinator coordinator, String workspaceId) {
        this.coordinator = coordinator;
        this.workspaceId = workspaceId;
    }

    @Override
    public boolean serves(String capabilityId) {
        return capabilityId != null && DELEGABLE.contains(capabilityId);
    }

    @Override
    public RemoteOutcome execute(String capabilityId, String input, String expectation,
                                 String episodeId) {
        // The task id is derived from the episode, so the same episode retries the same
        // request id and the peer's durable inbox recognises it.
        String taskId = "w53-" + (episodeId == null ? "task" : episodeId);
        CognitiveCoordinator.Outcome outcome = coordinator.arrange(
                new CognitiveCoordinator.TaskSpec(taskId, capabilityId, input, expectation,
                        CognitiveCoordinator.DEFAULT_BUDGET_MILLIS,
                        CognitiveCoordinator.DEFAULT_DEADLINE_MILLIS, java.util.List.of(),
                        episodeId));
        Optional<DispatchIntent> intent = coordinator.findDispatch(outcome.requestId());
        String responder = intent.map(DispatchIntent::remoteResponder).orElse(null);
        String status = intent.map(DispatchIntent::remoteStatus).orElse(null);
        return new RemoteOutcome(outcome.requestId(), responder, capabilityId, status,
                outcome.verdict().map(v -> v.disposition().name()).orElse("NONE"),
                outcome.reason(), outcome.reason());
    }

    /**
     * The workspace the delegated tasks belong to.
     *
     * @return the workspace id
     */
    public String workspaceId() {
        return workspaceId;
    }
}
