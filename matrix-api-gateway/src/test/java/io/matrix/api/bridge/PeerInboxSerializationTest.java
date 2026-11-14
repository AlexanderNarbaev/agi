package io.matrix.api.bridge;

import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — chain B's inbox holds one connection, and one connection is not thread-safe.
 *
 * <p><b>What this test holds.</b> The peer serves concurrent deliveries, and the durable
 * inbox keeps ONE JDBC connection. Two statements on one connection at the same time may
 * interleave: the check that a request id is unknown and the write that records it are
 * two statements, and without serialization both may pass the check and both may write.
 * The observable consequence is not a slow request but a wrong one — a second row for an
 * identity that must hold one row, or a reader that sees a half-written row.</p>
 *
 * <p><b>Why the assertions look at the database and not at the absence of an exception.</b>
 * A lost update and a duplicated row are both silent when nobody looks: the store answers
 * every call. What holds the rule is the state of the inbox afterwards — one row per
 * request id, one acceptance per request id, and every row in a state its own history
 * explains.</p>
 *
 * <p><b>What it proves.</b> Three cases. Concurrent deliveries of the same request id
 * produce exactly one acceptance and one row. Concurrent deliveries of different request
 * ids, mixed with reads of the inbox, produce every row and no failure. A row that is
 * completed while its stages are measured keeps both facts.</p>
 */
class PeerInboxSerializationTest {

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    /** How many threads deliver at the same time. */
    private static final int THREADS = 8;

    @TempDir
    Path directory;

    /**
     * Concurrent deliveries of one request id produce one acceptance and one row.
     */
    @Test
    @DisplayName("concurrent deliveries of one request id are admitted once")
    void concurrentDeliveriesOfOneRequestAreAdmittedOnce() throws Exception {
        try (RemoteTaskStore store = new RemoteTaskStore(directory.resolve("tasks.sqlite"))) {
            List<RemoteTaskStore.Decision> decisions = Collections.synchronizedList(new ArrayList<>());
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            CognitiveTaskRequest request = request("req-one", "the same task");
            CountDownLatch start = new CountDownLatch(1);

            List<Thread> workers = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                Thread worker = new Thread(() -> {
                    try {
                        start.await();
                        decisions.add(store.admit(request, "digest-" + request.payloadHash(), NOW)
                                .decision());
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join(30_000L);
            }

            assertThat(failures).as("no delivery may fail on a shared connection").isEmpty();
            assertThat(decisions).hasSize(THREADS);
            assertThat(decisions.stream()
                    .filter(decision -> decision == RemoteTaskStore.Decision.ACCEPTED).count())
                    .as("one request id is admitted once")
                    .isEqualTo(1L);
            assertThat(decisions.stream()
                    .filter(decision -> decision == RemoteTaskStore.Decision.DUPLICATE_IN_PROGRESS)
                    .count())
                    .as("every later delivery of the same id is a duplicate in progress")
                    .isEqualTo((long) THREADS - 1L);
            assertThat(store.size())
                    .as("one request id holds one row")
                    .isEqualTo(1);
        }
    }

    /**
     * Concurrent deliveries of different request ids, mixed with reads, all land.
     */
    @Test
    @DisplayName("concurrent deliveries of different requests and reads all land")
    void concurrentDeliveriesAndReadsAllLand() throws Exception {
        try (RemoteTaskStore store = new RemoteTaskStore(directory.resolve("tasks.sqlite"))) {
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger reads = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);

            List<Thread> workers = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                String requestId = "req-" + index;
                Thread writer = new Thread(() -> {
                    try {
                        start.await();
                        store.admit(request(requestId, "task " + requestId),
                                "digest-" + requestId, NOW);
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
                Thread reader = new Thread(() -> {
                    try {
                        start.await();
                        store.find(requestId);
                        store.unfinished();
                        store.size();
                        reads.incrementAndGet();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                });
                workers.add(writer);
                workers.add(reader);
                writer.start();
                reader.start();
            }
            start.countDown();
            for (Thread worker : workers) {
                worker.join(30_000L);
            }

            assertThat(failures).as("no operation may fail on a shared connection").isEmpty();
            assertThat(reads.get()).isEqualTo(THREADS);
            assertThat(store.size()).as("every request holds its own row").isEqualTo(THREADS);
            for (int index = 0; index < THREADS; index++) {
                RemoteTaskStore.Row row = store.find("req-" + index).orElseThrow();
                assertThat(row.state()).isEqualTo(RemoteTaskStore.State.ACCEPTED);
                assertThat(row.payloadDigest()).isEqualTo("digest-req-" + index);
            }
        }
    }

    /**
     * A row that is completed while its stages are measured keeps both facts.
     */
    @Test
    @DisplayName("a row completed while its stages are measured keeps both")
    void aRowKeepsItsResultAndItsMeasurement() throws Exception {
        try (RemoteTaskStore store = new RemoteTaskStore(directory.resolve("tasks.sqlite"))) {
            store.admit(request("req-both", "the measured task"), "digest-both", NOW);
            List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch start = new CountDownLatch(1);

            Thread completer = new Thread(() -> {
                try {
                    start.await();
                    store.markCompleted("req-both", "{\"answer\":\"yes\"}", "trace-1", true, NOW);
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            Thread measurer = new Thread(() -> {
                try {
                    start.await();
                    store.recordStages("req-both", "{\"inboxWrite\":1}");
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            Thread reader = new Thread(() -> {
                try {
                    start.await();
                    store.find("req-both");
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
            completer.start();
            measurer.start();
            reader.start();
            start.countDown();
            completer.join(30_000L);
            measurer.join(30_000L);
            reader.join(30_000L);

            assertThat(failures).as("no operation may fail on a shared connection").isEmpty();
            RemoteTaskStore.Row row = store.find("req-both").orElseThrow();
            assertThat(row.state()).isEqualTo(RemoteTaskStore.State.COMPLETED);
            assertThat(row.resultJson()).isEqualTo("{\"answer\":\"yes\"}");
            assertThat(row.stageTimingsJson()).isEqualTo("{\"inboxWrite\":1}");
            assertThat(row.effectCommitted()).isTrue();
        }
    }

    /**
     * A request for the bounded cognitive inference.
     *
     * @param requestId the identity of the request
     * @param input     the bounded input of the task
     * @return the request
     */
    private static CognitiveTaskRequest request(String requestId, String input) {
        return new CognitiveTaskRequest(
                BridgeProtocol.VERSION,
                "tsk-" + requestId,
                requestId,
                new Correlation("corr-" + requestId, "ep-parent", "ep-" + requestId, requestId),
                "ep-parent",
                "chain-a-epistemic-core",
                CognitiveCapability.COGNITIVE_INFERENCE,
                CognitiveCapability.COGNITIVE_INFERENCE_VERSION,
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE,
                input,
                Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId + "-" + input,
                null);
    }
}
