package io.matrix.bridge;

import com.sun.net.httpserver.HttpServer;
import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Confidence;
import io.matrix.contract.Outcome;
import io.matrix.contract.Provenance;
import io.matrix.contract.RetryPolicy;
import io.matrix.contract.ValidityInterval;
import io.matrix.contract.bridge.BridgeAnswer;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeProtocol;
import io.matrix.contract.bridge.HmacBridgeSigner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W52 — the publisher of the SECOND direction, over a real socket.
 *
 * <p><b>Why a socket and not a mock.</b> The publisher's job is a wire contract: the
 * envelope is signed, the headers carry the protocol, the request id is the idempotency
 * key, and the answer is read back from the body. A mock would prove that the publisher
 * calls a method; only a socket proves that what leaves this process is what the other
 * side can read.</p>
 */
class KnowledgePublisherTest {

    /** The shared secret of the test. */
    private static final byte[] SECRET =
            "the-publisher-secret".getBytes(StandardCharsets.UTF_8);

    /** The instant the message claims to have been written at. */
    private static final String SENT_AT = "2026-11-09T10:00:00Z";

    /** The identity of the sender. */
    private static final String SENDER_ID = "chain-a-epistemic-core";

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** An established belief. */
    private static Belief belief() {
        return new Belief("belief-1", "the lamp is lit when power and switch are both on",
                BeliefStatus.SUPPORTED,
                new Confidence(0.9, Confidence.Basis.MEASURED, 3, 0, "three observations agree",
                        List.of()),
                List.of("ev-1"), List.of(),
                Provenance.of("the cognitive kernel", SENT_AT),
                ValidityInterval.openFrom(SENT_AT), 1, null, null, null);
    }

    /**
     * A receiver that answers the way chain B's bridge answers.
     *
     * @param deliveries how many deliveries arrived
     * @param bodies     the bodies that arrived
     * @return the base URL of the server
     */
    private String receiver(AtomicInteger deliveries, java.util.List<String> bodies)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(KnowledgePublisher.KNOWLEDGE_PATH, exchange -> {
            deliveries.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            bodies.add(body);
            String answer = "{\"status\":\"ACCEPTED\",\"requestId\":\"req-knowledge-1\","
                    + "\"auditId\":\"audit-1\",\"reason\":\"accepted\","
                    + "\"outcome\":{\"id\":\"outcome-1\",\"actionId\":\"action-1\","
                    + "\"observationId\":null,\"predictionId\":null,\"status\":\"SUCCEEDED\","
                    + "\"observed\":\"the brain took the proposition\",\"matched\":true,"
                    + "\"error\":0.0,\"reason\":\"the brain learned it\",\"at\":\""
                    + SENT_AT + "\"}}";
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("what leaves this process is a signed envelope the other side can read")
    void whatLeavesThisProcessIsASignedEnvelope() throws IOException {
        AtomicInteger deliveries = new AtomicInteger();
        java.util.List<String> bodies = new java.util.ArrayList<>();
        String baseUrl = receiver(deliveries, bodies);

        KnowledgePublisher publisher = new KnowledgePublisher(baseUrl,
                new HmacBridgeSigner(SECRET), SENDER_ID);
        BridgeAnswer answer = publisher.publishKnowledge("req-knowledge-1", belief(),
                Provenance.of("chain-a", SENT_AT), SENT_AT);

        assertEquals(1, deliveries.get());
        assertTrue(answer.accepted(), "the receiver accepted the message");
        assertNotNull(answer.outcome(), "the receiver reported what it did");
        assertEquals("the brain took the proposition", answer.outcome().observed());

        BridgeEnvelope envelope = new BridgeEnvelopeCodec().decode(bodies.get(0));
        assertEquals(BridgeProtocol.NAME, envelope.protocol());
        assertEquals(BridgeProtocol.VERSION, envelope.version());
        assertEquals(BridgeCapability.KNOWLEDGE, envelope.kind(),
                "the message exercises the right of the second direction");
        assertEquals("req-knowledge-1", envelope.requestId(),
                "the request id is the idempotency key");
        assertTrue(envelope.verify(new HmacBridgeSigner(SECRET)),
                "the envelope is signed with the shared secret");

        BridgePayload payload = new BridgePayloadCodec().decode(envelope.payload());
        assertEquals("the lamp is lit when power and switch are both on",
                payload.candidate().proposition(),
                "the payload carries the proposition the core established");
        assertEquals(BeliefStatus.SUPPORTED, payload.candidate().status());
    }

    @Test
    @DisplayName("a receiver that answers with nothing is a refusal, not a silent success")
    void aReceiverThatAnswersWithNothingIsARefusal() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(KnowledgePublisher.KNOWLEDGE_PATH, exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        KnowledgePublisher publisher = new KnowledgePublisher(baseUrl,
                new HmacBridgeSigner(SECRET), SENDER_ID);

        BridgeAnswer answer = publisher.publishKnowledge("req-knowledge-1", belief(),
                Provenance.of("chain-a", SENT_AT), SENT_AT);

        assertFalse(answer.accepted(), "an empty answer is not an acceptance");
        assertTrue(answer.decision().reason().contains("empty body"),
                "the refusal says what was wrong: " + answer.decision().reason());
    }

    @Test
    @DisplayName("a receiver that is not there is a failure, and it is named as one")
    void aReceiverThatIsNotThereIsAFailure() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int closedPort = server.getAddress().getPort();
        server.stop(0);
        server = null;

        KnowledgePublisher publisher = new KnowledgePublisher("http://127.0.0.1:" + closedPort,
                new HmacBridgeSigner(SECRET), SENDER_ID, Duration.ofSeconds(2),
                new RetryPolicy(2, 10L, 2.0, 50L));

        KnowledgePublicationFailure failure = assertThrows(KnowledgePublicationFailure.class,
                () -> publisher.publishKnowledge("req-knowledge-1", belief(),
                        Provenance.of("chain-a", SENT_AT), SENT_AT));
        assertTrue(failure.getMessage().contains("attempt"),
                "the failure says how many attempts were made: " + failure.getMessage());
    }
}
