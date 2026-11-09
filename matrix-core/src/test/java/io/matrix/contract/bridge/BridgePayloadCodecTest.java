package io.matrix.contract.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.BeliefStatus;
import io.matrix.contract.Confidence;
import io.matrix.contract.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.matrix.contract.bridge.BridgeTestFixtures.SENT_AT;
import static io.matrix.contract.bridge.BridgeTestFixtures.provenance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W51 — the payload survives the JSON round trip, and the JSON is canonical.
 *
 * <p>Two properties are proved. The first is the round trip: what a chain B client writes,
 * a chain A reader gets back, field for field. The second is canonicity: the same content
 * always produces the same string, with the map keys in a fixed order. The second is what
 * makes the signature work across two processes — without it, the same message would
 * produce different bytes on the sender and on the receiver, and every call would be
 * refused as a forgery.</p>
 */
class BridgePayloadCodecTest {

    private final BridgePayloadCodec codec = new BridgePayloadCodec();

    @Test
    @DisplayName("an observation payload survives the round trip")
    void anObservationSurvivesTheRoundTrip() {
        BridgePayload payload = BridgeTestFixtures.payload();

        BridgePayload decoded = codec.decode(codec.encode(payload));

        assertEquals(payload.kind(), decoded.kind());
        assertEquals(payload.observation(), decoded.observation());
        assertEquals(payload.provenance(), decoded.provenance());
        assertEquals(payload.candidate(), decoded.candidate());
        assertEquals(payload.evidenceLink(), decoded.evidenceLink());
    }

    @Test
    @DisplayName("a candidate payload survives the round trip")
    void aCandidateSurvivesTheRoundTrip() {
        Belief candidate = new Belief("belief-1", "the lamp is lit", BeliefStatus.CANDIDATE,
                Confidence.declared(0.4, List.of("heard from the sensor")), List.of(),
                List.of(), provenance(), null, 1, null, null, "the sensor said so");
        BridgePayload payload = BridgePayload.ofCandidate(candidate, provenance());

        BridgePayload decoded = codec.decode(codec.encode(payload));

        assertEquals(BridgeCapability.CANDIDATE, decoded.kind());
        assertEquals(candidate, decoded.candidate());
        assertEquals(payload.provenance(), decoded.provenance());
    }

    @Test
    @DisplayName("an evidence link survives the round trip")
    void anEvidenceLinkSurvivesTheRoundTrip() {
        Evidence evidence = new Evidence("evidence-1", "subject-1", "source-1",
                Evidence.Relation.SUPPORTS, "the lamp is lit", 80, SENT_AT, provenance());
        BridgePayload payload = BridgePayload.ofEvidenceLink(evidence, provenance());

        BridgePayload decoded = codec.decode(codec.encode(payload));

        assertEquals(BridgeCapability.EVIDENCE_LINK, decoded.kind());
        assertEquals(evidence, decoded.evidenceLink());
    }

    @Test
    @DisplayName("the encoding is canonical: the same content gives the same string")
    void theEncodingIsCanonical() {
        BridgePayload payload = BridgeTestFixtures.multiKeyPayload();

        String first = codec.encode(payload);
        String second = codec.encode(BridgeTestFixtures.multiKeyPayload());

        assertEquals(first, second);
        assertTrue(first.indexOf("\"alpha\"") < first.indexOf("\"mu\""),
                "the map keys must be in a sorted order, not in the order of the map");
        assertTrue(first.indexOf("\"mu\"") < first.indexOf("\"zeta\""));
    }

    @Test
    @DisplayName("different content gives a different string")
    void differentContentGivesADifferentString() {
        assertNotEquals(codec.encode(BridgeTestFixtures.payload()),
                codec.encode(BridgeTestFixtures.multiKeyPayload()));
    }

    @Test
    @DisplayName("a payload that is not JSON is refused")
    void aPayloadThatIsNotJsonIsRefused() {
        BridgeProtocolException failure = assertThrows(BridgeProtocolException.class,
                () -> codec.decode("this is not json"));

        assertEquals(BridgeRefusalReason.MALFORMED_MESSAGE, failure.reason());
    }

    @Test
    @DisplayName("an empty payload is refused")
    void anEmptyPayloadIsRefused() {
        assertThrows(BridgeProtocolException.class, () -> codec.decode(""));
        assertThrows(BridgeProtocolException.class, () -> codec.decode(null));
    }

    @Test
    @DisplayName("a payload with a field the reader does not know is refused")
    void anUnknownFieldIsRefused() {
        String json = codec.encode(BridgeTestFixtures.payload());
        String withUnknownField = json.substring(0, json.length() - 1) + ",\"unknown\":1}";

        BridgeProtocolException failure =
                assertThrows(BridgeProtocolException.class, () -> codec.decode(withUnknownField));

        assertEquals(BridgeRefusalReason.MALFORMED_MESSAGE, failure.reason());
    }

    @Test
    @DisplayName("a payload that names no kind is refused")
    void aPayloadThatNamesNoKindIsRefused() {
        BridgeProtocolException failure = assertThrows(BridgeProtocolException.class,
                () -> codec.decode("{\"observation\":null}"));

        assertEquals(BridgeRefusalReason.UNKNOWN_PAYLOAD_KIND, failure.reason());
    }
}
