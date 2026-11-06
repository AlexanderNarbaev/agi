package io.matrix.cognitive;

import java.nio.file.Path;

/**
 * W45.2 — the refusal to open a cognitive database that another writer holds.
 *
 * <h2>What is refused</h2>
 * <p>A second process that opens the same cognitive database file is REFUSED at start-up. The
 * refusal is not a warning and not a retry: the second writer is told which file it tried to
 * open and who holds it, and it does not open the file at all.</p>
 *
 * <p>This is not a {@link CognitiveConflictException} (a request contradicts a record, and the
 * record is intact) and not a {@link CognitiveRequestFailure} (a request's outcome is unknown).
 * Nothing is wrong with the data and nothing is uncertain about a request: the database simply
 * has an owner, and the contract says it may have only one.</p>
 *
 * <h2>Why the refusal is the safe answer</h2>
 * <p>The W45.1 recovery decides whether a claimed request may be executed again by asking the
 * operating system whether the owner of the claim is alive. When the liveness of an owner
 * cannot be disproved, the claim is treated as OWNED, because a claim wrongly considered alive
 * is visible while one wrongly considered dead is a duplicated execution.</p>
 *
 * <p>That rule is sound only while there is ONE writer per database file. A second writer in
 * another container, or on another host, has a process id the first writer cannot see, so the
 * first writer would judge its claims DEAD and repeat their work. Refusing the second writer
 * turns the assumption into a fact.</p>
 */
public class CognitiveWriterConflictException extends RuntimeException {

    private final Path databaseFile;
    private final Path lockFile;
    private final OwnershipToken owner;

    /**
     * A refusal.
     *
     * @param databaseFile the database the second writer tried to open
     * @param lockFile     the lock file that holds the record of the owner
     * @param owner        the owner, or null when the lock file could not be read
     * @param message      what the operator is told
     */
    public CognitiveWriterConflictException(Path databaseFile, Path lockFile, OwnershipToken owner,
                                            String message) {
        super(message);
        this.databaseFile = databaseFile;
        this.lockFile = lockFile;
        this.owner = owner;
    }

    /** The database the second writer tried to open. */
    public Path databaseFile() {
        return databaseFile;
    }

    /** The lock file that holds the record of the owner. */
    public Path lockFile() {
        return lockFile;
    }

    /**
     * The owner of the database.
     *
     * @return the owner, or null when the lock file could not be read
     */
    public OwnershipToken owner() {
        return owner;
    }
}
