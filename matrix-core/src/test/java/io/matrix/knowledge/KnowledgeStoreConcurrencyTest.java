package io.matrix.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the epistemic store's write path under concurrency.
 *
 * <p><b>What this test is about.</b> The store owns one SQLite connection. Every write
 * opens a transaction on it — {@code setAutoCommit(false)}, the writes, {@code commit()},
 * and {@code setAutoCommit(true)} in the {@code finally}. That sequence is only correct
 * if no other thread touches the same connection while the transaction is open: another
 * writer's {@code setAutoCommit(true)} puts the connection back into auto-commit mode,
 * and the first writer's {@code commit()} then fails with "database in auto-commit mode"
 * — the transaction is lost, and the report that was being written vanishes with it.</p>
 *
 * <p><b>Why it is here.</b> The distributed cognitive path writes a remote report into
 * this store while the cognitive kernel writes into the same store. The defect was
 * pre-existing, but the distributed path is what exposes it: one report per remote task,
 * several tasks at a time, and a report that is lost is an epistemic fact that quietly
 * does not exist.</p>
 *
 * <p><b>What it proves.</b> That concurrent writes do not lose each other, and that the
 * database holds every claim afterwards — the state, not merely the absence of an
 * exception.</p>
 */
class KnowledgeStoreConcurrencyTest {

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    /** The workspace the writers share. */
    private static final String WORKSPACE = "bridged";

    @TempDir
    Path directory;

    @Test
    @DisplayName("four writers, one store, one workspace: every claim is on record")
    void fourWritersOneStoreOneWorkspace() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        AtomicInteger failures = new AtomicInteger();
        List<String> messages = new ArrayList<>();

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int index = i;
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        store.applyChanges(WORKSPACE, changesOf(index));
                    } catch (Throwable e) {
                        failures.incrementAndGet();
                        synchronized (messages) {
                            messages.add(e.getClass().getName() + ": " + e.getMessage()
                                    + (e.getCause() == null ? "" : " | cause: "
                                    + e.getCause().getClass().getName() + ": "
                                    + e.getCause().getMessage()));
                        }
                    }
                }, "knowledge-writer-" + index);
                threads.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(0, failures.get(),
                    "the concurrent writes must not fail: " + messages);
            List<Claim> claims = store.claims(WORKSPACE);
            assertEquals(4, claims.size(),
                    "every claim must be on record: no writer may lose another writer's work");
        }
    }

    @Test
    @DisplayName("the writes survive a restart: the state, not the exception")
    void theWritesSurviveARestart() throws Exception {
        Path database = directory.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int index = i;
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        store.applyChanges(WORKSPACE, changesOf(index));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "knowledge-writer-" + index);
                threads.add(thread);
                thread.start();
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
        }

        try (KnowledgeStore reopened = new KnowledgeStore(database)) {
            assertEquals(4, reopened.claims(WORKSPACE).size(),
                    "the committed work must be on record after the restart");
            assertTrue(reopened.claims(WORKSPACE).stream()
                    .allMatch(claim -> claim.workspaceId().equals(WORKSPACE)),
                    "the claims must belong to the workspace they were written for");
        }
    }

    /**
     * The changes of one writer.
     *
     * @param index the writer's number
     * @return the changes
     */
    private static KnowledgeChanges changesOf(int index) {
        Source source = new Source("src-" + index, WORKSPACE, SourceKind.SECONDARY_REPORT,
                "the remote cognitive report " + index, "locator-" + index, 20, 80,
                "the proposition " + index, NOW, "hash-" + index, null, null, "text");
        Claim claim = new Claim("clm-" + index, WORKSPACE, "con-" + index,
                "the proposition " + index, null, null, ClaimStatus.CANDIDATE, 0.0, 1,
                "src-" + index, "reported by the peer", "not validated", ClaimWarrant.NONE,
                null, NOW, NOW);
        Evidence evidence = new Evidence("evd-" + index, WORKSPACE, "clm-" + index,
                "src-" + index, EvidenceRelation.SUPPORTS, "the proposition " + index, 10, NOW);
        return new KnowledgeChanges(List.of(source), List.of(claim), List.of(evidence),
                List.of(), List.of());
    }
}
