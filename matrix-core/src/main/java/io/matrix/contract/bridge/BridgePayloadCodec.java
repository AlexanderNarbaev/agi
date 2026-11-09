package io.matrix.contract.bridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Optional;

/**
 * W51 — the JSON form of a bridge payload (BRIDGE-2, BRIDGE-5).
 *
 * <p><b>Why the JSON must be canonical.</b> The payload string is part of the message the
 * signature covers: the sender signs the canonical bytes of the envelope, and the envelope
 * carries the payload as a string. If two encodings of the same payload could differ, the
 * sender and the receiver would sign and verify different bytes and every message would be
 * refused — so the encoding has to be one, not merely <i>a</i> JSON encoding. The one property
 * that makes it unsafe to skip is the map order: {@link Observation} and {@link Belief} carry
 * {@code Map<String, Object>} fields, and the JDK's immutable-map copy does not promise an
 * iteration order, so a payload built twice from equal content can serialise its keys in a
 * different order. This codec therefore sorts map keys explicitly, and does not rely on the
 * default.</p>
 *
 * <p><b>Why a reader refuses what it does not know (BRIDGE-2).</b> Decoding fails on a field
 * the reader has never heard of. A change that adds a field is a change of the protocol, and a
 * change of the protocol is a new version; a reader that silently ignored the unknown field
 * would be accepting a message of a version it does not speak, which is the silent change of
 * meaning that BRIDGE-2 forbids.</p>
 *
 * <p><b>Why Jackson and not a hand-written writer.</b> The repository already depends on
 * Jackson (through {@code quarkus-rest-jackson} in {@code matrix-core}, and explicitly in
 * {@code matrix-brain-runtime}), and the canonical contracts in {@code io.matrix.contract} are
 * records that Jackson reads natively. A hand-written mapper for three record types would be
 * a second, divergent definition of the same wire format — the class of defect this wave has
 * been finding all along. No new dependency is declared for it.</p>
 *
 * <p><b>The public surface names no Jackson type.</b> Chain B may use this codec without
 * Jackson on its compile classpath: the only types that cross the boundary are
 * {@link BridgePayload} and {@link String}.</p>
 */
public final class BridgePayloadCodec {

    /**
     * The mapper both sides use.
     *
     * <p>{@code ORDER_MAP_ENTRIES_BY_KEYS} is the reason the encoding is canonical; the
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} is the reason an unknown field is a refusal rather
     * than a loss. A mapper is thread-safe once configured, so one shared instance serves
     * every caller and there is no per-call configuration to get wrong.</p>
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /**
     * The canonical JSON of a payload.
     *
     * @param payload the payload
     * @return the JSON, with the map keys of every nested map sorted
     * @throws BridgeProtocolException when the payload cannot be written
     */
    public String encode(BridgePayload payload) {
        if (payload == null) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "there is no payload to encode");
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the payload cannot be written as JSON", e);
        }
    }

    /**
     * The payload a canonical JSON string carries.
     *
     * <p>The kind is read first and on its own, so that a payload naming a kind the protocol
     * does not define is refused as an unknown kind rather than as a malformed document: the
     * two are different facts and the audit record has to tell them apart (BRIDGE-6).</p>
     *
     * @param json the canonical JSON
     * @return the payload
     * @throws BridgeProtocolException when the string is not a payload this reader can read
     */
    public BridgePayload decode(String json) {
        if (json == null || json.isBlank()) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the payload is empty");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the payload is not JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the payload is not a JSON object");
        }
        JsonNode kindNode = root.get("kind");
        if (kindNode == null || !kindNode.isTextual()) {
            throw new BridgeProtocolException(BridgeRefusalReason.UNKNOWN_PAYLOAD_KIND,
                    "the payload does not name a kind");
        }
        Optional<BridgeCapability> kind = BridgeCapability.parse(kindNode.asText());
        if (kind.isEmpty()) {
            throw new BridgeProtocolException(BridgeRefusalReason.UNKNOWN_PAYLOAD_KIND,
                    "the payload names the kind " + kindNode.asText() + ", which the protocol does not define");
        }
        try {
            return MAPPER.readValue(json, BridgePayload.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the payload does not satisfy the contract it claims", e);
        }
    }
}
