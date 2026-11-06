package io.matrix.cognitive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W45.2 — the writer lock of a cognitive database file: ONE writer process per file, enforced.
 *
 * <h2>The contract</h2>
 * <p><b>One writer process per cognitive database file.</b> A second process that opens the
 * same file is REFUSED at start-up: it is told which file it tried to open and who holds it,
 * and it does not open the file at all. The topology is a fact, not an assumption.</p>
 *
 * <p>The contract exists because of the W45.1 recovery. That recovery decides whether a
 * claimed request may be executed again by asking the operating system whether the owner of
 * the claim is alive, and it is deliberately conservative: when the liveness of an owner cannot
 * be disproved, the claim is treated as OWNED, because a claim wrongly considered alive is
 * visible while one wrongly considered dead is a duplicated execution. That rule is sound only
 * while there is one writer. A second writer in another container, or on another host, has a
 * process id the first writer cannot see, so the first writer would judge its claims dead and
 * repeat their work.</p>
 *
 * <h2>The mechanism</h2>
 * <p>The lock is a FILE next to the database — {@code <database>.writer.lock} — and it holds two
 * things at once:</p>
 * <ul>
 *   <li>the operating system's lock on that file, which is ATOMIC and which the operating
 *       system releases when the process dies, whatever the process was doing at the time;</li>
 *   <li>a RECORD of the owner: the pid, the start instant of that process, the instance token,
 *       the database the lock belongs to and the instant the lock was taken. The record is what
 *       an operator reads, and it is what tells the next writer whether the previous owner is
 *       dead.</li>
 * </ul>
 *
 * <p>A row in the database cannot hold this lock: the second writer would have to OPEN the
 * database to read it, and opening the database is exactly what is being guarded.</p>
 *
 * <h2>How a dead owner is told from a live one</h2>
 * <p>The same primitives the {@link OwnershipToken} of a claim carries: the pid, the start
 * instant of the process and the instance token. The owner is alive when its pid exists AND the
 * process that owns that pid started at the recorded instant; a pid that was recycled belongs to
 * a later process and is therefore not the owner; and a pid that cannot be seen at all is a
 * process on another host or in another container, whose liveness cannot be disproved — the
 * claim is then treated as OWNED, exactly as the W45.1 rule treats it.</p>
 *
 * <h2>What a kill leaves behind</h2>
 * <p>A store that closes normally DELETES the lock file and releases the operating system's
 * lock. A store whose process is killed leaves the lock file behind with the dead owner's
 * record in it; the operating system's lock is gone, so the next writer may take the file over
 * — and it does, because the owner in the file is dead.</p>
 *
 * <h2>What this cannot cover</h2>
 * <p>The operating system's lock protects the file against another writer on the SAME host, and
 * against another host only when the shared filesystem implements cross-host locks. A volume
 * whose filesystem does not coordinate locks between hosts degrades this guard to a per-host
 * guard, and a second host would then have to be kept out by other means.</p>
 */
public final class WriterLock implements AutoCloseable {

    /** The suffix of the lock file that sits next to the database. */
    public static final String LOCK_SUFFIX = ".writer.lock";

    /** The key of the pid of the owner in the lock file. */
    public static final String KEY_PID = "pid";

    /** The key of the start instant of the owning process in the lock file. */
    public static final String KEY_STARTED_AT = "startedAt";

    /** The key of the instance token of the owner in the lock file. */
    public static final String KEY_TOKEN = "token";

    /** The key of the database the lock belongs to in the lock file. */
    public static final String KEY_DATABASE = "database";

    /** The key of the instant the lock was taken in the lock file. */
    public static final String KEY_CLAIMED_AT = "claimedAt";

    /** How many bytes of the lock file are read. */
    private static final int RECORD_BYTES = 4096;

    private static final System.Logger LOG = System.getLogger(WriterLock.class.getName());

    /**
     * The state every holder of one lock shares.
     *
     * <p>W45.2. The contract is <b>ONE WRITER PROCESS</b> per database file, not one store
     * instance: the cognitive store and the epistemic store open the SAME file with their own
     * connections, and both belong to one process. The lock therefore belongs to the PROCESS,
     * and a second holder inside this JVM joins the lock instead of being refused — what
     * would be refused is a second PROCESS.</p>
     */
    private static final class Holder {
        private final Path databaseFile;
        private final Path lockFile;
        private final OwnershipToken owner;
        private final FileChannel channel;
        private final FileLock fileLock;
        private int references;

