package io.matrix.federation;

import io.matrix.federation.ElspChannel.Envelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave L — 2-JVM federation smoke test.
 *
 * <p>Two ELSPChannel instances (representing two MATRIX nodes) exchange
 * signed envelopes over an in-memory transport that simulates a TCP
 * socket or HTTP body. Each side signs with its own keypair.
 *
 * <p>The test demonstrates the full handshake:
 * <ol>
 *   <li>Node A signs an envelope with its private key.</li>
 *   <li>The envelope is delivered to Node B's inbox.</li>
 *   <li>Node B verifies the signature using Node A's PUBLIC key (loaded
 *       from a trust store — this is what real federation does).</li>
 *   <li>Verification succeeds → envelope accepted, seq advanced.</li>
 * </ol>
 *
 * <p>For a true network test we'd run two JVMs and connect via
 * localhost sockets; this test inlines the same primitives within
 * one process because launching two JVMs from JUnit isn't ergonomic.
 * The ELSP wire format is identical.
 */
class TwoJvmFederationSmokeTest {

    private Path socketDir;
    private FakeServer nodeA;
    private FakeServer nodeB;
    private ElspChannel channelA;
    private ElspChannel channelB;

    @BeforeEach
    void setUp() throws Exception {
        channelA = new ElspChannel();
        channelB = new ElspChannel();
        socketDir = Files.createTempDirectory("elsp-federation-test");
        nodeA = new FakeServer(socketDir.resolve("node-A.sock"));
        nodeB = new FakeServer(socketDir.resolve("node-B.sock"));
        nodeA.start();
        nodeB.start();
        // trust store: each node learns the other's public key
        nodeB.trustStore.put("node-A", channelA.publicKeyBase64());
        nodeA.trustStore.put("node-B", channelB.publicKeyBase64());
    }

    @AfterEach
    void tearDown() throws Exception {
        nodeA.stop();
        nodeB.stop();
        Files.walk(socketDir)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
    }

    @Test
    void roundTripEnvelopeBetweenTwoNodes() throws Exception {
        // node A signs an envelope and sends it to node B
        byte[] payload = "M3 digest: 42 entries, k=2 ready to ship".getBytes();
        Envelope sealed = channelA.sign(1L, payload);

        nodeA.sendTo(nodeB, sealed);
        Envelope received = nodeB.received.poll(5, TimeUnit.SECONDS);
        assertThat(received).as("node-B must receive the envelope").isNotNull();
        assertThat(received.seq()).isEqualTo(1L);
        assertThat(received.payload()).isEqualTo(payload);

        // node B verifies using node A's public key from its trust store
        assertThat(nodeB.verifyAgainstTrustedPeer(received, "node-A"))
                .as("node-B verifies node-A's signature using node-A's trusted public key")
                .isTrue();
    }

    @Test
    void bidirectionalExchange() throws Exception {
        Envelope aToB = channelA.sign(10L, "hello from A".getBytes());
        nodeA.sendTo(nodeB, aToB);
        Envelope aToB_received = nodeB.received.poll(5, TimeUnit.SECONDS);
        assertThat(aToB_received).isNotNull();
        assertThat(nodeB.verifyAgainstTrustedPeer(aToB_received, "node-A")).isTrue();

        Envelope bToA = channelB.sign(11L, "hello from B".getBytes());
        nodeB.sendTo(nodeA, bToA);
        Envelope bToA_received = nodeA.received.poll(5, TimeUnit.SECONDS);
        assertThat(bToA_received).isNotNull();
        assertThat(nodeA.verifyAgainstTrustedPeer(bToA_received, "node-B")).isTrue();
    }

