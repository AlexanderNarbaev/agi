package io.matrix.cognitive;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * W45.1 — the ownership token of the process that claimed a request.
 *
 * <h2>What it is for</h2>
 * <p>An idempotency key that is claimed but never finished must be recoverable. The question a
 * retry has to answer is therefore not "is the row old?" but "is the owner of the claim still
 * running?". A start-up rule that marked every {@code IN_PROGRESS} row as abandoned would be
 * wrong in the simplest way possible: it would steal the work of a process that is alive and
 * working.</p>
 *
 * <p>The token carries three things:</p>
 * <ul>
 *   <li>the <b>pid</b> of the owning process;</li>
 *   <li>the <b>start instant</b> of that process, which distinguishes it from a LATER process
 *       that happens to reuse the same pid — a recycled pid is not the owner;</li>
 *   <li>the <b>token</b>, a random value unique to this instance, which distinguishes two
 *       instances in the same process.</li>
 * </ul>
 *
 * <h2>The topology this assumes</h2>
 * <p>The liveness check is a check on the LOCAL host. MATRIX is a single-user local
 * application whose cognitive store is one SQLite file opened by one application process, so
 * the owner of a claim is a process on this machine and the operating system can be asked about
 * it. The check is deliberately conservative: when the liveness of the owner cannot be
 * established, the claim is treated as OWNED (still running) and is not stolen. A claim that is
 * wrongly considered alive is visible and recoverable by an operator; a claim that is wrongly
 * considered dead is a duplicated execution.</p>
 */
public record OwnershipToken(long pid, String startedAt, String token) {

    /** What the owner of a claim is, from the point of view of the process that asks. */
    public enum Ownership {
        /** The claim was made by this very instance. */
        MINE,
        /** The claim was made by another process, and that process is alive. */
        LIVE_OTHER,
        /** The owner of the claim is gone. */
        DEAD
    }

    /**
     * A token for this instance.
     *
     * <p>Each call makes a NEW token: the token identifies an INSTANCE, not a JVM, so that a
     * service that is rebuilt inside the same JVM does not inherit the claims of the instance
     * it replaced.</p>
     *
     * @return the token of this instance
     */
    public static OwnershipToken current() {
        return new OwnershipToken(ProcessHandle.current().pid(),
                startInstantOf(ProcessHandle.current()).map(Instant::toString).orElse(""),
                UUID.randomUUID().toString());
    }

    /**
     * Classifies the owner of a claim from the point of view of this instance.
     *
     * @param owner the owner recorded in the claim
     * @param mine  the token of this instance
     * @return what the owner is
     */
    public static Ownership classify(OwnershipToken owner, OwnershipToken mine) {
        if (owner.token().equals(mine.token())) {
            return Ownership.MINE;
        }
        if (owner.pid() == mine.pid()) {
            // Another INSTANCE of this very process. The store has one writer per database
            // file, so the instance that made the claim is gone: its work may be recovered.
            return Ownership.DEAD;
        }
        return owner.ownerAlive() ? Ownership.LIVE_OTHER : Ownership.DEAD;
    }

    /**
     * Whether the process that holds this token is demonstrably still running.
     *
     * <p>The check is: the pid exists AND the process that owns that pid started at the
     * recorded instant. A pid that exists but belongs to a process that started later is a
     * RECYCLED pid and not the owner.</p>
     *
     * @return true when the owner is alive, or when its liveness cannot be disproved
     */
    public boolean ownerAlive() {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            // The pid does not exist on this host: the owner is gone. This is the normal
            // case after a SIGKILL, and it is the case the recovery exists for.
            return false;
        }
        Optional<Instant> recorded = recordedStart();
        Optional<Instant> actual = handle.get().info().startInstant();
        if (recorded.isEmpty() || actual.isEmpty()) {
            // The liveness of the owner cannot be disproved, so the claim is treated as
            // OWNED. A claim that is wrongly considered alive is visible; one that is
            // wrongly considered dead is a duplicated execution.
            return true;
        }
        return recorded.get().equals(actual.get());
    }

    /**
     * The start instant this token was recorded with.
     *
     * @return the instant, when it was recorded and it can be parsed
     */
    public Optional<Instant> recordedStart() {
        if (startedAt == null || startedAt.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(startedAt));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> startInstantOf(ProcessHandle handle) {
        try {
            return handle.info().startInstant();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