        private Holder(Path databaseFile, Path lockFile, OwnershipToken owner,
                       FileChannel channel, FileLock fileLock) {
            this.databaseFile = databaseFile;
            this.lockFile = lockFile;
            this.owner = owner;
            this.channel = channel;
            this.fileLock = fileLock;
            this.references = 1;
        }
    }

    /**
     * The locks this JVM holds, by lock file.
     *
     * <p>The map is the reason a second store in this process joins the lock rather than
     * colliding with it: the operating system's lock on a file cannot be taken twice from one
     * JVM, and two stores of one process are one writer.</p>
     */
    private static final java.util.Map<Path, Holder> HELD = new java.util.concurrent.ConcurrentHashMap<>();

    private final Holder holder;

    private boolean closed;

    private WriterLock(Holder holder) {
        this.holder = holder;
    }

    /**
     * The lock file of a database.
     *
     * @param databaseFile the database
     * @return the lock file that sits next to it
     */
    public static Path lockFileFor(Path databaseFile) {
        Path absolute = databaseFile.toAbsolutePath().normalize();
        Path name = absolute.getFileName();
        return absolute.resolveSibling(name + LOCK_SUFFIX);
    }

    /**
     * Takes the writer lock of a database.
     *
     * @param databaseFile the database to be written
     * @param mine         the identity of this writer
     * @return the lock
     * @throws CognitiveWriterConflictException when another writer holds the database
     */
    public static WriterLock acquire(Path databaseFile, OwnershipToken mine) {
        Path absolute = databaseFile.toAbsolutePath().normalize();
        Path lockFile = lockFileFor(absolute);
        synchronized (WriterLock.class) {
            Holder held = HELD.get(lockFile);
            if (held != null) {
                // THIS PROCESS already writes that file. The contract is one writer
                // PROCESS, so this caller joins the lock it already holds.
                held.references++;
                return new WriterLock(held);
            }
        }
        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
            try {
                fileLock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                // This very JVM already holds the lock on that file: another instance of this
                // process is the writer.
                fileLock = null;
            }
            if (fileLock == null) {
                OwnershipToken holder = readOwner(channel);
                closeQuietly(channel);
                throw refusal(absolute, lockFile, holder,
                        "the operating system reports that another writer holds the lock file");
            }
            OwnershipToken recorded = readOwner(channel);
            if (recorded != null && ownerIsAlive(recorded, mine)) {
                // The operating system gave us the lock, yet the file names a live owner: the
                // lock did not protect us — another host over a shared volume, or a filesystem
                // whose locks are not honoured. The liveness of that owner cannot be
                // disproved, so the owner keeps the file.
                releaseQuietly(fileLock, channel);
                throw refusal(absolute, lockFile, recorded,
                        "the lock file names a live owner");
            }
            writeOwner(channel, mine, absolute);
            Holder holder = new Holder(absolute, lockFile, mine, channel, fileLock);
            synchronized (WriterLock.class) {
                Holder raced = HELD.putIfAbsent(lockFile, holder);
                if (raced != null) {
                    // Another thread of this JVM took the lock in between. One process is
                    // one writer, so this caller joins the lock that was taken.
                    raced.references++;
                    releaseQuietly(fileLock, channel);
                    return new WriterLock(raced);
                }
            }
            return new WriterLock(holder);
        } catch (IOException e) {
            if (fileLock != null) {
                releaseQuietly(fileLock, channel);
            } else {
                closeQuietly(channel);
            }
            throw new IllegalStateException("failed to take the writer lock of the cognitive "
                    + "database " + absolute, e);
        }
    }

    /**
     * Whether the owner recorded in a lock file is still running.
     *
     * <p>The rule is the one of the {@link OwnershipToken}: the owner is alive when its pid
     * exists and the process that owns that pid started at the recorded instant, and it is
     * alive when its liveness cannot be disproved. The instance token distinguishes two
     * instances of one process: the record of an instance that is still running is never
     * mistaken for the record of a dead one.</p>
     *
     * @param recorded the owner recorded in the lock file
     * @param mine     the identity of this writer
     * @return true when the owner may not be displaced
     */
    public static boolean ownerIsAlive(OwnershipToken recorded, OwnershipToken mine) {
        if (recorded.token().equals(mine.token())) {
            return true;
        }
        return recorded.ownerAlive();
    }

    /**
     * The owner recorded in a lock file.
     *
     * @param lockFile the lock file
     * @return the owner, or null when the file holds no readable record
     */
    public static OwnershipToken readOwner(Path lockFile) {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.READ)) {
            return readOwner(channel);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "failed to read the writer lock file " + lockFile, e);
            return null;
        }
    }

    /**
     * Writes the record of an owner into a lock file.
     *
     * <p>This is what a test does to leave a lock behind, and what the guard itself does when
     * it takes a lock over.</p>
     *
     * @param lockFile     the lock file
     * @param owner        the owner to record
     * @param databaseFile the database the lock belongs to
     */
    public static void writeOwner(Path lockFile, OwnershipToken owner, Path databaseFile) {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            writeOwner(channel, owner, databaseFile);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write the writer lock file " + lockFile, e);
        }
    }

    /** The database this lock guards. */
    public Path databaseFile() {
        return holder.databaseFile;
    }

    /** The lock file that sits next to the database. */
    public Path lockFile() {
        return holder.lockFile;
    }

    /** The owner recorded in the lock file. */
    public OwnershipToken owner() {
        return holder.owner;
    }

    /**
     * Releases the lock.
     *
     * <p>The lock file is deleted first, while the operating system's lock is still held, so
     * that no other writer can take the file over between the deletion and the release. Only
     * the record of THIS writer is deleted: a lock file that names somebody else is left
     * alone.</p>
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Holder held = holder;
        boolean last;
        synchronized (WriterLock.class) {
            held.references--;
            last = held.references <= 0;
            if (last) {
                HELD.remove(held.lockFile, held);
            }
        }
        if (!last) {
            // Another holder of this process is still writing the file: the lock stays.
            return;
        }
        try {
            OwnershipToken recorded = readOwner(held.channel);
            if (recorded != null && recorded.token().equals(held.owner.token())) {
                Files.deleteIfExists(held.lockFile);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "failed to delete the writer lock file "
                    + held.lockFile, e);
        }
        releaseQuietly(held.fileLock, held.channel);
    }

    /** The refusal an operator reads. */
    private static CognitiveWriterConflictException refusal(Path databaseFile, Path lockFile,
                                                            OwnershipToken owner, String because) {
        return new CognitiveWriterConflictException(databaseFile, lockFile, owner,
                "the cognitive database " + databaseFile + " is held by another writer: "
                        + describe(owner) + "; " + because + "; the lock file is " + lockFile
                        + "; ONE WRITER PROCESS PER COGNITIVE DATABASE FILE is the contract of "
                        + "this store, and a second writer would make the recovery of an "
                        + "interrupted request unsafe");
    }

    /** How the owner is described to an operator. */
    public static String describe(OwnershipToken owner) {
        if (owner == null) {
            return "an owner that could not be read from the lock file";
        }
        return "process " + owner.pid() + " started at " + owner.startedAt()
                + " (instance " + owner.token() + ")";
    }

    /** Reads the record of the owner out of an open lock file. */
    private static OwnershipToken readOwner(FileChannel channel) throws IOException {
        channel.position(0L);
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_BYTES);
        int read = channel.read(buffer);
        if (read <= 0) {
            return null;
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return parseOwner(new String(bytes, StandardCharsets.UTF_8));
    }

    /** Parses the record of an owner. */
    static OwnershipToken parseOwner(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                fields.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        String pid = fields.get(KEY_PID);
        String token = fields.get(KEY_TOKEN);
        if (pid == null || token == null) {
            return null;
        }
        try {
            return new OwnershipToken(Long.parseLong(pid),
                    fields.getOrDefault(KEY_STARTED_AT, ""), token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Writes the record of an owner into an open lock file. */
    private static void writeOwner(FileChannel channel, OwnershipToken owner, Path databaseFile)
            throws IOException {
        String content = KEY_PID + "=" + owner.pid() + "\n"
                + KEY_STARTED_AT + "=" + owner.startedAt() + "\n"
                + KEY_TOKEN + "=" + owner.token() + "\n"
                + KEY_DATABASE + "=" + databaseFile + "\n"
                + KEY_CLAIMED_AT + "=" + Instant.now() + "\n";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        channel.truncate(0L);
        channel.position(0L);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);
    }

    private static void releaseQuietly(FileLock fileLock, FileChannel channel) {
        if (fileLock != null && fileLock.isValid()) {
            try {
                fileLock.release();
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "failed to release the writer lock", e);
            }
        }
        closeQuietly(channel);
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "failed to close the writer lock file", e);
        }
    }
}
