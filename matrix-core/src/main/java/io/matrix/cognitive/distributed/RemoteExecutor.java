package io.matrix.cognitive.distributed;

/**
 * W53 — the kernel's port to a remote cognitive capability.
 *
 * <p><b>Why a port.</b> The cognitive kernel must be able to delegate a bounded piece of
 * work to another running process, and it must be able to record what happened — but the
 * kernel is not allowed to know how the message travels or what the peer's brain is. A port
 * says what the kernel needs (can you serve this capability, execute this task, tell me what
 * came back) and nothing about the transport.</p>
 *
 * <p><b>Why the kernel does not choose the peer.</b> The kernel's own
 * {@code AlgorithmSelector} chooses the method; this port only says whether the capability
 * that was chosen happens to be one that another process can serve, and executes it there.
 * There is no second selector and no second ranking: the decision was made once, by the
 * selector that already existed.</p>
 *
 * <p><b>What the outcome may contain.</b> The status the peer named, the disposition chain A
 * reached, and the reason. Nothing here may be treated as an established belief: the remote
 * result is a report, and the disposition is this process's own reading of it.</p>
 */
public interface RemoteExecutor {

    /**
     * Whether another running process can serve this capability.
     *
     * @param capabilityId the capability the selector chose
     * @return true when the capability may be executed remotely
     */
    boolean serves(String capabilityId);

    /**
     * What one remote execution produced.
     *
     * @param requestId    the request id, stable across retries
     * @param responderId  the process that answered
     * @param capabilityId the capability that ran
     * @param status       the status the peer named
     * @param disposition  the disposition this process reached
     * @param reason       why that disposition was reached
     * @param summary      the peer's structured result, as it named it
     */
    record RemoteOutcome(
            String requestId,
            String responderId,
            String capabilityId,
            String status,
            String disposition,
            String reason,
            String summary) {
    }

    /**
     * Executes one bounded task on the peer.
     *
     * @param capabilityId the capability the selector chose
     * @param input        the bounded input of the task
     * @param expectation  the criterion the answer will be judged against
     * @param episodeId    the cognitive episode the task belongs to
     * @return what the peer produced and what this process concluded about it
     */
    RemoteOutcome execute(String capabilityId, String input, String expectation,
                          String episodeId);
}
