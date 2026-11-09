package io.matrix.api.bridge;

import com.sun.net.httpserver.HttpServer;
import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.bridge.TaskPublisher;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskReport;
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
 * W53 — one task, dispatched across the process boundary and answered.
 *
 * <p><b>What this test is.</b> It runs the real sender and the real receiver over a real
 * HTTP connection: chain A's {@link TaskPublisher} signs a canonical request and posts
 * it; chain B's {@link TaskBridge} authenticates it, admits it durably, executes the
 * capability and answers with a signed response and a signed report; chain A verifies
 * both signatures before it accepts anything. Nothing here is a stub of the message path
 * — the only substitution is the cognitive path itself, which is counted rather than
 * replaced, so that the test can say how many times the peer was actually asked to
 * think.</p>
 *
 * <p><b>What it proves.</b> The three states the mandate keeps apart are three states
 * here: the message was accepted, the execution completed, and the report exists. A
 * caller that confuses them would be a caller that treats a delivery receipt as a
 * result.</p>
 */
class TaskRoundTripTest {

    /** The shared secret of the test. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-09T12:00:00Z";

    @TempDir
    Path directory;

    private HttpServer server;
    private TaskBridge bridge;
    private CountingBrain brain;

    @BeforeEach
    void startChainB() throws IOException, java.sql.SQLException {
        brain = new CountingBrain();
        bridge = new TaskBridge(new RemoteTaskStore(directory.resolve("tasks.sqlite")),
                CognitiveCapability.standard(brain), SECRET, () -> NOW);
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

    /** A request for the bounded cognitive inference. */
    private static CognitiveTaskRequest request(String requestId) {
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
                "is the lamp lit",
                java.util.Map.of(),
                List.of(),
                "cognitive-task-response/v1",
                CognitiveTaskRequest.DEFAULT_BUDGET_MILLIS,
                CognitiveTaskRequest.DEFAULT_DEADLINE_MILLIS,
                4096,
                CognitiveTaskRequest.MAX_REMOTE_HOPS,
                Provenance.of("chain-a", NOW),
                "hash-" + requestId,
                null);
    }

    /** A brain that counts how many times it was asked to think. */
    private static final class CountingBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            return new CycleResult("the lamp is lit", 0.8, 5L, true, List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }

    @Test
    @DisplayName("a dispatched task comes back as a signed response and a signed report")
    void aDispatchedTaskComesBack() {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");

        TaskPublisher.Dispatch dispatch = publisher.dispatch(request("req-rt-1"), NOW);

        assertThat(dispatch.status().name()).isEqualTo("ACCEPTED");
        assertThat(dispatch.response().isPresent())
                .withFailMessage("the peer answered with a result")
                .isTrue();
        assertThat(dispatch.responseSigned())
                .withFailMessage("the response must arrive signed")
                .isTrue();
        assertThat(dispatch.report().isPresent())
                .withFailMessage("the peer answered with a report")
                .isTrue();
        assertThat(dispatch.reportSigned())
                .withFailMessage("the report must arrive signed")
                .isTrue();
        assertThat(dispatch.completed()).isTrue();
        assertThat(brain.calls.get())
                .withFailMessage("the peer must have run the capability once")
                .isEqualTo(1);

        CognitiveTaskResponse response = dispatch.response().orElseThrow();
        assertThat(response.status()).isEqualTo(CognitiveTaskResponse.Status.COMPLETED);
        assertThat(response.result()).isEqualTo("the lamp is lit");
        assertThat(response.capabilityId()).isEqualTo(CognitiveCapability.COGNITIVE_INFERENCE);
        assertThat(response.responderId()).isEqualTo("chain-b-brain-runtime");

        CognitiveTaskReport report = dispatch.report().orElseThrow();
        assertThat(report.requestId()).isEqualTo("req-rt-1");
        assertThat(report.relation()).isEqualTo(CognitiveTaskReport.Relation.INDEPENDENT);
        assertThat(report.responderId()).isEqualTo("chain-b-brain-runtime");
    }

    @Test
    @DisplayName("a report whose signature does not verify is dropped, not passed on")
    void aForgedReportIsDropped() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");

        // The peer is given a different secret, so its report is signed by someone the
        // caller does not trust.
        bridge = new TaskBridge(new RemoteTaskStore(directory.resolve("other.sqlite")),
                CognitiveCapability.standard(brain),
                "a-different-secret".getBytes(StandardCharsets.UTF_8), () -> NOW);
        server.removeContext(TaskBridge.TASK_PATH);
        server.createContext(TaskBridge.TASK_PATH, exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            String answer = bridge.answer(new String(request, StandardCharsets.UTF_8));
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        TaskPublisher.Dispatch dispatch = publisher.dispatch(request("req-rt-2"), NOW);

        assertThat(dispatch.report().isPresent())
                .withFailMessage("an unverified report must not reach the epistemic side")
                .isFalse();
        assertThat(dispatch.reportSigned()).isFalse();
    }
}
