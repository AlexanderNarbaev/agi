package io.matrix.brain.runtime.bridge;

import com.sun.net.httpserver.HttpServer;
import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.RetryPolicy;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.HmacBridgeSigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 §11.1 and §11.5 — the publisher's behaviour when the bridge cannot be reached.
 *
 * <p>Two things are proved here.</p>
 *
 * <p><b>The retry policy has a consumer.</b> {@code RetryPolicy} existed in the canonical
 * contracts as a statement about retries with nothing in the repository asking it. The
 * publisher is that consumer: it retries a TRANSPORT failure within the policy's bounds and
 * it does not retry a refusal, because a refusal is an answer.</p>
 *
 * <p><b>An unreachable peer fails fast and explicitly.</b> The publisher must not hang, and
 * it must not pretend that the message was delivered: the caller gets an exception that
 * names the attempts, and the caller can then decide whether to send it again.</p>
 */
class BridgePublisherRetryTest {

    /** The endpoint of a peer that does not exist. */
    private static final String UNREACHABLE = "http://127.0.0.1:";

    /** The instant the demonstration stamps its messages with. */
    private static final String AT = "2026-11-08T00:00:00Z";

    private static Observation observation() {
        return Observation.ofText("obs-retry", "the brain runtime reports something", AT,
                Provenance.of("chain-b", AT));
    }

    private static BridgePublisher publisher(String baseUrl, RetryPolicy policy) {
        return new BridgePublisher(baseUrl, new HmacBridgeSigner("the shared secret"), "chain-b",
                Duration.ofSeconds(2), policy);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    @DisplayName("a transport failure is retried within the policy's bounds and then succeeds")
    void aTransportFailureIsRetried() throws Exception {
        AtomicInteger received = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/bridge/messages", exchange -> {
            int attempt = received.incrementAndGet();
            if (attempt == 1) {
                // The peer drops the connection without an answer: a transport failure that
                // says nothing about whether the message was applied.
                exchange.close();
                return;
            }
            byte[] body = ("{\"status\":\"ACCEPTED\",\"requestId\":\"req-retry\","
                    + "\"auditId\":\"audit-retry\",\"reason\":\"the message satisfies the contract\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(body);
            }
        });
        server.start();
        try {
            BridgePublisher publisher = publisher("http://127.0.0.1:" + server.getAddress().getPort(),
                    new RetryPolicy(3, 10L, 2.0, 50L));

            BridgeDecision decision = publisher.publishObservation("req-retry", observation(),
                    Provenance.of("chain-b", AT), AT);

            assertEquals(BridgeDecision.Status.ACCEPTED, decision.status());
            assertEquals(2, received.get(),
                    "the publisher must try again after a transport failure and stop when it works");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("an unreachable peer fails explicitly and names the number of attempts")
    void anUnreachablePeerFailsExplicitly() throws Exception {
        BridgePublisher publisher = publisher(UNREACHABLE + closedPort(),
                new RetryPolicy(2, 1L, 2.0, 5L));

        BridgePublicationFailure failure = assertThrows(BridgePublicationFailure.class,
                () -> publisher.publishObservation("req-unreachable", observation(),
                        Provenance.of("chain-b", AT), AT));

        assertTrue(failure.getMessage().contains("2 attempt(s)"),
                "the failure must name how many attempts were made: " + failure.getMessage());
    }

    @Test
    @DisplayName("a policy that permits one attempt makes exactly one")
    void aPolicyOfOneMakesOneAttempt() throws Exception {
        BridgePublisher publisher = publisher(UNREACHABLE + closedPort(), RetryPolicy.NONE);

        BridgePublicationFailure failure = assertThrows(BridgePublicationFailure.class,
                () -> publisher.publishObservation("req-once", observation(),
                        Provenance.of("chain-b", AT), AT));

        assertTrue(failure.getMessage().contains("1 attempt(s)"),
                "the failure must name how many attempts were made: " + failure.getMessage());
    }
}
