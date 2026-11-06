package io.matrix.knowledge;

/**
 * W45.2 — the outcome of a load: what was loaded, and the snapshot of what it replaced.
 *
 * <p>The two halves matter separately. The loaded content is what the workspace holds now;
 * the snapshot is what it held before, and it is the thing that makes the replacement
 * recoverable. A load that replaced an empty workspace has no snapshot, because there was
 * nothing to preserve — and saying so is better than reporting a snapshot of nothing.</p>
 *
 * @param workspaceId the workspace that was loaded
 * @param loaded      the content that was loaded
 * @param snapshot    the snapshot of what was replaced, or null when nothing was replaced
 */
public record WorkspaceLoad(String workspaceId, KnowledgeSnapshot loaded, WorkspaceSnapshot snapshot) {

    /** Whether the load replaced a workspace that held something. */
    public boolean replaced() {
        return snapshot != null;
    }
}
