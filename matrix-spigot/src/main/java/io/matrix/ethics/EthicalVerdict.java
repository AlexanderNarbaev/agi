package io.matrix.ethics;

/**
 * Ethical verdict for an action evaluated by {@link EthicalFilter}.
 *
 * <p>Ref: L7_Ethics.md §3.3
 */
@Deprecated(since = "2.2.0", forRemoval = true)
@SuppressWarnings("removal")
public enum EthicalVerdict {
    APPROVED,
    REJECTED,
    ESCALATED,
    MODIFIED
}
