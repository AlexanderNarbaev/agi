package io.matrix.api.bridge;

import com.sun.net.httpserver.HttpServer;
import io.matrix.api.brain.BrainCycle;
import io.matrix.api.brain.ExplainTrace;
import io.matrix.bridge.TaskPublisher;
import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.CognitiveTaskRequest;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the peer's own measurement of its own stages.
 *
 * <p><b>What this test is.</b> The mandate asks for the peer's authentication, its
 * durable inbox write and its answer signing to be measured apart. The wave's honest
 * list said that separating them needs a protocol-version decision, because the peer
 * reported a single duration for all of its work. This test is the proof that the
 * separation does not need a protocol decision at all: the peer measures its own
 * stages, keeps them in its own durable row, and the caller receives exactly the same
 * answer it received before.</p>
 *
 * <p><b>What it proves.</b> Three things. The peer's row carries the stages of the
 * execution, each measured with the peer's own monotonic clock. The execution stage is
 * a measurement and not a constant: a brain that takes a known time must show up in
 * the number. And the answer that travels the wire carries nothing new: the
 * measurement stays on the peer's side of the boundary.</p>
 */
class PeerStageTimingsTest {

    /** The shared secret of the test. */
    private static final byte[] SECRET =
            "the-bridge-secret-of-the-tests".getBytes(StandardCharsets.UTF_8);

    /** The instant the fixtures claim. */
    private static final String NOW = "2026-11-10T09:00:00Z";

    /** How long the brain of this test thinks. */
    private static final long BRAIN_MILLIS = 60L;

    @TempDir
    Path directory;

    private HttpServer server;
    private TaskBridge bridge;
    private RemoteTaskStore store;
    private SlowBrain brain;

    @BeforeEach
    void startChainB() throws IOException, java.sql.SQLException {
        brain = new SlowBrain();
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

    @Test
    @DisplayName("the peer keeps the stages of its own work in its own row")
    void thePeerKeepsTheStagesOfItsOwnWork() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        publisher.dispatch(request("req-stages-1"), NOW);

        RemoteTaskStore.Row row = store.find("req-stages-1").orElseThrow();
        assertThat(row.stageTimingsJson())
                .withFailMessage("the peer must record what its own work cost")
                .isNotNull();

        Map<String, Double> stages = readStages(row.stageTimingsJson());
        assertThat(stages.keySet())
                .withFailMessage("the peer must separate its durable write, its execution, "
                        + "its outcome write and its answer signing")
                .contains("inboxWrite", "stateWrite", "execution", "outcomeWrite",
                        "answerSigning", "deliveryTotal", "authenticationAndValidation");
        assertThat(stages.values())
                .withFailMessage("a stage cannot cost a negative amount of time")
                .allMatch(value -> value >= 0.0);
        assertThat(stages.get("authenticationAndValidation"))
                .withFailMessage("the remainder cannot be larger than the whole delivery")
                .isLessThanOrEqualTo(stages.get("deliveryTotal"));
    }

    @Test
    @DisplayName("the execution stage is measured, not written down as a constant")
    void theExecutionStageIsMeasured() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        publisher.dispatch(request("req-stages-2"), NOW);

        Map<String, Double> stages =
                readStages(store.find("req-stages-2").orElseThrow().stageTimingsJson());

        assertThat(stages.get("execution"))
                .withFailMessage("the peer must measure the execution it really performed")
                .isGreaterThanOrEqualTo((double) BRAIN_MILLIS);
        assertThat(stages.get("deliveryTotal"))
                .withFailMessage("the whole delivery must be at least the execution")
                .isGreaterThanOrEqualTo(stages.get("execution"));
        assertThat(brain.calls.get())
                .withFailMessage("the peer must have run the capability once")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the measurement does not travel on the wire")
    void theMeasurementDoesNotTravelOnTheWire() throws Exception {
        TaskPublisher publisher = new TaskPublisher(baseUrl(), new HmacBridgeSigner(SECRET),
                "chain-a-epistemic-core");
        TaskPublisher.Dispatch dispatch = publisher.dispatch(request("req-stages-3"), NOW);

        assertThat(dispatch.completed())
                .withFailMessage("the task must have completed")
                .isTrue();
        assertThat(dispatch.response().orElseThrow().status().name()).isEqualTo("COMPLETED");
        // The caller's answer carries the response and the report, and nothing else:
        // the peer keeps its measurement on its own side of the boundary.
        assertThat(dispatch.response().orElseThrow().toString())
                .withFailMessage("the peer's measurement must not travel on the wire")
                .doesNotContain("authenticationAndValidation");
    }

    /** Reads the measurement the peer recorded. */
    @SuppressWarnings("unchecked")
    private static Map<String, Double> readStages(String json) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, Map.class);
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

    /** A brain that takes a known amount of time and counts how often it was asked. */
    private static final class SlowBrain implements BrainCycle {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CycleResult cycle(String input, String context, String model) {
            calls.incrementAndGet();
            try {
                Thread.sleep(BRAIN_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new CycleResult("the lamp is lit", 0.8, BRAIN_MILLIS, true,
                    List.of("BIR", "HDC"));
        }

        @Override
        public ExplainTrace buildExplain(String explainId) {
            return null;
        }
    }
}
