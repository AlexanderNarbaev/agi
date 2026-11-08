package io.matrix.contract.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

/**
 * W51 — the wire form of a bridge envelope.
 *
 * <p><b>Why the envelope needs its own codec.</b> The payload is JSON, and the envelope
 * carries the payload as a JSON <i>string</i> — the exact bytes that the signature is
 * computed over. If the envelope were assembled by string concatenation, a payload containing
 * a quote or a newline would either break the document or change the bytes the signature
 * covers. The codec therefore builds the document with a JSON tree and reads it back the
 * same way, so the payload's bytes survive the round trip unchanged and the signature stays
 * verifiable.</p>
 *
 * <p><b>The canonical bytes are not the wire bytes.</b> {@link BridgeEnvelope#canonicalBytes()}
 * is what the signature covers; the wire form is only a carrier. That separation is what lets
 * the transport change without invalidating a signature, and it is why this class never
 * re-serialises the payload itself — it copies the string it was given.</p>
 *
 * <p><b>The public surface names no Jackson type.</b> Chain B may use this codec without
 * depending on Jackson's API, exactly as with {@link BridgePayloadCodec}.</p>
 */
public final class BridgeEnvelopeCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Encodes an envelope.
     *
     * @param envelope the envelope
     * @return the JSON document
     */
    public String encode(BridgeEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope must not be null");
        }
        ObjectNode node = MAPPER.createObjectNode();
        node.put("protocol", envelope.protocol());
        node.put("version", envelope.version());
        node.put("requestId", envelope.requestId());
        node.put("sentAt", envelope.sentAt());
        node.put("senderId", envelope.senderId());
        node.put("kind", envelope.kind().name());
        node.put("payload", envelope.payload());
        if (envelope.signature() == null) {
            node.putNull("signature");
        } else {
            node.put("signature", envelope.signature());
        }
        return node.toString();
    }

    /**
     * Decodes an envelope.
     *
     * @param json the JSON document
     * @return the envelope
     * @throws BridgeProtocolException when the document is not an envelope this reader speaks
     */
    public BridgeEnvelope decode(String json) {
        if (json == null || json.isBlank()) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the envelope is empty");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the envelope is not valid JSON: " + e.getMessage());
        }
        if (node == null || !node.isObject()) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the envelope is not a JSON object");
        }
        String kindName = text(node, "kind");
        Optional<BridgeCapability> kind = BridgeCapability.parse(kindName);
        if (kind.isEmpty()) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the envelope names the unknown kind " + kindName);
        }
        JsonNode signature = node.get("signature");
        return new BridgeEnvelope(
                text(node, "protocol"),
                node.path("version").asInt(),
                text(node, "requestId"),
                text(node, "sentAt"),
                text(node, "senderId"),
                kind.get(),
                node.path("payload").asText(""),
                signature == null || signature.isNull() ? null : signature.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new BridgeProtocolException(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "the envelope has no textual field " + field);
        }
        return value.asText();
    }
}
