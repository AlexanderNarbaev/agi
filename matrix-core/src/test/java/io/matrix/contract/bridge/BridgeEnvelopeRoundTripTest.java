package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.SECRET;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static io.matrix.contract.bridge.BridgeTestFixtures.signedEnvelope;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the envelope codec and the payload codec agree, byte for byte.
 *
 * <p><b>The property this file protects.</b> The signature is computed over the canonical
 * bytes of the envelope, and the envelope carries the payload as a <i>string</i> — the exact
 * bytes that were signed. If the transport changed those bytes in any way, the signature
 * would stop verifying and every message would be refused as a forgery. So the payload's
 * bytes have to survive the envelope's JSON round trip unchanged, and the signature has to
 * verify on the far side.</p>
 *
 * <p><b>Why the payload is a string and not a nested object.</b> A nested object would have
 * to be re-serialised by whoever reads it, and re-serialising is where the bytes change: a
 * map's iteration order, a number's spelling, an escaped character. The envelope copies the
 * string it was given, and the payload codec is the only thing that ever parses it. That is
 * why there are two codecs and why the second one knows nothing about the first one's
 * format.</p>
 */
class BridgeEnvelopeRoundTripTest {

    private final BridgeEnvelopeCodec envelopeCodec = new BridgeEnvelopeCodec();
    private final BridgePayloadCodec payloadCodec = new BridgePayloadCodec();

    @Test
    @DisplayName("the payload's bytes survive the envelope round trip unchanged")
    void thePayloadBytesSurviveTheRoundTrip() {
        BridgeEnvelope original = signedEnvelope();

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));

        assertEquals(original.payload(), decoded.payload(),
                "the payload must come back as the very same string, byte for byte");
        assertArrayEquals(original.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                decoded.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the signature still verifies after the envelope round trip")
    void theSignatureStillVerifiesAfterTheRoundTrip() {
        BridgeEnvelope original = signedEnvelope();

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));

        assertTrue(decoded.verify(SECRET),
                "a message that survives the transport must still be authentic");
        assertArrayEquals(original.canonicalBytes(), decoded.canonicalBytes());
    }

    @Test
    @DisplayName("the payload of a round-tripped envelope still decodes")
    void thePayloadOfARoundTrippedEnvelopeStillDecodes() {
        BridgeEnvelope original = signedEnvelope();

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));
        BridgePayload payload = payloadCodec.decode(decoded.payload());

        assertEquals(BridgeCapability.OBSERVATION, payload.kind());
        assertEquals(BridgeTestFixtures.observation(), payload.observation());
    }

    @Test
    @DisplayName("every field survives the round trip")
    void everyFieldSurvivesTheRoundTrip() {
        BridgeEnvelope original = signedEnvelope();

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));

        assertEquals(original.protocol(), decoded.protocol());
        assertEquals(original.version(), decoded.version());
        assertEquals(original.requestId(), decoded.requestId());
        assertEquals(original.sentAt(), decoded.sentAt());
        assertEquals(original.senderId(), decoded.senderId());
        assertEquals(original.kind(), decoded.kind());
        assertEquals(original.signature(), decoded.signature());
        assertEquals(REQUEST_ID, decoded.requestId());
    }

    @Test
    @DisplayName("an unsigned envelope round-trips with no signature")
    void anUnsignedEnvelopeRoundTrips() {
        BridgeEnvelope original = envelope(payload(), REQUEST_ID);

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));

        assertEquals(original.signature(), decoded.signature());
        assertEquals(original.payload(), decoded.payload());
    }

    @Test
    @DisplayName("a payload with characters that JSON must escape survives the round trip")
    void anAwkwardPayloadSurvivesTheRoundTrip() {
        String awkward = "{\"text\":\"a quote \\\" a backslash \\\\ a newline \\n a tab \\t\"}";
        BridgeEnvelope original = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, BridgeTestFixtures.SENT_AT, BridgeTestFixtures.SENDER_ID,
                BridgeCapability.OBSERVATION, awkward, null);

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));

        assertEquals(awkward, decoded.payload());
    }

    @Test
    @DisplayName("a payload with characters outside ASCII survives the round trip")
    void aNonAsciiPayloadSurvivesTheRoundTrip() {
        String nonAscii = "{\"text\":\"свет лампы — 100%\"}";
        BridgeEnvelope original = new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION,
                REQUEST_ID, BridgeTestFixtures.SENT_AT, BridgeTestFixtures.SENDER_ID,
                BridgeCapability.OBSERVATION, nonAscii, null);

        BridgeEnvelope decoded = envelopeCodec.decode(envelopeCodec.encode(original));

        assertEquals(nonAscii, decoded.payload());
    }
}
