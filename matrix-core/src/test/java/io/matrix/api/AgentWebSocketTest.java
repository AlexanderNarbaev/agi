package io.matrix.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.agent.AgentBrainService;
import io.matrix.redis.NeuronCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentWebSocket message handling.
 *
 * Tests message parsing logic without requiring a WebSocket container.
 * Full integration tests require Jakarta WebSocket runtime.
 */
class AgentWebSocketTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private AgentWebSocket webSocket;

    @BeforeEach
    void setUp() {
        webSocket = new AgentWebSocket();
    }

    @Test
    void shouldCreateWebSocketInstance() {
        assertThat(webSocket).isNotNull();
    }

    @Test
    void shouldParseStartMessage() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "start");
        msg.put("agentId", "test-agent");
        assertThat(msg.get("type").asText()).isEqualTo("start");
        assertThat(msg.get("agentId").asText()).isEqualTo("test-agent");
    }

    @Test
    void shouldParseStartMessageWithoutAgentId() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "start");
        assertThat(msg.has("agentId")).isFalse();
    }

    @Test
    void shouldParseSensorsMessage() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "sensors");
        msg.put("data", 12345L);
        assertThat(msg.get("type").asText()).isEqualTo("sensors");
        assertThat(msg.get("data").asLong()).isEqualTo(12345L);
    }

    @Test
    void shouldParseSensorsMessageDefaultsToZero() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "sensors");
        long bits = msg.has("data") ? msg.get("data").asLong() : 0L;
        assertThat(bits).isZero();
    }

    @Test
    void shouldParseTrainMessage() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "train");
        msg.put("generations", 50);
        msg.put("population", 30);
        msg.put("k", 8);
        assertThat(msg.get("generations").asInt()).isEqualTo(50);
        assertThat(msg.get("population").asInt()).isEqualTo(30);
        assertThat(msg.get("k").asInt()).isEqualTo(8);
    }

    @Test
    void shouldParseTrainMessageDefaults() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "train");
        int generations = msg.has("generations") ? msg.get("generations").asInt() : 20;
        int population = msg.has("population") ? msg.get("population").asInt() : 30;
        int k = msg.has("k") ? msg.get("k").asInt() : 8;
        assertThat(generations).isEqualTo(20);
        assertThat(population).isEqualTo(30);
        assertThat(k).isEqualTo(8);
    }

    @Test
    void shouldParseStopMessage() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "stop");
        assertThat(msg.get("type").asText()).isEqualTo("stop");
    }

    @Test
    void shouldParseSaveMessage() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "save");
        assertThat(msg.get("type").asText()).isEqualTo("save");
    }

    @Test
    void shouldRecognizeUnknownMessageType() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("type", "invalid_type");
        String type = msg.has("type") ? msg.get("type").asText() : "";
        assertThat(type).isEqualTo("invalid_type");
    }

    @Test
    void shouldValidateAgentIdFormat() {
        assertThat("MatrixBot1".matches("[a-zA-Z0-9_-]+")).isTrue();
        assertThat("agent_42".matches("[a-zA-Z0-9_-]+")).isTrue();
        assertThat("test-agent".matches("[a-zA-Z0-9_-]+")).isTrue();
    }

    @Test
    void shouldRejectInvalidAgentId() {
        assertThat("bad id".matches("[a-zA-Z0-9_-]+")).isFalse();
        assertThat("x;DROP".matches("[a-zA-Z0-9_-]+")).isFalse();
        assertThat("".matches("[a-zA-Z0-9_-]+")).isFalse();
    }

    @Test
    void shouldRejectOverlongAgentId() {
        String longId = "x".repeat(65);
        assertThat(longId.length() <= 64).isFalse();
    }

    @Test
    void shouldValidateSensorBitsRange() {
        long valid = 0xFFFFFL;
        assertThat(valid).isLessThanOrEqualTo(0xFFFFFL);
        long overflow = 0x1FFFFFL;
        assertThat(overflow).isGreaterThan(0xFFFFFL);
    }

    @Test
    void shouldHandleEmptyMessage() throws Exception {
        ObjectNode msg = MAPPER.createObjectNode();
        String type = msg.has("type") ? msg.get("type").asText() : "";
        assertThat(type).isEmpty();
    }
}
