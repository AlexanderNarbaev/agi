package io.matrix.cognitive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W45.2 — one writer process per cognitive database file, enforced.
 *
 * <p>The owner decided that the topology must be a FACT rather than an assumption: a second
 * process that opens the same cognitive database is refused at start-up. These tests check the
 * refusal, the release and the recovery — including the two cases that are easy to get wrong:
 * a lock left behind by a process that was KILLED (the file stays, and the next writer takes
 * it over) and a lock held by a process that is ALIVE (the next writer is refused).</p>
 *
 * <p>The cross-process cases run a real second JVM, because the refusal of a second process is
 * the decision being made and an in-process simulation cannot make it.</p>
 */
class CognitiveWriterLockTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** A pid that cannot exist on this host: the owner of it is dead by definition. */
    private static final long IMPOSSIBLE_PID = 999_999_999L;

    /** How long the helper process holds the lock before it gives it back by itself. */
    private static final long HOLD_MILLIS = 120_000L;

    /** How long a test waits for the helper process to take the lock. */
    private static final long WAIT_MILLIS = 60_000L;

    /** How long a test sleeps between two attempts to see the helper's record. */
    private static final long POLL_INTERVAL_MILLIS = 50L;

    /** The line the helper process prints once it holds the lock. */
    private static final String HELD_PREFIX = "HELD ";

    @TempDir
    Path directory;

    private final List<Process> helpers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Process helper : helpers) {
            helper.destroyForcibly();
        }
        helpers.clear();
    }

    @Test
    @DisplayName("a second store of the SAME process joins the writer it already holds")
    void aSecondStoreOfTheSameProcessJoinsTheWriterItAlreadyHolds() {
        // W45.2. The contract is ONE WRITER PROCESS per database file. The cognitive store
        // and the epistemic store open the SAME file with their own connections, and both
        // belong to one process — so a second store of this process is the SAME writer and
        // joins the lock instead of colliding with it. What is refused is a second
        // PROCESS, which the test below proves.
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore holder = new CognitiveStore(database)) {
            try (CognitiveStore second = new CognitiveStore(database)) {
                assertThat(second.writerLock()).isPresent();
                // Both holders name the SAME owner: one process, one writer.
                assertThat(second.writerLock().orElseThrow().owner().token())
                        .isEqualTo(holder.writerLock().orElseThrow().owner().token());
                assertThat(holder.writerLock().orElseThrow().owner().pid())
                        .isEqualTo(ProcessHandle.current().pid());
            }
            // The second holder closed; the first still holds the lock, so the lock file
            // must still be there.
            assertThat(WriterLock.lockFileFor(database)).exists();
        }
        // The last holder closed: the lock file is gone and the file can be opened again.
        assertThat(WriterLock.lockFileFor(database)).doesNotExist();
        try (CognitiveStore again = new CognitiveStore(database)) {
            assertThat(again.writerLock()).isPresent();
        }
    }

    @Test
    @DisplayName("a store on a different file is not affected by the guard")
    void aStoreOnADifferentFileIsNotAffected() {
        Path first = directory.resolve("first.sqlite");
        Path second = directory.resolve("second.sqlite");

        try (CognitiveStore one = new CognitiveStore(first);
             CognitiveStore two = new CognitiveStore(second)) {
            assertThat(one.writerLock()).isPresent();
            assertThat(two.writerLock()).isPresent();
            assertThat(WriterLock.lockFileFor(first)).isNotEqualTo(WriterLock.lockFileFor(second));
        }
    }

    @Test
    @DisplayName("the guard is released when the holder closes, and the file opens again")
    void theGuardIsReleasedWhenTheHolderCloses() {
        Path database = directory.resolve("cognitive.sqlite");
        Path lockFile = WriterLock.lockFileFor(database);

        CognitiveStore holder = new CognitiveStore(database);
        assertThat(lockFile).exists();
        holder.close();
        // The lock file is gone, so nothing is left behind for the next writer to explain.
        assertThat(lockFile).doesNotExist();

        try (CognitiveStore reopened = new CognitiveStore(database)) {
            assertThat(reopened.writerLock()).isPresent();
        }
    }

    @Test
    @DisplayName("a lock left behind by a dead owner does not block the next writer")
    void aLockLeftBehindByADeadOwnerDoesNotBlock() {
        Path database = directory.resolve("cognitive.sqlite");
        Path lockFile = WriterLock.lockFileFor(database);
        OwnershipToken dead = new OwnershipToken(IMPOSSIBLE_PID, INSTANT, "dead-owner");
        WriterLock.writeOwner(lockFile, dead, database.toAbsolutePath().normalize());

        try (CognitiveStore store = new CognitiveStore(database)) {
            // The owner is dead, so the file may be taken over.
            assertThat(store.writerLock()).isPresent();
            OwnershipToken recorded = WriterLock.readOwner(lockFile);
            assertThat(recorded).isNotNull();
            assertThat(recorded.pid()).isEqualTo(ProcessHandle.current().pid());
        }
    }

    @Test
    @DisplayName("a lock held by a live owner blocks the next writer")
    void aLockHeldByALiveOwnerBlocks() {
        Path database = directory.resolve("cognitive.sqlite");
        Path lockFile = WriterLock.lockFileFor(database);
        // A live process: this very JVM, with its real start instant.
        OwnershipToken live = OwnershipToken.current();
        WriterLock.writeOwner(lockFile, live, database.toAbsolutePath().normalize());

        assertThatThrownBy(() -> new CognitiveStore(database))
                .isInstanceOf(CognitiveWriterConflictException.class);

        CognitiveWriterConflictException refusal = catchRefusal(database);
        assertThat(refusal.owner()).isNotNull();
        assertThat(refusal.owner().pid()).isEqualTo(ProcessHandle.current().pid());
    }

    @Test
    @DisplayName("a second PROCESS is refused, and the refusal names the holder")
    void aSecondProcessIsRefused() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        Process helper = startHelper(database);
        long holder = awaitHolder(database);

        assertThatThrownBy(() -> new CognitiveStore(database))
                .isInstanceOf(CognitiveWriterConflictException.class)
                .hasMessageContaining(Long.toString(holder));

        CognitiveWriterConflictException refusal = catchRefusal(database);
        assertThat(refusal.owner()).isNotNull();
        assertThat(refusal.owner().pid()).isEqualTo(holder);
    }

    @Test
    @DisplayName("a killed writer leaves the lock file behind, and the next writer takes it over")
    void aKilledWriterLeavesTheLockFileBehind() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        Path lockFile = WriterLock.lockFileFor(database);
        Process helper = startHelper(database);
        long holder = awaitHolder(database);

        // The kill: the process is gone, and nothing of it runs to release the lock.
        helper.destroyForcibly();
        assertThat(helper.waitFor(WAIT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();

        // The lock file is LEFT BEHIND with the dead owner's record in it.
        assertThat(lockFile).exists();
        OwnershipToken dead = WriterLock.readOwner(lockFile);
        assertThat(dead).isNotNull();
        assertThat(dead.pid()).isEqualTo(holder);
        assertThat(dead.ownerAlive()).isFalse();

        // The next writer takes it over, because that owner is dead.
        try (CognitiveStore store = new CognitiveStore(database)) {
            assertThat(store.writerLock()).isPresent();
            OwnershipToken recorded = WriterLock.readOwner(lockFile);
            assertThat(recorded.pid()).isEqualTo(ProcessHandle.current().pid());
        }
    }

    @Test
    @DisplayName("a store that was opened without the guard says so")
    void aStoreThatWasOpenedWithoutTheGuardSaysSo() {
        Path database = directory.resolve("cognitive.sqlite");

        try (CognitiveStore store = CognitiveStore.withoutSingleWriterGuard(database,
                "the test opens the file without the guard")) {
            assertThat(store.writerLock()).isEmpty();
            try (CognitiveStore guarded = new CognitiveStore(database)) {
                assertThat(guarded.writerLock()).isPresent();
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Opens the store, expecting the refusal, and returns it. */
    private static CognitiveWriterConflictException catchRefusal(Path database) {
        try {
            new CognitiveStore(database);
        } catch (CognitiveWriterConflictException e) {
            return e;
        }
        throw new AssertionError("the store was opened although another writer holds it");
    }

    /** Starts the helper process that opens the store and holds it. */
    private Process startHelper(Path database) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                Holder.class.getName(),
                database.toString(),
                Long.toString(HOLD_MILLIS));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        helpers.add(process);
        return process;
    }

    /** Waits until the helper process holds the lock, and returns its pid. */
    private long awaitHolder(Path database) throws Exception {
        Path lockFile = WriterLock.lockFileFor(database);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
        while (System.nanoTime() < deadline) {
            OwnershipToken owner = WriterLock.readOwner(lockFile);
            if (owner != null && owner.pid() != ProcessHandle.current().pid()) {
                return owner.pid();
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
        throw new AssertionError("the helper process did not take the writer lock in time");
    }

    /**
     * The helper process: it opens the store, says that it holds the lock, and waits.
     *
     * <p>It is a real second process, because the refusal of a second process is the decision
     * that is being made.</p>
     */
    public static final class Holder {

        /** The entry point of the helper process. */
        public static void main(String[] args) throws Exception {
            Path database = Path.of(args[0]);
            long millis = Long.parseLong(args[1]);
            CognitiveStore store = new CognitiveStore(database);
            System.out.println(HELD_PREFIX + ProcessHandle.current().pid());
            System.out.flush();
            Thread.sleep(millis);
            store.close();
        }
    }
}
