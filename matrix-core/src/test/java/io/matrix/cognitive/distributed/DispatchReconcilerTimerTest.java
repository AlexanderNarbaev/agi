package io.matrix.cognitive.distributed;

import io.matrix.bridge.TaskPublisher;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import io.matrix.contract.bridge.HmacBridgeSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — the reconciliation pass on a timer.
 *
 * <p><b>What these tests hold.</b> The pass runs while the process is up, not only when
 * it starts: a dispatch whose answer was lost is finished by the timer, and a dispatch
 * that is still in flight is left alone. A dispatch that will not be sent again — its
 * retry budget is spent — is closed, so it leaves the queue instead of being picked up
 * for ever.</p>
 *
 * <p><b>What they do not claim.</b> There is no distributed exactly-once guarantee here:
 * the delivery is at-least-once and the effect is effectively-once because the peer
 * deduplicates the request id.</p>
 */
class DispatchReconcilerTimerTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The shared secret of the two sides. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    @TempDir
    Path directory;

    /**
     * A transport that dies on the first delivery and answers on the second.
     *
     * <p>That is the lost answer the timer exists for: the request was committed and
     * sent, and the process stayed up while the answer never arrived.</p>
     */
    private static final class CrashingTransport implements CognitiveCoordinator.TaskTransport {
        private final List<CognitiveTaskRequest> seen = new ArrayList<>();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            seen.add(request);
            if (seen.size() == 1) {
                throw new IllegalStateException("the answer was lost while the request was in flight");
            }
            return answer(request);
        }
    }

    /**
     * A transport whose peer never answers, and that holds the request while it waits.
     */
    private static final class SilentTransport implements CognitiveCoordinator.TaskTransport {
        private final CountDownLatch arrived = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final List<CognitiveTaskRequest> seen = new ArrayList<>();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            seen.add(request);
            arrived.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return answer(request);
        }
    }

    /**
     * A transport whose process dies on every delivery.
     *
     * <p>The row of such a dispatch stays unfinished: the request was committed and sent,
     * and nothing came back. That is the state the reconciliation pass exists for.</p>
     */
    private static final class DeadTransport implements CognitiveCoordinator.TaskTransport {
        private final List<CognitiveTaskRequest> seen = new ArrayList<>();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            seen.add(request);
            throw new IllegalStateException("the process died while the request was in flight");
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
                Optional.of(response), Optional.of(report), true, true,
                Optional.of(peerReport(request)));
    }

    /**
     * The peer's signed report, as it arrives at chain A.
     *
     * @param request the request the report answers
     * @return the envelope
     */
    private static BridgeEnvelope peerReport(CognitiveTaskRequest request) {
        CognitiveTaskReport report = new CognitiveTaskReport(BridgeProtocol.VERSION,
                request.taskId(), request.requestId(), "the lamp is lit", null,
                CognitiveTaskReport.Relation.INDEPENDENT, 0.9,
                CognitiveTaskReport.ConfidenceBasis.DECLARED, List.of(),
                "chain-b", request.requiredCapability(), "v1", List.of("the lamp is dark"),
                Provenance.of("chain-b", AT), AT);
        BridgeEnvelope envelope = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                request.requestId(), AT, "chain-b", BridgeCapability.INFERENCE_RESPONSE,
                new BridgePayloadCodec().encode(BridgePayload.ofTaskReport(report,
                        Provenance.of("chain-b", AT))), null);
        return envelope.withSignature(envelope.sign(new HmacBridgeSigner(SECRET)));
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

    /**
     * A coordinator whose retry budget is already spent.
     *
     * @param store     the store
     * @param transport the transport
     * @return the coordinator
     */
    private static CognitiveCoordinator coordinatorWithoutBudget(CognitiveStore store,
                                                    CognitiveCoordinator.TaskTransport transport) {
        return coordinator(store, transport).withPolicy(new DispatchPolicy(
                CognitiveCoordinator.DEFAULT_MAX_PAYLOAD_BYTES,
                CognitiveCoordinator.DEFAULT_MAX_CONCURRENT_DISPATCHES, 0,
                CognitiveCoordinator.DEFAULT_BUDGET_MILLIS,
                CognitiveCoordinator.DEFAULT_DEADLINE_MILLIS));
    }

    @Test
    @DisplayName("the timer finishes a dispatch whose answer was lost")
    void theTimerFinishesADispatchWhoseAnswerWasLost() {
        Path database = directory.resolve("cognitive.sqlite");
        CrashingTransport transport = new CrashingTransport();
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = coordinator(store, transport);
            try {
                coordinator.arrange(spec());
            } catch (IllegalStateException expected) {
                // the lost answer the timer exists for
            }

            DispatchReconcilerTimer timer = new DispatchReconcilerTimer(
                    new DispatchReconciler(store, coordinator, WORKSPACE, () -> AT));
            try {
                timer.runOnce();
            } finally {
                timer.close();
            }

            DispatchIntent dispatch = store.findDispatch("req-tsk-1").orElseThrow();
            assertEquals(DispatchIntent.State.ADJUDICATED, dispatch.state(),
                    "the timer must finish the dispatch whose answer was lost");
            assertEquals(2, transport.seen.size(),
                    "the request must be sent again, and only once more");
            assertEquals("req-tsk-1", transport.seen.get(1).requestId(),
                    "the same request must be sent again, with the same identity");
        }
    }

    @Test
    @DisplayName("the timer leaves a dispatch that is in flight alone")
    void theTimerLeavesADispatchThatIsInFlightAlone() throws InterruptedException {
        Path database = directory.resolve("cognitive.sqlite");
        SilentTransport transport = new SilentTransport();
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = coordinator(store, transport);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    coordinator.arrange(spec());
                } catch (Throwable e) {
                    failure.set(e);
                }
            });
            worker.start();
            assertTrue(transport.arrived.await(10, TimeUnit.SECONDS),
                    "the request must have reached the peer");

            DispatchReconcilerTimer timer = new DispatchReconcilerTimer(
                    new DispatchReconciler(store, coordinator, WORKSPACE, () -> AT));
            try {
                timer.runOnce();
            } finally {
                timer.close();
            }

            assertEquals(1, transport.seen.size(),
                    "a request that is still in flight must not be sent again");
            transport.release.countDown();
            worker.join(10_000);
            assertTrue(failure.get() == null, "the dispatch must have finished normally");
        }
    }

    @Test
    @DisplayName("a dispatch whose budget is spent is finished, and is not sent again")
    void aDispatchWhoseBudgetIsSpentIsFinishedAndIsNotSentAgain() {
        Path database = directory.resolve("cognitive.sqlite");
        DeadTransport transport = new DeadTransport();
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = coordinatorWithoutBudget(store, transport);
            try {
                coordinator.arrange(spec());
            } catch (IllegalStateException expected) {
                // the process died while the request was in flight
            }
            assertEquals(DispatchIntent.State.DISPATCH_PENDING,
                    store.findDispatch("req-tsk-1").orElseThrow().state(),
                    "the dispatch of a lost answer must still be unfinished");

            DispatchReconciler reconciler = new DispatchReconciler(store, coordinator, WORKSPACE,
                    () -> AT);
            DispatchReconcilerTimer timer = new DispatchReconcilerTimer(reconciler);
            try {
                timer.runOnce();
                DispatchIntent dispatch = store.findDispatch("req-tsk-1").orElseThrow();
                assertEquals(DispatchIntent.State.FAILED, dispatch.state(),
                        "a dispatch that will not be sent again must be closed");
                assertTrue(store.unfinishedDispatches(WORKSPACE).isEmpty(),
                        "a closed dispatch must leave the unfinished queue");

                DispatchReconciler.Summary again = reconciler.reconcileStale();
                assertEquals(0, again.examined(),
                        "a closed dispatch must not be picked up again");
            } finally {
                timer.close();
            }
        }
    }
}