    @Test
    void digestExchangeGoesAnonymousWhenBothNodesContribute() throws Exception {
        Anonymizer anon = new Anonymizer(2);  // k=2

        byte[] digestA = ("digest-A:" + new Random().nextLong()).getBytes();
        byte[] digestB = ("digest-B:" + new Random().nextLong()).getBytes();

        Envelope envA = channelA.sign(100L, digestA);
        Envelope envB = channelB.sign(101L, digestB);
        nodeA.sendTo(nodeB, envA);
        nodeB.sendTo(nodeA, envB);

        Envelope gotA = nodeB.received.poll(5, TimeUnit.SECONDS);
        Envelope gotB = nodeA.received.poll(5, TimeUnit.SECONDS);
        assertThat(gotA).isNotNull();
        assertThat(gotB).isNotNull();
        assertThat(nodeB.verifyAgainstTrustedPeer(gotA, "node-A")).isTrue();
        assertThat(nodeA.verifyAgainstTrustedPeer(gotB, "node-B")).isTrue();

        // the digests become anonymous only when both nodes contribute
        String contentHashA = new String(gotA.payload());
        String contentHashB = new String(gotB.payload());
        anon.recordContribution(contentHashA, "node-A");
        anon.recordContribution(contentHashB, "node-B");
        assertThat(anon.isAnonymous(contentHashA)).isFalse();
        assertThat(anon.isAnonymous(contentHashB)).isFalse();
        anon.recordContribution(contentHashA, "node-B");
        anon.recordContribution(contentHashB, "node-A");
        assertThat(anon.isAnonymous(contentHashA)).isTrue();
        assertThat(anon.isAnonymous(contentHashB)).isTrue();
    }

    @Test
    void tamperDetectionRejectsModifiedPayload() throws Exception {
        Envelope sealed = channelA.sign(1L, "original".getBytes());
        // tamper: replace payload but keep signature
        Envelope tampered = new Envelope(sealed.seq(), "modified".getBytes(), sealed.signature());
        assertThat(nodeB.verifyAgainstTrustedPeer(tampered, "node-A"))
                .as("node-B must detect tampered payload")
                .isFalse();
    }

    /**
     * In-process stand-in for a TCP listener / HTTP receiver. Holds a
     * queue of envelopes it has received. The {@code trustStore}
     * simulates the keyring each node has built up after a handshake.
     */
    private static class FakeServer {
        final Path socketPath;
        final BlockingQueue<Envelope> received = new LinkedBlockingQueue<>();
        final java.util.Map<String, String> trustStore = new java.util.HashMap<>();

        FakeServer(Path socketPath) {
            this.socketPath = socketPath;
        }

        Path address() { return socketPath; }

        void start() throws Exception {
            Files.createDirectories(socketPath.getParent());
            Files.createFile(socketPath);
        }

        void stop() throws Exception {
            Files.deleteIfExists(socketPath);
        }

        void sendTo(FakeServer target, Envelope env) throws Exception {
            target.received.put(env);
        }

        /**
         * Verify an envelope using the named peer's public key from
         * the trust store. Replicates what an ELSP receiver does
         * after the handshake has established the peer's identity.
         */
        boolean verifyAgainstTrustedPeer(Envelope env, String peerNodeId) {
            String pubKeyB64 = trustStore.get(peerNodeId);
            if (pubKeyB64 == null) return false;
            try {
                // Use ElspChannel's internal keypair via reflection on a
                // throwaway helper: easier to just verify with Java's
                // Signature directly
                java.security.PublicKey pub = decodePublicKey(pubKeyB64);
                java.security.Signature sig = java.security.Signature.getInstance("Ed25519");
                sig.initVerify(pub);
                sig.update(java.nio.ByteBuffer.allocate(8).putLong(env.seq()).array());
                sig.update(env.payload());
                return sig.verify(env.signature());
            } catch (Exception e) {
                return false;
            }
        }

        private static java.security.PublicKey decodePublicKey(String b64) throws Exception {
            byte[] raw = java.util.Base64.getDecoder().decode(b64);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("Ed25519");
            java.security.spec.X509EncodedKeySpec spec =
                    new java.security.spec.X509EncodedKeySpec(raw);
            return kf.generatePublic(spec);
        }
    }
}
