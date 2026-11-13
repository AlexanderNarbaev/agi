package io.matrix.cognitive.distributed;

import io.matrix.bridge.TaskPublisher;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the coordinator's boundary is bounded.
 *
 * <p><b>What these tests hold.</b> The mandate asks for bounded work (§6.6), for a
 * deadline that is propagated and honoured (§6.5), and for the failure cases of §12
 * (a payload that is too large, a concurrency limit that is reached, a timeout, a
 * cancellation). A bound that is only written down is not a bound.</p>
 *
 * <p><b>What they also hold about honesty.</b> A timeout and a cancellation do not
 * delete what the peer said: the answer stays on record in the dispatch row, and what
 * disappears is chain A's judgement of it, because a task that timed out or was cancelled
 * has no verdict.</p>
 */
class CognitiveCoordinatorLimitsTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    /** The deadline the fixtures give a task. */
    private static final long DEADLINE_MILLIS = 5000L;

    @TempDir
    Path directory;

    /**
     * A monotonic clock the test moves by hand.
     *
     * <p>The deadline is a decision about how long chain A waits, so it is measured with
     * a monotonic source — and the source is injected so that the decision is
     * reproducible instead of a race with the machine's speed.</p>
     */
    private static final class MovableClock implements LongSupplier {
        private long nanos;

        @Override
        public long getAsLong() {
            return nanos;
        }

        /**
         * Moves the clock forward.
         *
         * @param millis how many milliseconds
         */
        void advance(long millis) {
            nanos += millis * 1_000_000L;
        }
    }

    /**
     * A transport that answers, and counts what it was asked to send.
     */
    private static final class CountingTransport implements CognitiveCoordinator.TaskTransport {
        private int calls;

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            calls++;
            return answer(request);
        }
    }

    /**
     * A transport that records the withdrawal it was asked to carry.
     */
    private static final class RecordingTransport implements CognitiveCoordinator.TaskTransport {

        private String cancelledRequest;
        private String cancellationReason;

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            return answer(request);
        }

        @Override
        public boolean cancel(String requestId, String reason, String sentAt) {
            this.cancelledRequest = requestId;
            this.cancellationReason = reason;
            return true;
        }
    }

    /**
     * A transport that waits until the test lets it go.
     */
    private static final class BlockingTransport implements CognitiveCoordinator.TaskTransport {
        private final CountDownLatch sent = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            sent.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return answer(request);
        }

        /**
         * Waits until the request is on its way.
         *
         * @throws InterruptedException when the wait is interrupted
         */
        void awaitTheSend() throws InterruptedException {
            assertTrue(sent.await(10, TimeUnit.SECONDS), "the request must have been sent");
        }

        /**
         * Lets the answer through.
         */
        void release() {
            release.countDown();
        }
    }

    /**
     * The answer a peer gives.
     *
     * @param request the request that was sent
     * @return the dispatch the peer produced
     */
    private static TaskPublisher.Dispatch answer(CognitiveTaskRequest request) {
        CognitiveTaskResponse response = new CognitiveTaskResponse(request.taskId(),
                request.requestId(), request.correlation(), request.payloadHash(),
                "chain-b-brain-runtime", request.requiredCapability(), "v1",
                CognitiveTaskResponse.Status.COMPLETED, "the lamp is lit", 0.9,
                CognitiveTaskResponse.ConfidenceBasis.DECLARED, List.of(), null, null,
                List.of(), List.of(), List.of(), List.of("BIR"),
                Provenance.of("chain-b", AT), "the capability ran", 5L, "trace-1", null, AT);
        CognitiveTaskReport report = new CognitiveTaskReport(BridgeProtocol.VERSION,
                request.taskId(), request.requestId(), "the lamp is lit", null,
                CognitiveTaskReport.Relation.INDEPENDENT, 0.9,
                CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of(),
                "chain-b-brain-runtime", request.requiredCapability(), "v1", List.of(),
                Provenance.of("chain-b", AT), AT);
        return new TaskPublisher.Dispatch(request.requestId(),
                BridgeDecision.Status.ACCEPTED, "the message satisfies the contract",
                Optional.of(response), Optional.of(report), true, true, Optional.empty());
    }

    /**
     * A transport whose work takes longer than the task's deadline.
     */
    private static final class SlowTransport implements CognitiveCoordinator.TaskTransport {
        private final MovableClock clock;
        private final long millis;

        SlowTransport(MovableClock clock, long millis) {
            this.clock = clock;
            this.millis = millis;
        }

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            clock.advance(millis);
            return answer(request);
        }
    }

    /**
     * A task with the given input.
     *
     * @param input the bounded input of the task
     * @return the task
     */
    private static CognitiveCoordinator.TaskSpec spec(String input) {
        return new CognitiveCoordinator.TaskSpec("tsk-1", "brain.cognitive-inference",
                input, "the lamp is lit", DEADLINE_MILLIS, DEADLINE_MILLIS, List.of());
    }

    /**
     * A coordinator over a fresh store.
     *
     * @param store     the store
     * @param transport the transport
     * @param clock     the monotonic clock
     * @param policy    the bounds
     * @return the coordinator
     */
    private static CognitiveCoordinator coordinator(CognitiveStore store,
                                                    CognitiveCoordinator.TaskTransport transport,
                                                    LongSupplier clock, DispatchPolicy policy) {
        return new CognitiveCoordinator(store, transport,
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a-epistemic-core", () -> AT, policy, clock);
    }

    @Test
    @DisplayName("a task whose input does not fit is refused before anything is sent")
    void aTaskThatDoesNotFitIsRefusedBeforeItIsSent() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CountingTransport transport = new CountingTransport();
            DispatchPolicy policy = new DispatchPolicy(16, 4, 3, DEADLINE_MILLIS, DEADLINE_MILLIS);
            CognitiveCoordinator coordinator = coordinator(store, transport,
                    new MovableClock(), policy);

            CognitiveCoordinator.Outcome outcome = coordinator.arrange(spec("this input is far too long"));

            assertEquals(DispatchIntent.State.FAILED, outcome.state(),
                    "a task that does not fit must not be dispatched");
            assertTrue(outcome.reason().contains("payload limit"),
                    "the reason must name the limit that refused it: " + outcome.reason());
            assertEquals(0, transport.calls, "nothing may be sent for a refused task");
            assertTrue(store.findDispatch("req-tsk-1").isEmpty(),
                    "a task that was never dispatched must leave no dispatch record");
        }
    }

    @Test
    @DisplayName("the concurrency limit refuses the work that exceeds it")
    void theConcurrencyLimitRefusesTheWorkThatExceedsIt() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            BlockingTransport transport = new BlockingTransport();
            DispatchPolicy policy = new DispatchPolicy(4096, 1, 3, DEADLINE_MILLIS, DEADLINE_MILLIS);
            CognitiveCoordinator coordinator = coordinator(store, transport,
                    new MovableClock(), policy);

            AtomicReference<CognitiveCoordinator.Outcome> first = new AtomicReference<>();
            Thread worker = new Thread(() -> first.set(coordinator.arrange(spec("short"))));
            worker.start();
            transport.awaitTheSend();

            CognitiveCoordinator.Outcome refused = coordinator.arrange(spec("short"));

            assertEquals(DispatchIntent.State.FAILED, refused.state(),
                    "the work that exceeds the concurrency limit must be refused");
            assertTrue(refused.reason().contains("concurrency limit"),
                    "the reason must name the limit that refused it: " + refused.reason());

            transport.release();
            worker.join(10_000L);
            assertEquals(DispatchIntent.State.ADJUDICATED, first.get().state(),
                    "the first dispatch was within the limit and must have been judged");
        }
    }

    @Test
    @DisplayName("an answer that arrives after the deadline is a timeout, and it stays on record")
    void anAnswerThatArrivesAfterTheDeadlineIsATimeout() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            MovableClock clock = new MovableClock();
            // The peer's work takes longer than the deadline the task named.
            CognitiveCoordinator coordinator = coordinator(store,
                    new SlowTransport(clock, DEADLINE_MILLIS + 1000L), clock,
                    DispatchPolicy.standard());

            CognitiveCoordinator.Outcome outcome = coordinator.arrange(spec("short"));

            assertEquals(DispatchIntent.State.TIMED_OUT, outcome.state(),
                    "an answer after the deadline is a timeout, not a judgement");
            assertTrue(outcome.verdict().isEmpty(),
                    "a task that timed out has no verdict");
            DispatchIntent intent = store.findDispatch("req-tsk-1").orElseThrow();
            assertEquals(DispatchIntent.State.TIMED_OUT, intent.state(),
                    "the dispatch row must show the timeout");
            assertNotNull(intent.remoteStatus(),
                    "what the peer said must stay on record: a timeout does not delete it");
        }
    }

    @Test
    @DisplayName("a cancelled task is not judged, and the peer's answer is still on record")
    void aCancelledTaskIsNotJudged() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            BlockingTransport transport = new BlockingTransport();
            CognitiveCoordinator coordinator = coordinator(store, transport, new MovableClock(),
                    DispatchPolicy.standard());

            AtomicReference<CognitiveCoordinator.Outcome> outcome = new AtomicReference<>();
            Thread worker = new Thread(() -> outcome.set(coordinator.arrange(spec("short"))));
            worker.start();
            transport.awaitTheSend();

            assertTrue(coordinator.cancel("req-tsk-1"),
                    "a dispatched task must be cancellable");

            transport.release();
            worker.join(10_000L);

            assertEquals(DispatchIntent.State.CANCELLED, outcome.get().state(),
                    "a cancelled task ends as cancelled");
            assertTrue(outcome.get().verdict().isEmpty(),
                    "a cancelled task has no verdict");
            DispatchIntent intent = store.findDispatch("req-tsk-1").orElseThrow();
            assertNotNull(intent.remoteStatus(),
                    "the peer's answer must stay on record even after a cancellation");
        }
    }

    @Test
    @DisplayName("the withdrawal travels to the peer, so the peer's work stops too")
    void theWithdrawalTravelsToThePeer() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            RecordingTransport transport = new RecordingTransport();
            CognitiveCoordinator coordinator = coordinator(store, transport, new MovableClock(),
                    DispatchPolicy.standard());

            coordinator.arrange(spec("short"));

            assertTrue(coordinator.cancel("req-tsk-1"),
                    "a dispatched task must be cancellable");
            assertEquals("req-tsk-1", transport.cancelledRequest,
                    "the peer must be told that the task is withdrawn");
            assertNotNull(transport.cancellationReason,
                    "the withdrawal must say why the task is withdrawn");
        }
    }

    @Test
    @DisplayName("a transport that cannot carry the withdrawal does not stop the local "
            + "cancellation")
    void aTransportThatCannotCarryTheWithdrawalDoesNotStopTheLocalCancellation()
            throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CountingTransport transport = new CountingTransport();
            CognitiveCoordinator coordinator = coordinator(store, transport, new MovableClock(),
                    DispatchPolicy.standard());

            coordinator.arrange(spec("short"));

            assertTrue(coordinator.cancel("req-tsk-1"),
                    "the local cancellation must happen even when the peer cannot be told");
            assertEquals(DispatchIntent.State.CANCELLED,
                    store.findDispatch("req-tsk-1").orElseThrow().state(),
                    "the dispatch row must show the cancellation");
        }
    }

    @Test
    @DisplayName("cancelling something that was never dispatched changes nothing")
    void cancellingSomethingThatWasNeverDispatchedChangesNothing() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = coordinator(store, new CountingTransport(),
                    new MovableClock(), DispatchPolicy.standard());

            assertFalse(coordinator.cancel("req-never-sent"),
                    "there is nothing to cancel");
            assertFalse(coordinator.cancel(" "),
                    "an empty request id names no task");
        }
    }

    @Test
    @DisplayName("the answer is written into the dispatch row")
    void theAnswerIsWrittenIntoTheDispatchRow() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = coordinator(store, new CountingTransport(),
                    new MovableClock(), DispatchPolicy.standard());

            coordinator.arrange(spec("short"));

            DispatchIntent intent = store.findDispatch("req-tsk-1").orElseThrow();
            assertNotNull(intent.responseJson(),
                    "the structured answer must be retrievable after the decision");
            assertEquals(DispatchIntent.State.ADJUDICATED, intent.state(),
                    "the judged task ends as adjudicated");
        }
    }

    @Test
    @DisplayName("the process keeps one coordinator, so its bounds are the process's bounds")
    void theProcessKeepsOneCoordinatorSoItsBoundsAreTheProcessesBounds() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            DistributedTaskResource resource = new DistributedTaskResource(store,
                    new TaskPublisher("http://127.0.0.1:1",
                            new io.matrix.contract.bridge.HmacBridgeSigner(
                                    "the-test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "chain-a-epistemic-core"),
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE));

            assertTrue(resource.coordinator() == resource.coordinator(),
                    "a coordinator per request would make the concurrency cap unreachable");
        }
    }

    @Test
    @DisplayName("the policy refuses bounds that make no sense")
    void thePolicyRefusesBoundsThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchPolicy(0, 4, 3, DEADLINE_MILLIS, DEADLINE_MILLIS),
                "a payload limit of zero bytes is not a limit");
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchPolicy(4096, 0, 3, DEADLINE_MILLIS, DEADLINE_MILLIS),
                "a concurrency limit of zero dispatches is not a limit");
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchPolicy(4096, 4, -1, DEADLINE_MILLIS, DEADLINE_MILLIS),
                "a negative retry budget is not a budget");
        assertEquals(CognitiveCoordinator.DEFAULT_MAX_PAYLOAD_BYTES,
                DispatchPolicy.standard().maxPayloadBytes(),
                "the standard policy must use the contract's own payload limit");
    }
}
