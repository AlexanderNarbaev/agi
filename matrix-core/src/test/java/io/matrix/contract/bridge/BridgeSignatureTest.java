package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.matrix.contract.bridge.BridgeTestFixtures.OTHER_SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.SENT_AT;
import static io.matrix.contract.bridge.BridgeTestFixtures.SENDER_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — BRIDGE-3: every call is authenticated, or it is refused.
 *
 * <p>Three things are proved here, and each of them is a way the bridge could have been
 * wrong: that a good signature verifies, that a signature over a <i>different</i> payload
 * does not, and that a signature made with a different secret does not. The second is the
 * one that matters most: a signature that did not cover the payload would authenticate the
 * envelope and leave its content open to anyone who could rewrite it.</p>
 */
class BridgeSignatureTest {

    @Test
    @DisplayName("a signature made with the shared secret verifies")
    void goodSignatureVerifies() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();

        assertTrue(signed.verify(SECRET));
        assertTrue(signed.verify(new HmacBridgeSigner(SECRET)));
    }

    @Test
    @DisplayName("a signature over a changed payload does not verify")
    void changedPayloadDoesNotVerify() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();
        String tamperedPayload = signed.payload().replace("the lamp is lit", "the lamp is dark");
        assertNotEquals(signed.payload(), tamperedPayload,
                "the fixture payload must actually change, or this test proves nothing");

        BridgeEnvelope tampered = new BridgeEnvelope(signed.protocol(), signed.version(),
                signed.requestId(), signed.sentAt(), signed.senderId(), signed.kind(),
                tamperedPayload, signed.signature());

        assertFalse(tampered.verify(SECRET));
    }

    @Test
    @DisplayName("a signature made with another secret does not verify")
    void anotherSecretDoesNotVerify() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();

        assertFalse(signed.verify(OTHER_SECRET));
        assertFalse(signed.verify(new HmacBridgeSigner(OTHER_SECRET)));
    }

    @Test
    @DisplayName("an envelope with no signature does not verify")
    void unsignedDoesNotVerify() {
        assertFalse(envelope().verify(SECRET));
    }

    @Test
    @DisplayName("a signature that is not hex does not verify")
    void malformedSignatureDoesNotVerify() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();
        BridgeEnvelope malformed = signed.withSignature("not a hex signature at all");

        assertFalse(malformed.verify(SECRET));
    }

    @Test
    @DisplayName("the signed bytes cover the protocol and the version")
    void signatureCoversTheProtocolAndVersion() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();
        BridgeEnvelope otherProtocol = new BridgeEnvelope("matrix.other", signed.version(),
                signed.requestId(), signed.sentAt(), signed.senderId(), signed.kind(),
                signed.payload(), signed.signature());

        assertFalse(otherProtocol.verify(SECRET));
        assertFalse(new BridgeEnvelope(signed.protocol(), signed.version() + 1, signed.requestId(),
                signed.sentAt(), signed.senderId(), signed.kind(), signed.payload(),
                signed.signature()).verify(SECRET));
    }

    @Test
    @DisplayName("a signer cannot be built without a secret")
    void aSignerNeedsASecret() {
        assertThrows(IllegalArgumentException.class, () -> new HmacBridgeSigner((byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> new HmacBridgeSigner(new byte[0]));
    }

    @Test
    @DisplayName("the payload and the identifiers are what the signature covers")
    void theSignatureCoversEveryField() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();

        assertFalse(new BridgeEnvelope(signed.protocol(), signed.version(), "req-other",
                signed.sentAt(), signed.senderId(), signed.kind(), signed.payload(),
                signed.signature()).verify(SECRET));
        assertFalse(new BridgeEnvelope(signed.protocol(), signed.version(), signed.requestId(),
                SENT_AT + "+1", signed.senderId(), signed.kind(), signed.payload(),
                signed.signature()).verify(SECRET));
        assertFalse(new BridgeEnvelope(signed.protocol(), signed.version(), signed.requestId(),
                signed.sentAt(), "chain-b-something-else", signed.kind(), signed.payload(),
                signed.signature()).verify(SECRET));
    }

    @Test
    @DisplayName("the request id is part of the signed bytes")
    void theRequestIdIsSigned() {
        BridgeEnvelope signed = BridgeTestFixtures.signedEnvelope();

        assertFalse(new BridgeEnvelope(signed.protocol(), signed.version(), REQUEST_ID + "-x",
                signed.sentAt(), signed.senderId(), signed.kind(), signed.payload(),
                signed.signature()).verify(SECRET));
    }

    @Test
    @DisplayName("the payload the signature was made over is the one the codec produced")
    void theFixtureCarriesTheCanonicalPayload() {
        BridgeEnvelope envelope = envelope(payload(), REQUEST_ID);

        assertTrue(envelope.payload().startsWith("{"));
        assertTrue(envelope.payload().contains("\"kind\":\"OBSERVATION\""));
    }
}
