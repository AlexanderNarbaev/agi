package io.matrix.cognitive.distributed;

import io.matrix.bridge.TaskPublisher;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import io.matrix.contract.Correlation;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W53 — one task, one coordinator, one durable trace.
 *
 * <p><b>What these tests hold.</b> The coordinator writes its intent down before it
 * sends anything, it evaluates what came back against chain A's own evidence, and it
 * records the verdict where a later reader can find it. The peer's answer is never
 * promoted by the coordinator; the verdict is the only thing the coordinator decides, and
 * it is the assessor's verdict, not the peer's.</p>
 */
class CognitiveCoordinatorTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-09T12:00:00Z";

    /** The workspace of the fixtures. */
    private static final String WORKSPACE = "bridged";

    @TempDir
    Path directory;

    /** A transport that answers with a completed response and a report. */
    private static final class AnsweringTransport implements CognitiveCoordinator.TaskTransport {

        private final CognitiveStore store;
        private boolean sawTheIntent;

        AnsweringTransport(CognitiveStore store) {
            this.store = store;
        }

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            // The intent must already be durable when the transport is asked to send.
            sawTheIntent = store.findDispatch(request.requestId()).isPresent();
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
                    Optional.empty());
        }
    }

    /**
     * A transport whose peer says the task is still running before it answers.
     */
    private static final class ProgressingTransport implements CognitiveCoordinator.TaskTransport {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            if (calls.incrementAndGet() == 1) {
                CognitiveTaskResponse running = new CognitiveTaskResponse(request.taskId(),
                        request.requestId(), request.correlation(), request.payloadHash(),
                        "chain-b-brain-runtime", request.requiredCapability(), "v1",
                        CognitiveTaskResponse.Status.IN_PROGRESS, null, null,
                        CognitiveTaskResponse.ConfidenceBasis.UNKNOWN, List.of(), null, null,
                        List.of(), List.of(), List.of(), List.of("BIR"),
                        Provenance.of("chain-b", AT), "the task is still running", null,
                        "trace-1", "the task is still running", AT);
                return new TaskPublisher.Dispatch(request.requestId(),
                        BridgeDecision.Status.ACCEPTED, "the message satisfies the contract",
                        Optional.of(running), Optional.empty(), true, false,
                        Optional.empty());
            }
            return answer(request);
        }

        /**
         * The answer of a finished task.
         *
         * @param request the request
         * @return the dispatch
         */
        private TaskPublisher.Dispatch answer(CognitiveTaskRequest request) {
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
                    Optional.empty());
        }
    }

    /**
     * A transport that remembers the request it was asked to send.
     */
    private static final class WatchingTransport implements CognitiveCoordinator.TaskTransport {

        private CognitiveTaskRequest request;

        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            this.request = request;
            return TaskPublisher.Dispatch.empty(request.requestId(),
                    BridgeDecision.Status.ACCEPTED, "the message satisfies the contract");
        }
    }

    /** A transport that never gets an answer. */
    private static final class SilentTransport implements CognitiveCoordinator.TaskTransport {
        @Override
        public TaskPublisher.Dispatch dispatch(CognitiveTaskRequest request, String sentAt) {
            return TaskPublisher.Dispatch.empty(request.requestId(),
                    BridgeDecision.Status.ACCEPTED, "the message satisfies the contract");
        }
    }

    private CognitiveCoordinator.TaskSpec spec() {
        return new CognitiveCoordinator.TaskSpec("tsk-1", "brain.cognitive-inference",
                "is the lamp lit", "the lamp is lit", 5000L, 5000L, List.of());
    }

    @Test
    @DisplayName("the intent is durable before the request is sent")
    void theIntentIsDurableBeforeTheSend() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            AnsweringTransport transport = new AnsweringTransport(store);
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store, transport,
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            coordinator.arrange(spec());

            assertTrue(transport.sawTheIntent,
                    "the intent must be written before the request leaves");
        }
    }

    @Test
    @DisplayName("the verdict is recorded, and it is chain A's verdict")
    void theVerdictIsRecorded() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            AnsweringTransport transport = new AnsweringTransport(store);
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store, transport,
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            CognitiveCoordinator.Outcome outcome = coordinator.arrange(spec());

            assertEquals(DispatchIntent.State.ADJUDICATED, outcome.state());
            assertTrue(outcome.verdict().isPresent());
            assertEquals(RemoteReportAssessor.Disposition.UNRESOLVED,
                    outcome.verdict().orElseThrow().disposition(),
                    "a peer that asserts its own confidence settles nothing here");
            DispatchIntent dispatch = store.findDispatch("req-tsk-1").orElseThrow();
            assertEquals("UNRESOLVED", dispatch.disposition());
        }
    }

    @Test
    @DisplayName("a peer that says the task is still running is waited for, not judged")
    void aPeerThatSaysTheTaskIsStillRunningIsWaitedFor() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            ProgressingTransport transport = new ProgressingTransport();
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store, transport,
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            CognitiveCoordinator.Outcome outcome = coordinator.arrange(spec());

            assertEquals(2, transport.calls.get(),
                    "the caller must ask again while the peer is still working");
            assertEquals(DispatchIntent.State.ADJUDICATED, outcome.state(),
                    "the task completes when the peer finally answers");
            assertTrue(outcome.verdict().isPresent(),
                    "the answer that came is judged, and only then");
        }
    }

    @Test
    @DisplayName("the dispatch names the episode the task belongs to, and the peer sees "
            + "the same one")
    void theDispatchNamesTheEpisodeTheTaskBelongsTo() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            WatchingTransport transport = new WatchingTransport();
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store, transport,
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            coordinator.arrange(new CognitiveCoordinator.TaskSpec("tsk-1",
                    "brain.cognitive-inference", "is the lamp lit", "the lamp is lit", 5000L,
                    5000L, List.of(), "ep-the-episode"));

            assertEquals("ep-the-episode", transport.request.correlation().episodeId(),
                    "the peer must see the episode the task belongs to");
            DispatchIntent dispatch = store.findDispatch("req-tsk-1").orElseThrow();
            assertEquals("ep-the-episode", dispatch.parentEpisodeId(),
                    "the dispatch row must name the episode durably");
        }
    }

    @Test
    @DisplayName("a task that belongs to no episode names no episode")
    void aTaskThatBelongsToNoEpisodeNamesNoEpisode() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            WatchingTransport transport = new WatchingTransport();
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store, transport,
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            coordinator.arrange(spec());

            assertNull(transport.request.correlation().episodeId(),
                    "a task with no episode must not claim one");
        }
    }

    @Test
    @DisplayName("a peer that answers nothing produces a failure, not a silent success")
    void aSilentPeerProducesAFailure() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store,
                    new SilentTransport(),
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            CognitiveCoordinator.Outcome outcome = coordinator.arrange(spec());

            assertEquals(DispatchIntent.State.FAILED, outcome.state());
            assertTrue(outcome.verdict().isEmpty(), "there was nothing to judge");
            assertEquals(DispatchIntent.State.FAILED,
                    store.findDispatch("req-tsk-1").orElseThrow().state());
        }
    }

    @Test
    @DisplayName("the whole history of the task is visible after a restart")
    void theHistoryIsVisibleAfterARestart() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store,
                    new AnsweringTransport(store),
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);
            coordinator.arrange(spec());
        }
        try (CognitiveStore reopened = new CognitiveStore(database)) {
            DispatchIntent dispatch = reopened.findDispatch("req-tsk-1").orElseThrow();
            assertEquals("tsk-1", dispatch.taskId());
            assertEquals(DispatchIntent.State.ADJUDICATED, dispatch.state());
            assertEquals("UNRESOLVED", dispatch.disposition());
            assertEquals("chain-b-brain-runtime", dispatch.remoteResponder());
            assertTrue(reopened.unfinishedDispatches(WORKSPACE).isEmpty());
        }
    }

    @Test
    @DisplayName("the request carries the expectation, so the answer is judged against "
            + "something")
    void theRequestCarriesTheExpectation() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            AnsweringTransport transport = new AnsweringTransport(store);
            CognitiveCoordinator coordinator = new CognitiveCoordinator(store, transport,
                    new RemoteReportAssessor(RemoteReportAssessor.LocalEvidence.NONE),
                    WORKSPACE, "chain-a-epistemic-core", () -> AT);

            coordinator.arrange(spec());

            assertTrue(transport.sawTheIntent);
            // The expectation the coordinator declared is in the request's context, and
            // the request is the one the peer received.
            assertEquals(1, store.dispatches(WORKSPACE).size());
            assertEquals("brain.cognitive-inference",
                    store.dispatches(WORKSPACE).get(0).capabilityId());
        }
    }

    /** The correlation the request carries, for the fixtures above. */
    @SuppressWarnings("unused")
    private static Correlation correlation() {
        return new Correlation("corr-1", "ep-parent", "ep-1", "req-1");
    }
}
