package io.matrix.cognitive.distributed;

import io.matrix.bridge.TaskDispatchException;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the retry budget, and the fact that it is spent durably.
 *
 * <p><b>What these tests hold.</b> A peer that does not answer is dialled again, but
 * only within a budget; the count of the attempts lives in the dispatch's own row, so a
 * restart does not hand the task a fresh one; and a task that spent its budget is a
 * finished, traceable failure rather than a request that is dialled for ever.</p>
 *
 * <p><b>What they do not claim.</b> Nothing here is a distributed exactly-once promise:
 * every attempt carries the same request id, so the peer's durable inbox recognises the
 * later attempt and the logical effect still happens once.</p>
 */
class CognitiveCoordinatorRetryTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    /** The budget the fixtures spend. */
    private static final int MAX_RETRIES = 2;

    @TempDir
    Path directory;

    /** A transport that never gets an answer. */
    private static final class SilentTransport implements CognitiveCoordinator.TaskTransport {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            calls.incrementAndGet();
            return TaskPublisher.Dispatch.empty(request.requestId(),
                    BridgeDecision.Status.ACCEPTED, "the message satisfies the contract");
        }
    }

    /** A transport whose first delivery fails and whose second one answers. */
    private static final class FailingThenAnsweringTransport
            implements CognitiveCoordinator.TaskTransport {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            if (calls.incrementAndGet() == 1) {
                throw new TaskDispatchException("the peer did not answer", null);
            }
            return answer(request);
        }
    }

    /** The answer a peer gives. */
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

    /** A task. */
    private static CognitiveCoordinator.TaskSpec spec() {
        return new CognitiveCoordinator.TaskSpec("tsk-1", "brain.cognitive-inference",
                "is the lamp lit", "the lamp is lit", 5000L, 5000L, List.of());
    }

    /** A coordinator with a bounded budget. */
    private static CognitiveCoordinator coordinator(CognitiveStore store,
                                                    CognitiveCoordinator.TaskTransport transport) {
        return new CognitiveCoordinator(store, transport,
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a-epistemic-core", () -> AT)
                .withPolicy(new DispatchPolicy(4096, 4, MAX_RETRIES, 5000L, 5000L));
    }

    @Test
    @DisplayName("a peer that does not answer is dialled within the budget, and no further")
    void aPeerThatDoesNotAnswerIsDialledWithinTheBudgetAndNoFurther() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            SilentTransport transport = new SilentTransport();

            CognitiveCoordinator.Outcome outcome = coordinator(store, transport).arrange(spec());

            assertEquals(MAX_RETRIES + 1, transport.calls.get(),
                    "the task must be dialled once and then retried within the budget");
            assertEquals(MAX_RETRIES + 1, store.attempts("req-tsk-1"),
                    "every attempt must be counted durably");
            assertEquals(DispatchIntent.State.FAILED, outcome.state());
            assertTrue(outcome.reason().contains("retry budget"),
                    "the failure must say that the budget is spent: " + outcome.reason());
        }
    }

    @Test
    @DisplayName("a delivery that fails is retried, and the later attempt answers")
    void aDeliveryThatFailsIsRetriedAndTheLaterAttemptAnswers() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            FailingThenAnsweringTransport transport = new FailingThenAnsweringTransport();

            CognitiveCoordinator.Outcome outcome = coordinator(store, transport).arrange(spec());

            assertEquals(2, transport.calls.get(), "the failed delivery must be retried");
            assertEquals(DispatchIntent.State.ADJUDICATED, outcome.state(),
                    "the answered attempt must reach a verdict");
            assertEquals(1, store.attempts("req-tsk-1"),
                    "the budget counts the deliveries that produced no answer: the failed "
                            + "one spent it, the answered one did not");
        }
    }

    @Test
    @DisplayName("the budget is durable: a restart does not buy a new one")
    void theBudgetIsDurableARestartDoesNotBuyANewOne() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            coordinator(store, new SilentTransport()).arrange(spec());
        }

        // The process restarts: a new store over the same file, a new coordinator.
        try (CognitiveStore restarted = new CognitiveStore(database)) {
            SilentTransport transport = new SilentTransport();

            Optional<CognitiveCoordinator.Outcome> outcome = coordinator(restarted, transport)
                    .replayByRequestId("req-tsk-1");

            assertTrue(outcome.isPresent());
            assertEquals(0, transport.calls.get(),
                    "a task that spent its budget must not be dialled again");
            assertTrue(outcome.get().reason().contains("retry budget"),
                    "the refusal must say that the budget is spent: " + outcome.get().reason());
            assertEquals(MAX_RETRIES + 1, restarted.attempts("req-tsk-1"),
                    "the attempt count must not change when the request is not sent");
        }
    }

    @Test
    @DisplayName("the replay is measured as a path of its own")
    void theReplayIsMeasuredAsAPathOfItsOwn() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            FailingThenAnsweringTransport transport = new FailingThenAnsweringTransport();
            CognitiveCoordinator coordinator = coordinator(store, transport);

            coordinator.arrange(spec());

            Optional<CognitiveCoordinator.Outcome> replay =
                    coordinator.replayByRequestId("req-tsk-1");

            assertTrue(replay.isPresent());
            Map<String, Long> timings = replay.get().timingsNanos();
            assertTrue(timings.containsKey("replay_dispatch_nanos"),
                    "the replay must measure the crossing it makes");
            assertTrue(timings.containsKey("replay_total_nanos"),
                    "the replay must measure itself as a whole");
            assertFalse(timings.containsKey("dispatch_nanos"),
                    "the replay must not be measured with the first path's keys: the two "
                            + "costs are different numbers");
        }
    }

    @Test
    @DisplayName("a task that spent its budget is finished, not replayed by the reconciliation")
    void aTaskThatSpentItsBudgetIsFinishedNotReplayedByTheReconciliation() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            coordinator(store, new SilentTransport()).arrange(spec());
        }

        try (CognitiveStore restarted = new CognitiveStore(database)) {
            SilentTransport transport = new SilentTransport();
            DispatchReconciler.Summary summary = new DispatchReconciler(restarted,
                    coordinator(restarted, transport), WORKSPACE).reconcile();

            assertEquals(0, summary.examined(),
                    "a dispatch that failed for its own reasons is not unfinished work");
            assertEquals(0, transport.calls.get());
            assertTrue(restarted.unfinishedDispatches(WORKSPACE).isEmpty());
        }
    }
}
