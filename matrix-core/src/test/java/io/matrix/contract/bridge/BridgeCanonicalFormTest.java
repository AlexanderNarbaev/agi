package io.matrix.contract.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.matrix.contract.bridge.BridgeTestFixtures.REQUEST_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.SENT_AT;
import static io.matrix.contract.bridge.BridgeTestFixtures.SENDER_ID;
import static io.matrix.contract.bridge.BridgeTestFixtures.envelope;
import static io.matrix.contract.bridge.BridgeTestFixtures.payload;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the canonical bytes of a message are deterministic.
 *
 * <p>The signature is over bytes, so both sides must produce the same bytes for the same
 * message. A canonical form that was only <i>usually</i> the same would be a bridge that
 * refuses messages at random, and the refusal would look like a forgery. The tests here
 * prove that the same content always yields the same bytes, that every field is covered, and
 * that the form refuses the one thing that could make it ambiguous: a field that spans
 * lines.</p>
 */
class BridgeCanonicalFormTest {

    @Test
    @DisplayName("the same message always produces the same bytes")
    void theSameMessageProducesTheSameBytes() {
        BridgeEnvelope first = envelope(payload(), REQUEST_ID);
        BridgeEnvelope second = envelope(payload(), REQUEST_ID);

        assertArrayEquals(first.canonicalBytes(), second.canonicalBytes());
        assertEquals(new String(first.canonicalBytes(), StandardCharsets.UTF_8),
                new String(second.canonicalBytes(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("the canonical form carries every field, in a fixed order")
    void theCanonicalFormCarriesEveryField() {
        BridgeEnvelope envelope = envelope(payload(), REQUEST_ID);

        String canonical = new String(envelope.canonicalBytes(), StandardCharsets.UTF_8);

        assertEquals(BridgeProtocol.NAME, canonical.lines().toList().get(0));
        assertEquals(String.valueOf(BridgeProtocol.VERSION), canonical.lines().toList().get(1));
        assertEquals(REQUEST_ID, canonical.lines().toList().get(2));
        assertEquals(SENT_AT, canonical.lines().toList().get(3));
        assertEquals(SENDER_ID, canonical.lines().toList().get(4));
        assertEquals(BridgeCapability.OBSERVATION.name(), canonical.lines().toList().get(5));
        assertTrue(canonical.endsWith(envelope.payload()));
    }

    @Test
    @DisplayName("changing any field changes the bytes")
    void everyFieldIsCovered() {
        BridgeEnvelope base = envelope(payload(), REQUEST_ID);
        byte[] canonical = base.canonicalBytes();

        assertFalse(java.util.Arrays.equals(canonical,
                new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION, "req-other",
                        SENT_AT, SENDER_ID, base.kind(), base.payload(), null).canonicalBytes()));
        assertFalse(java.util.Arrays.equals(canonical,
                new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION, REQUEST_ID,
                        "2026-11-08T10:00:01Z", SENDER_ID, base.kind(), base.payload(), null)
                        .canonicalBytes()));
        assertFalse(java.util.Arrays.equals(canonical,
                new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION, REQUEST_ID,
                        SENT_AT, "another-sender", base.kind(), base.payload(), null)
                        .canonicalBytes()));
        assertFalse(java.util.Arrays.equals(canonical,
                new BridgeEnvelope(BridgeProtocol.NAME, BridgeProtocol.VERSION + 1, REQUEST_ID,
                        SENT_AT, SENDER_ID, base.kind(), base.payload(), null).canonicalBytes()));
    }

    @Test
    @DisplayName("the signature is not part of the bytes it signs")
    void theSignatureIsNotPartOfItsOwnInput() {
        BridgeEnvelope unsigned = envelope(payload(), REQUEST_ID);
        BridgeEnvelope signed = unsigned.withSignature(unsigned.sign(BridgeTestFixtures.SECRET));

        assertArrayEquals(unsigned.canonicalBytes(), signed.canonicalBytes());
        assertNotEquals(unsigned.signature(), signed.signature());
    }

    @Test
    @DisplayName("an identifier that spans lines is refused at construction")
    void anIdentifierThatSpansLinesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new BridgeEnvelope(
                BridgeProtocol.NAME, BridgeProtocol.VERSION, "req\nsecond-line", SENT_AT,
                SENDER_ID, BridgeCapability.OBSERVATION, "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEnvelope(
                BridgeProtocol.NAME, BridgeProtocol.VERSION, REQUEST_ID, "now\nlater", SENDER_ID,
                BridgeCapability.OBSERVATION, "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEnvelope(
                BridgeProtocol.NAME, BridgeProtocol.VERSION, REQUEST_ID, SENT_AT, "sender\rname",
                BridgeCapability.OBSERVATION, "{}", null));
    }

    @Test
    @DisplayName("the fields the canonical form needs are not blank")
    void theFieldsAreNotBlank() {
        assertThrows(IllegalArgumentException.class, () -> new BridgeEnvelope(
                BridgeProtocol.NAME, BridgeProtocol.VERSION, "  ", SENT_AT, SENDER_ID,
                BridgeCapability.OBSERVATION, "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEnvelope(
                BridgeProtocol.NAME, BridgeProtocol.VERSION, REQUEST_ID, SENT_AT, SENDER_ID,
                null, "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new BridgeEnvelope(
                BridgeProtocol.NAME, BridgeProtocol.VERSION, REQUEST_ID, SENT_AT, SENDER_ID,
                BridgeCapability.OBSERVATION, null, null));
    }

    @Test
    @DisplayName("the payload digest identifies the payload and changes with it")
    void theDigestIdentifiesThePayload() {
        BridgeEnvelope base = envelope(payload(), REQUEST_ID);

        assertEquals(base.payloadDigest(), envelope(payload(), REQUEST_ID).payloadDigest());
        assertNotEquals(base.payloadDigest(),
                envelope(BridgeTestFixtures.multiKeyPayload(), REQUEST_ID).payloadDigest());
        assertEquals(64, base.payloadDigest().length(),
                "a SHA-256 digest is sixty-four hex characters");
    }
}
