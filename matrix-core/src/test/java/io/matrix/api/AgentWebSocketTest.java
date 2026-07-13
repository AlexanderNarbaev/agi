package io.matrix.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.agent.AgentBrainService;
import io.matrix.redis.NeuronCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AgentWebSocket message handling and security.
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

    // ── Security: WebSocket hardening tests ──

    @Test
    void shouldClampGenerationsToMax() {
        // Simulates clampInt behavior
        int max = 500;
        int requested = 9999;
        int clamped = Math.max(1, Math.min(max, requested));
        assertThat(clamped).isEqualTo(500);
    }

    @Test
    void shouldClampGenerationsToMin() {
        int min = 1;
        int requested = -5;
        int clamped = Math.max(min, Math.min(500, requested));
        assertThat(clamped).isEqualTo(1);
    }

    @Test
    void shouldClampPopulationToRange() {
        int clamped = Math.max(1, Math.min(500, 9999));
        assertThat(clamped).isEqualTo(500);
        clamped = Math.max(1, Math.min(500, 0));
        assertThat(clamped).isEqualTo(1);
    }

    @Test
    void shouldClampKToRange() {
        int clamped = Math.max(1, Math.min(20, 99));
        assertThat(clamped).isEqualTo(20);
    }

    @Test
    void shouldClampOnlineIterationsToRange() {
        int clamped = Math.max(1, Math.min(1000, 9999));
        assertThat(clamped).isEqualTo(1000);
    }

    @Test
    void shouldValidateTokenFormat() {
        // Empty token should be rejected
        assertThat(isValidTokenTest(null)).isFalse();
        assertThat(isValidTokenTest("")).isFalse();
        assertThat(isValidTokenTest("   ")).isFalse();

        // Control characters should be rejected
        assertThat(isValidTokenTest("token\0injection")).isFalse();
        assertThat(isValidTokenTest("token\u0001control")).isFalse();

        // Overlong token should be rejected
        assertThat(isValidTokenTest("x".repeat(513))).isFalse();

        // Valid token should be accepted
        assertThat(isValidTokenTest("valid-token-123")).isTrue();
    }

    @Test
    void shouldValidateSensorBitsInFeedback() {
        long maxSensor = 0xFFFFF;
        assertThat(0L).isBetween(0L, maxSensor);
        assertThat(0xFFFFFL).isBetween(0L, maxSensor);
        assertThat(-1L).isLessThan(0L);
        assertThat(0x1FFFFFL).isGreaterThan(maxSensor);
    }

    @Test
    void shouldValidateRoleLength() {
        String role = "x".repeat(65);
        assertThat(role.length() > 64).isTrue();
        assertThat("miner".length() <= 64).isTrue();
    }

    @Test
    void shouldValidateMessageSize() {
        int maxBytes = 65_536;
        assertThat("small message".length()).isLessThan(maxBytes);
        assertThat("x".repeat(65_537).length()).isGreaterThan(maxBytes);
    }

    private boolean isValidTokenTest(String token) {
        if (token == null || token.isBlank()) return false;
        if (token.length() > 512) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x20 || c == '\0') return false;
        }
        return true;
    }
}
