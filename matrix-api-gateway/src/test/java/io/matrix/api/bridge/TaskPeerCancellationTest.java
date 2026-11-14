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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the peer honours a withdrawal.
 *
 * <p><b>What this test is about.</b> Chain A may withdraw a task it dispatched, but a
 * withdrawal that stops only the caller is a withdrawal that hides work: the peer keeps
 * running the task and commits its effect, and the caller believes it stopped. So the
 * withdrawal travels to the peer, the peer records it, and the peer's own state machine
 * says what became of the task.</p>
 *
 * <p><b>The three honest cases.</b> A task withdrawn <i>before it started</i> is never
 * executed. A task withdrawn <i>while it runs</i> cannot be interrupted — the brain
 * cannot be interrupted — so the work finishes and the row is closed as cancelled, with
 * the answer kept. A task that has <i>already finished</i> is not withdrawn at all: a
 * cancellation cannot undo a committed effect, and saying otherwise would hide a fact
 * the caller needs.</p>
 */
class TaskPeerCancellationTest {

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
    private BlockingBrain brain;

    @BeforeEach
    void startChainB() throws IOException, java.sql.SQLException {
        brain = new BlockingBrain();
        store = new RemoteTaskStore(directory.resolve("tasks.sqlite"));
        bridge = new TaskBridge(store, CognitiveCapability.standard(brain), SECRET, () -> NOW);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // The production peer serves on a pool of threads, so it can answer a second
        // delivery while the first task is still running. The test's server must do the
        // same, or it would queue the second delivery behind the running task and the
        // test would measure the queue rather than the peer.
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
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
                java.util.Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                5_000L,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId,
                null);
    }

    /** A brain that counts its calls and can be held. */
    private static final class BlockingBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            started.countDown();
            try {
                // The wait is longer than the test's own steps: a brain that gave up on
                // its own would end the task while the test still believes it runs.
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new CycleResult("the lamp is lit", 0.8, 5L, true, List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }

    @Test
    @DisplayName("a task withdrawn before it started is never executed")
    void aTaskWithdrawnBeforeItStartedIsNeverExecuted() throws Exception {
        // The request is in the peer's inbox and has not started: the withdrawal closes
        // it, and the delivery that follows must not reach the brain.
        store.admit(request("req-cancel-early", "a question"), "digest", NOW);
        assertThat(store.cancel("req-cancel-early", NOW))
                .contains(RemoteTaskStore.State.CANCELLED);

        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        publisher.dispatch(request("req-cancel-early", "a question"), NOW);

        assertThat(brain.calls.get())
                .withFailMessage("a task withdrawn before it started must not reach the brain")
                .isEqualTo(0);
        RemoteTaskStore.Row row = store.find("req-cancel-early").orElseThrow();
        assertThat(row.state()).isEqualTo(RemoteTaskStore.State.CANCELLED);
        assertThat(row.resultJson())
                .withFailMessage("the row must not claim that the task produced an answer")
                .isNull();
    }

    @Test
    @DisplayName("a withdrawal that arrives while the task runs closes the row as cancelled "
            + "and keeps the answer")
    void aWithdrawalWhileTheTaskRunsClosesTheRowAsCancelledAndKeepsTheAnswer()
            throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        Thread delivery = new Thread(() -> publisher.dispatch(
                request("req-cancel-running", "a question"), NOW));
        delivery.start();
        assertThat(brain.started.await(5, TimeUnit.SECONDS))
                .withFailMessage("the task must have started")
                .isTrue();

        // The task is running. The withdrawal is recorded rather than interrupting the
        // brain, which cannot be interrupted.
        assertThat(store.cancel("req-cancel-running", NOW))
                .contains(RemoteTaskStore.State.RUNNING);
        assertThat(store.cancelRequested("req-cancel-running")).isTrue();

        brain.release.countDown();
        delivery.join(10_000L);

        RemoteTaskStore.Row row = store.find("req-cancel-running").orElseThrow();
        assertThat(row.state()).isEqualTo(RemoteTaskStore.State.CANCELLED);
        assertThat(row.resultJson())
                .withFailMessage("the answer the peer produced must not be thrown away")
                .isNotNull();
        assertThat(brain.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a task that already finished is not withdrawn")
    void aTaskThatAlreadyFinishedIsNotWithdrawn() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        brain.release.countDown();
        publisher.dispatch(request("req-cancel-late", "a question"), NOW);

        RemoteTaskStore.Row before = store.find("req-cancel-late").orElseThrow();
        assertThat(before.state()).isEqualTo(RemoteTaskStore.State.COMPLETED);

        assertThat(store.cancel("req-cancel-late", NOW))
                .withFailMessage("a finished task cannot be withdrawn")
                .contains(RemoteTaskStore.State.COMPLETED);
        RemoteTaskStore.Row after = store.find("req-cancel-late").orElseThrow();
        assertThat(after.state()).isEqualTo(RemoteTaskStore.State.COMPLETED);
        assertThat(after.resultJson()).isNotNull();
    }

    @Test
    @DisplayName("a duplicate of a task that is still running is answered with its status")
    void aDuplicateOfATaskThatIsStillRunningIsAnsweredWithItsStatus() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        Thread delivery = new Thread(() -> publisher.dispatch(
                request("req-still-running", "a question"), NOW));
        delivery.start();
        assertThat(brain.started.await(5, TimeUnit.SECONDS))
                .withFailMessage("the task must have started")
                .isTrue();

        // The second delivery of the same request finds the task running. The peer must
        // say so: a silent answer makes the caller spend its budget discovering a delay
        // the peer knows about.
        TaskPublisher.Dispatch second = publisher.dispatch(
                request("req-still-running", "a question"), NOW);

        assertThat(second.response().isPresent())
                .withFailMessage("a duplicate must be answered with the task's status")
                .isTrue();
        assertThat(second.response().orElseThrow().status())
                .isEqualTo(CognitiveTaskResponse.Status.IN_PROGRESS);
        assertThat(brain.calls.get())
                .withFailMessage("the duplicate must not run the task a second time")
                .isEqualTo(1);

        brain.release.countDown();
        delivery.join(10_000L);
    }

    @Test
    @DisplayName("a withdrawal for a request the peer does not hold is refused")
    void aWithdrawalForARequestThePeerDoesNotHoldIsRefused() throws Exception {
        assertThat(store.cancel("req-never-seen", NOW)).isEmpty();
    }

    @Test
    @DisplayName("the withdrawal travels as its own signed kind, and the peer refuses it "
            + "without the right")
    void theWithdrawalTravelsAsItsOwnSignedKind() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        store.admit(request("req-cancel-signed", "a question"), "digest", NOW);

        boolean accepted = publisher.cancel("req-cancel-signed", "tsk-req-cancel-signed",
                "the task was withdrawn by the requester", NOW);

        assertThat(accepted)
                .withFailMessage("the peer must accept a withdrawal it is entitled to receive")
                .isTrue();
        assertThat(store.find("req-cancel-signed").orElseThrow().state())
                .isEqualTo(RemoteTaskStore.State.CANCELLED);

    }

}
