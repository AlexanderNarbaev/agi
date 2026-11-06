package io.matrix.knowledge;

/**
 * W45.2 — the refusal to replace a workspace that holds something.
 *
 * <p>The owner's decision: a destructive workspace reload fails closed. The refusal is an
 * exception and not a flag because a flag can be ignored, and because the caller has to be
 * TOLD what to do: the message names the parameter that asks for the replacement, so a
 * caller that reads the refusal does not have to guess.</p>
 *
 * <p>Nothing is deleted when this is thrown. The check happens before the transaction that
 * would delete anything is opened, so a refused load leaves the workspace exactly as it was
 * — the contradictions, the evidence and the revisions included.</p>
 */
public class WorkspaceReplacementRefused extends RuntimeException {

    private final String workspaceId;

    /**
     * A refusal.
     *
     * @param workspaceId the workspace that was not replaced
     * @param message     what the caller must send to replace it
     */
    public WorkspaceReplacementRefused(String workspaceId, String message) {
        super(message);
        this.workspaceId = workspaceId;
    }

    /** The workspace that was not replaced. */
    public String workspaceId() {
        return workspaceId;
    }
}
