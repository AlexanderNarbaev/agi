package io.matrix.api.bridge;

import com.sun.net.httpserver.HttpServer;
import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.bridge.TaskPublisher;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;
import io.matrix.contract.bridge.HmacBridgeSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the bounds the peer keeps on its own side of the bridge.
 *
 * <p><b>What this test is.</b> Chain A's bounds were proved in the core; this is the
 * other half of the same rule. A peer that accepts everything is an unbounded queue
 * with a database behind it, and a peer that answers late without saying so makes the
 * caller discover the lateness by itself. So the peer refuses an input larger than the
 * limit the task declared, refuses new work when its durable inbox is full, and says
 * plainly that its work outlasted the deadline the task gave it.</p>
 *
 * <p><b>What it does not claim.</b> A deadline that passes does not delete a result:
 * the answer that arrived late is still stored, still signed and still returned, and
 * the caller is told that it is late.</p>
 */
class TaskPeerBoundsTest {

    /** The shared secret of the test. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    @TempDir
    Path directory;

    private HttpServer server;
    private TaskBridge bridge;
    private RemoteTaskStore store;
    private CountingBrain brain;

    @BeforeEach
    void startChainB() throws IOException, java.sql.SQLException {
        brain = new CountingBrain();
        store = new RemoteTaskStore(directory.resolve("tasks.sqlite"));
        bridge = new TaskBridge(store, CognitiveCapability.standard(brain), SECRET, () -> NOW);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TaskBridge.TASK_PATH, exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            String answer = bridge.answer(new String(request, StandardCharsets.UTF_8));
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopChainB() {
        server.stop(0);
    }

    /** The URL chain B serves on. */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * A request for the bounded cognitive inference.
     *
     * @param requestId the request id
     * @param input     the input the peer will read
     * @param limit     the payload limit the task declares
     * @param deadline  the deadline the task allows, in milliseconds
     * @return the request
     */
    private static CognitiveTaskRequest request(String requestId, String input, int limit,
                                                long deadline) {
        return request(requestId, input, limit, deadline,
                CognitiveCapability.COGNITIVE_INFERENCE);
    }

    /**
     * A request for a named capability.
     *
     * @param requestId    the request id
     * @param input        the input the peer will read
     * @param limit        the payload limit the task declares
     * @param deadline     the deadline the task allows, in milliseconds
     * @param capabilityId the capability the task asks for
     * @return the request
     */
    private static CognitiveTaskRequest request(String requestId, String input, int limit,
                                                long deadline, String capabilityId) {
        return new CognitiveTaskRequest(
                BridgeProtocol.VERSION,
                "tsk-" + requestId,
                requestId,
                new Correlation("corr-" + requestId, "ep-parent", "ep-" + requestId, requestId),
                "ep-parent",
                "chain-a-epistemic-core",
                capabilityId,
                CognitiveCapability.COGNITIVE_INFERENCE_VERSION,
                CognitiveTaskRequest.Kind.COGNITIVE_INFERENCE,
                input,
                java.util.Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                deadline,
                limit,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId,
                null);
    }

    /** A brain that counts how many times it was asked to think. */
    private static final class CountingBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();

        private long durationMillis = 5L;

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            return new CycleResult("the lamp is lit", 0.8, durationMillis, true,
                    List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }

    @Test
    @DisplayName("a capability the peer does not serve is recorded as unsupported, not as a "
            + "failure")
    void aCapabilityThePeerDoesNotServeIsRecordedAsUnsupported() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");

        TaskPublisher.Dispatch dispatch = publisher.dispatch(
                request("req-unserved", "a question", 4096, 5_000L,
                        "brain.a-capability-the-peer-does-not-serve"), NOW);

