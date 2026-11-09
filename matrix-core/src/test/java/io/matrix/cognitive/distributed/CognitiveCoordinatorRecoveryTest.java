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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the recovery path of a delivery whose answer was lost.
 *
 * <p><b>What these tests hold.</b> The intent is durable before the request is sent, the
 * request itself is durable with the intent, and a pass over the unfinished dispatches
 * can send that request again — with the same request id, so the peer's durable inbox
 * recognises it and the logical effect still happens once.</p>
 *
 * <p><b>What they do not claim.</b> There is no distributed exactly-once guarantee here:
 * the delivery is at-least-once and the effect is effectively-once because the peer
 * deduplicates the request id.</p>
 */
class CognitiveCoordinatorRecoveryTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    @TempDir
    Path directory;

    /**
     * A transport that dies on the first delivery and answers on the second.
     *
     * <p>That is the crash the recovery path exists for: the request was committed and
     * sent, and the process died before the answer was recorded.</p>
     */
    private static final class CrashingTransport implements CognitiveCoordinator.TaskTransport {
        private final List<CognitiveTaskRequest> seen = new ArrayList<>();
        private boolean died;

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            seen.add(request);
            if (!died) {
                died = true;
                throw new IllegalStateException("the process died while the request was in flight");
            }
            return answer(request);
        }
    }

    /**
     * A transport whose peer says the task is still running, and then answers it.
     */
    private static final class RunningTransport implements CognitiveCoordinator.TaskTransport {

        private final List<CognitiveTaskRequest> seen = new ArrayList<>();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            seen.add(request);
            if (seen.size() == 1) {
                // The first attempt dies with the process, before the peer answered.
                throw new IllegalStateException(
                        "the process died while the request was in flight");
            }
            if (seen.size() == 2) {
                return running(request);
            }
            return answer(request);
        }
    }

    /**
     * The answer a peer gives when the task is still running.
     *
     * @param request the request that was sent
     * @return the dispatch the peer produced
     */
    private static TaskPublisher.Dispatch running(CognitiveTaskRequest request) {
        CognitiveTaskResponse response = new CognitiveTaskResponse(request.taskId(),
                request.requestId(), request.correlation(), request.payloadHash(),
                "chain-b-brain-runtime", request.requiredCapability(), "v1",
                CognitiveTaskResponse.Status.IN_PROGRESS, null, null,
                CognitiveTaskResponse.ConfidenceBasis.UNKNOWN, List.of(), null, null,
                List.of(), List.of(), List.of(), List.of("BIR"),
                Provenance.of("chain-b", AT), "the task is still running", null, "trace-1",
                "the task is still running", AT);
        return new TaskPublisher.Dispatch(request.requestId(),
                BridgeDecision.Status.ACCEPTED, "the message satisfies the contract",
                Optional.of(response), Optional.empty(), true, false, Optional.empty());
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
     * A task.
     *
     * @return the task
     */
    private static CognitiveCoordinator.TaskSpec spec() {
        return new CognitiveCoordinator.TaskSpec("tsk-1", "brain.cognitive-inference",
                "is the lamp lit", "the lamp is lit", 5000L, 5000L, List.of());
    }

    /**
     * A coordinator over a store.
     *
     * @param store     the store
     * @param transport the transport
     * @return the coordinator
     */
    private static CognitiveCoordinator coordinator(CognitiveStore store,
                                                    CognitiveCoordinator.TaskTransport transport) {
        return new CognitiveCoordinator(store, transport,
                new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                WORKSPACE, "chain-a-epistemic-core", () -> AT);
    }

    @Test
    @DisplayName("the intent keeps the request, so it can be sent again")
    void theIntentKeepsTheRequestSoItCanBeSentAgain() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            try {
                coordinator(store, new CrashingTransport()).arrange(spec());
            } catch (IllegalStateException expected) {
                // the crash the recovery path exists for
            }

            assertNotNull(store.requestJson("req-tsk-1").orElse(null),
                    "the request must be on record with the intent");
        }
    }

    @Test
    @DisplayName("after a crash the same request is sent again, with the same identity")
    void afterACrashTheSameRequestIsSentAgainWithTheSameIdentity() {
        Path database = directory.resolve("cognitive.sqlite");
        CrashingTransport transport = new CrashingTransport();

        try (CognitiveStore store = new CognitiveStore(database)) {
            try {
                coordinator(store, transport).arrange(spec());
            } catch (IllegalStateException expected) {
                // the crash the recovery path exists for
            }
        }

        // The process restarts: a new store over the same file, a new coordinator.
        try (CognitiveStore restarted = new CognitiveStore(database)) {
            List<DispatchIntent> unfinished = restarted.unfinishedDispatches(WORKSPACE);
            assertEquals(1, unfinished.size(),
                    "the dispatch of the crashed delivery must be on record as unfinished");
            assertEquals(DispatchIntent.State.DISPATCH_PENDING, unfinished.get(0).state(),
                    "a dispatch whose answer was lost is still pending");

            Optional<CognitiveCoordinator.Outcome> outcome = coordinator(restarted, transport)
                    .replayByRequestId("req-tsk-1");

            assertTrue(outcome.isPresent(), "the unfinished dispatch must be finishable");
            assertEquals(2, transport.seen.size(), "the request must have been sent twice");
            assertEquals(transport.seen.get(0).requestId(), transport.seen.get(1).requestId(),
                    "the replay must carry the same request id");
            assertEquals(transport.seen.get(0).payloadHash(), transport.seen.get(1).payloadHash(),
                    "the replay must carry the same payload hash, so the peer recognises it");
            assertEquals(DispatchIntent.State.ADJUDICATED, outcome.get().state(),
                    "the replayed delivery must reach a verdict");
        }
    }

    @Test
    @DisplayName("the reconciliation pass finishes what the crash left unfinished")
    void theReconciliationPassFinishesWhatTheCrashLeftUnfinished() {
        Path database = directory.resolve("cognitive.sqlite");
        CrashingTransport transport = new CrashingTransport();

        try (CognitiveStore store = new CognitiveStore(database)) {
            try {
                coordinator(store, transport).arrange(spec());
            } catch (IllegalStateException expected) {
                // the crash the recovery path exists for
            }
        }

        try (CognitiveStore restarted = new CognitiveStore(database)) {
            DispatchReconciler.Summary summary = new DispatchReconciler(restarted,
                    coordinator(restarted, transport), WORKSPACE).reconcile();

            assertEquals(1, summary.examined(), "the pass must find the unfinished dispatch");
            assertEquals(1, summary.replayed(), "the pass must send it again");
            assertEquals(1, summary.finished(), "the pass must finish it");
            assertTrue(summary.reasons().isEmpty(),
                    "nothing should be left unfinished: " + summary.reasons());
            assertTrue(restarted.unfinishedDispatches(WORKSPACE).isEmpty(),
                    "no dispatch may be left unfinished after the pass");
        }
    }

    @Test
    @DisplayName("a replay of a task the peer is still running is not judged")
    void aReplayOfATaskThePeerIsStillRunningIsNotJudged() throws Exception {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            RunningTransport transport = new RunningTransport();
            CognitiveCoordinator coordinator = coordinator(store, transport);

            // The first attempt crashes the process before the peer answers.
            assertThrows(RuntimeException.class, () -> coordinator.arrange(spec()));

            // The pass after the restart sends the request again; the peer says the task
            // is still running. That is not an answer to judge: the task stays in flight.
            Optional<CognitiveCoordinator.Outcome> outcome =
                    coordinator.replayByRequestId("req-tsk-1");

            assertTrue(outcome.isPresent());
            assertEquals(DispatchIntent.State.DISPATCHED, outcome.get().state(),
                    "a task that is still running is not closed");
            assertTrue(outcome.get().verdict().isEmpty(),
                    "a task that is still running has no verdict");
            DispatchIntent intent = store.findDispatch("req-tsk-1").orElseThrow();
            assertEquals(DispatchIntent.State.DISPATCHED, intent.state());
            assertNull(intent.disposition(),
                    "nothing may be adjudicated while the peer is still working");
        }
    }

    @Test
    @DisplayName("a dispatch with no request to send again is reported, not guessed")
    void aDispatchWithNoRequestToSendAgainIsReportedNotGuessed() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = coordinator(store, new CrashingTransport());
            assertTrue(coordinator.replayByRequestId("req-never-sent").isEmpty(),
                    "there is nothing to replay");

            DispatchReconciler.Summary summary = new DispatchReconciler(store, coordinator,
                    WORKSPACE).reconcile();
            assertEquals(0, summary.examined(), "an unknown dispatch is not an unfinished one");
        }
    }
}
