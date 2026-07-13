package io.matrix.agent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.agent.AgentAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record of a single reasoning step in the ReAct cycle.
 *
 * <p>Each trace captures the full Observe → Reason → Act → Observe Result → Reflect
 * cycle as structured data, enabling post-hoc analysis and episodic memory storage.
 *
 * <p>Ref: ReAct (Yao et al., 2022) — Synergizing Reasoning and Acting in Language Models
 */
public final class ReasoningTrace {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final long tick;
    private final long observation;
    private final String thought;
    private final AgentAction action;
    private final String actionResult;
    private final boolean actionSuccess;
    private final String reflection;
    private final long timestampMs;
    private final List<String> reasoningChain;

    private ReasoningTrace(Builder builder) {
        this.tick = builder.tick;
        this.observation = builder.observation;
        this.thought = Objects.requireNonNull(builder.thought, "thought must not be null");
        this.action = builder.action;
        this.actionResult = builder.actionResult != null ? builder.actionResult : "";
        this.actionSuccess = builder.actionSuccess;
        this.reflection = builder.reflection != null ? builder.reflection : "";
        this.timestampMs = builder.timestampMs > 0 ? builder.timestampMs : System.currentTimeMillis();
        this.reasoningChain = List.copyOf(builder.reasoningChain);
    }

    // ── Accessors ──

    /** Tick number when this trace was recorded. */
    public long tick() { return tick; }

    /** Raw sensor observation bits. */
    public long observation() { return observation; }

    /** The reasoning thought produced before action selection. */
    public String thought() { return thought; }

    /** The action selected based on reasoning. May be null for observe-only traces. */
    public AgentAction action() { return action; }

    /** Textual description of the action result. */
    public String actionResult() { return actionResult; }

    /** Whether the action succeeded. */
    public boolean actionSuccess() { return actionSuccess; }

    /** Post-action reflection on outcome. */
    public String reflection() { return reflection; }

    /** Wall-clock timestamp in milliseconds. */
    public long timestampMs() { return timestampMs; }

    /** Immutable chain of intermediate reasoning steps. */
    public List<String> reasoningChain() { return reasoningChain; }

    /**
     * Returns true if this trace represents a complete ReAct cycle
     * (has thought, action, and reflection).
     */
    public boolean isComplete() {
        return thought != null && !thought.isEmpty()
                && action != null
                && reflection != null && !reflection.isEmpty();
    }

    /**
     * Serializes this trace to JSON for persistence and analysis.
     */
    public String toJson() {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("tick", tick);
            root.put("observation", Long.toHexString(observation));
            root.put("thought", thought);
            root.put("actionType", action != null ? action.type().name() : "NONE");
            root.put("actionResult", actionResult);
            root.put("actionSuccess", actionSuccess);
            root.put("reflection", reflection);
            root.put("timestampMs", timestampMs);
            root.put("complete", isComplete());

            ArrayNode chainNode = root.putArray("reasoningChain");
            for (String step : reasoningChain) {
                chainNode.add(step);
            }

            if (action != null && !action.parameters().isEmpty()) {
                ObjectNode paramsNode = root.putObject("actionParams");
                action.parameters().forEach((k, v) -> paramsNode.put(k, String.valueOf(v)));
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Deserializes a trace from JSON. Action reconstruction is best-effort
     * (only type is restored, parameters are lost).
     */
    public static ReasoningTrace fromJson(String json) {
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(json);
            Builder builder = new Builder()
                    .tick(root.get("tick").asLong())
                    .observation(parseHex(root.get("observation").asText()))
                    .thought(root.get("thought").asText())
                    .actionResult(root.get("actionResult").asText())
                    .actionSuccess(root.get("actionSuccess").asBoolean())
                    .reflection(root.get("reflection").asText())
                    .timestampMs(root.get("timestampMs").asLong());

            String actionTypeStr = root.get("actionType").asText();
            if (!"NONE".equals(actionTypeStr)) {
                builder.action(new AgentAction(AgentAction.ActionType.valueOf(actionTypeStr)));
            }

            if (root.has("reasoningChain")) {
                ArrayNode chainNode = (ArrayNode) root.get("reasoningChain");
                for (var node : chainNode) {
                    builder.addReasoningStep(node.asText());
                }
            }

            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse ReasoningTrace JSON", e);
        }
    }

    private static long parseHex(String hex) {
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            return Long.parseLong(hex.substring(2), 16);
        }
        return Long.parseLong(hex, 16);
    }

    @Override
    public String toString() {
        return "ReasoningTrace{tick=" + tick
                + ", thought='" + truncate(thought, 40) + "'"
                + ", action=" + (action != null ? action.type() : "NONE")
                + ", success=" + actionSuccess
                + ", reflection='" + truncate(reflection, 40) + "'"
                + "}";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ── Builder ──

    public static final class Builder {
        private long tick;
        private long observation;
        private String thought = "";
        private AgentAction action;
        private String actionResult = "";
        private boolean actionSuccess;
        private String reflection = "";
        private long timestampMs;
        private final List<String> reasoningChain = new ArrayList<>();

        public Builder tick(long tick) { this.tick = tick; return this; }
        public Builder observation(long obs) { this.observation = obs; return this; }
        public Builder thought(String thought) { this.thought = thought; return this; }
        public Builder action(AgentAction action) { this.action = action; return this; }
        public Builder actionResult(String result) { this.actionResult = result; return this; }
        public Builder actionSuccess(boolean success) { this.actionSuccess = success; return this; }
        public Builder reflection(String reflection) { this.reflection = reflection; return this; }
        public Builder timestampMs(long ts) { this.timestampMs = ts; return this; }

        public Builder addReasoningStep(String step) {
            this.reasoningChain.add(step);
            return this;
        }

        public ReasoningTrace build() {
            return new ReasoningTrace(this);
        }
    }
}