        assertThat(dispatch.response().isPresent()).isTrue();
        assertThat(dispatch.response().orElseThrow().status())
                .isEqualTo(CognitiveTaskResponse.Status.UNSUPPORTED);
        RemoteTaskStore.Row row = store.find("req-unserved").orElseThrow();
        assertThat(row.state())
                .withFailMessage("the peer never served that capability; the row must say so")
                .isEqualTo(RemoteTaskStore.State.UNSUPPORTED);
    }

    @Test
    @DisplayName("a request larger than the limit it declares is refused, and the brain is not asked")
    void aRequestLargerThanTheLimitItDeclaresIsRefused() {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");

        TaskPublisher.Dispatch dispatch = publisher.dispatch(
                request("req-bound-1", "an input that is certainly longer than eight bytes",
                        8, 5_000L), NOW);

        // The message was accepted — it authenticated and it was well formed — but the
        // task was refused, and the refusal travels in the signed response.
        assertThat(dispatch.response().isPresent())
                .withFailMessage("the peer must say why it refused the task")
                .isTrue();
        CognitiveTaskResponse response = dispatch.response().orElseThrow();
        assertThat(response.status()).isEqualTo(CognitiveTaskResponse.Status.REFUSED);
        assertThat(response.refusalReason()).contains("payload limit");
        assertThat(brain.calls.get())
                .withFailMessage("an oversized request must not reach the brain")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("the peer refuses new work when its durable inbox is full")
    void thePeerRefusesNewWorkWhenItsInboxIsFull() throws Exception {
        // The inbox holds one unfinished request at most, and it is already full.
        Path database = directory.resolve("bounded.sqlite");
        RemoteTaskStore store = new RemoteTaskStore(database, 1);
        RemoteTaskStore.Admission admitted = store.admit(
                request("req-already-there", "a question", 4096, 5_000L), "digest", NOW);
        assertThat(admitted.decision()).isEqualTo(RemoteTaskStore.Decision.ACCEPTED);

        bridge = new TaskBridge(store, CognitiveCapability.standard(brain), SECRET, () -> NOW);
        server.removeContext(TaskBridge.TASK_PATH);
        server.createContext(TaskBridge.TASK_PATH, exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            String answer = bridge.answer(new String(request, StandardCharsets.UTF_8));
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        TaskPublisher.Dispatch dispatch = publisher.dispatch(
                request("req-newcomer", "a question", 4096, 5_000L), NOW);

        assertThat(dispatch.response().isPresent())
                .withFailMessage("the peer must say why it refused the task")
                .isTrue();
        CognitiveTaskResponse response = dispatch.response().orElseThrow();
        assertThat(response.status()).isEqualTo(CognitiveTaskResponse.Status.REFUSED);
        assertThat(response.refusalReason()).contains("inbox is full");
        assertThat(brain.calls.get())
                .withFailMessage("a request that the peer cannot hold must not reach the brain")
                .isEqualTo(0);
        RemoteTaskStore.Row refused = store.find("req-newcomer").orElseThrow();
        assertThat(refused.state()).isEqualTo(RemoteTaskStore.State.REFUSED);
        assertThat(refused.refusalReason()).contains("inbox is full");
    }

    @Test
    @DisplayName("work that outlasts the deadline is reported as timed out, and the result is kept")
    void workThatOutlastsTheDeadlineIsReportedAsTimedOutAndTheResultIsKept() throws Exception {
        brain.durationMillis = 9_000L;
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");

        TaskPublisher.Dispatch dispatch = publisher.dispatch(
                request("req-slow-1", "a question", 4096, 5_000L), NOW);

        assertThat(dispatch.response().isPresent())
                .withFailMessage("the late answer must still arrive — a deadline does not delete it")
                .isTrue();
        CognitiveTaskResponse response = dispatch.response().orElseThrow();
        assertThat(response.status()).isEqualTo(CognitiveTaskResponse.Status.TIMED_OUT);
        assertThat(response.result()).isEqualTo("the lamp is lit");
        assertThat(response.refusalReason()).contains("longer than the");

        RemoteTaskStore store = new RemoteTaskStore(directory.resolve("tasks.sqlite"));
        RemoteTaskStore.Row row = store.find("req-slow-1").orElseThrow();
        assertThat(row.state()).isEqualTo(RemoteTaskStore.State.TIMED_OUT);
        assertThat(row.resultJson()).isNotBlank();
    }

    @Test
    @DisplayName("a request that ended in a terminal state is replayed, not run again")
    void aRequestThatEndedInATerminalStateIsReplayedNotRunAgain() {
        brain.durationMillis = 9_000L;
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");

        publisher.dispatch(request("req-slow-2", "a question", 4096, 5_000L), NOW);
        TaskPublisher.Dispatch replay = publisher.dispatch(
                request("req-slow-2", "a question", 4096, 5_000L), NOW);

        assertThat(brain.calls.get())
                .withFailMessage("a terminal request must not be executed a second time")
                .isEqualTo(1);
        assertThat(replay.response().isPresent())
                .withFailMessage("the caller gets the stored answer")
                .isTrue();
        assertThat(replay.response().orElseThrow().status())
                .isEqualTo(CognitiveTaskResponse.Status.TIMED_OUT);
    }
}
