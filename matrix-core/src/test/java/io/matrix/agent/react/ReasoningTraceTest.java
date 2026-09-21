package io.matrix.agent.react;

import io.matrix.agent.AgentAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReasoningTraceTest {

    @Test
    void shouldBuildTraceWithAllFields() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(42)
                .observation(0xABCDL)
                .thought("Need to explore north")
                .action(new AgentAction(AgentAction.ActionType.MOVE))
                .actionResult("moved to (1,0)")
                .actionSuccess(true)
                .reflection("Move was successful, found new area")
                .timestampMs(1000L)
                .addReasoningStep("Observe: open space to north")
                .addReasoningStep("Reason: curiosity driver high")
                .addReasoningStep("Act: MOVE")
                .build();

        assertThat(trace.tick()).isEqualTo(42);
        assertThat(trace.observation()).isEqualTo(0xABCDL);
        assertThat(trace.thought()).isEqualTo("Need to explore north");
        assertThat(trace.action().type()).isEqualTo(AgentAction.ActionType.MOVE);
        assertThat(trace.actionResult()).isEqualTo("moved to (1,0)");
        assertThat(trace.actionSuccess()).isTrue();
        assertThat(trace.reflection()).isEqualTo("Move was successful, found new area");
        assertThat(trace.timestampMs()).isEqualTo(1000L);
        assertThat(trace.reasoningChain()).hasSize(3);
    }

    @Test
    void shouldBuildTraceWithDefaults() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(1)
                .observation(0L)
                .thought("test")
                .build();

        assertThat(trace.action()).isNull();
        assertThat(trace.actionResult()).isEmpty();
        assertThat(trace.actionSuccess()).isFalse();
        assertThat(trace.reflection()).isEmpty();
        assertThat(trace.reasoningChain()).isEmpty();
        assertThat(trace.timestampMs()).isGreaterThan(0);
    }

    @Test
    void shouldDetectCompleteTrace() {
        ReasoningTrace complete = new ReasoningTrace.Builder()
                .tick(1).observation(0L).thought("thinking")
                .action(new AgentAction(AgentAction.ActionType.WAIT))
                .reflection("reflected")
                .build();

        ReasoningTrace incomplete = new ReasoningTrace.Builder()
                .tick(1).observation(0L).thought("thinking")
                .build();

        assertThat(complete.isComplete()).isTrue();
        assertThat(incomplete.isComplete()).isFalse();
    }

    @Test
    void shouldSerializeToJson() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(5)
                .observation(0xFFL)
                .thought("test thought")
                .action(new AgentAction(AgentAction.ActionType.MINE))
                .actionResult("mined stone")
                .actionSuccess(true)
                .reflection("good outcome")
                .timestampMs(2000L)
                .addReasoningStep("step 1")
                .addReasoningStep("step 2")
                .build();

        String json = trace.toJson();

        assertThat(json).contains("\"tick\" : 5");
        assertThat(json).contains("\"thought\" : \"test thought\"");
        assertThat(json).contains("\"actionType\" : \"MINE\"");
        assertThat(json).contains("\"actionSuccess\" : true");
        assertThat(json).contains("\"reflection\" : \"good outcome\"");
        assertThat(json).contains("\"reasoningChain\"");
        assertThat(json).contains("step 1");
        assertThat(json).contains("step 2");
    }

    @Test
    void shouldDeserializeFromJson() {
        ReasoningTrace original = new ReasoningTrace.Builder()
                .tick(10)
                .observation(0x1234L)
                .thought("deserialized thought")
                .action(new AgentAction(AgentAction.ActionType.EXPLORE))
                .actionResult("explored area")
                .actionSuccess(false)
                .reflection("need different approach")
                .timestampMs(3000L)
                .addReasoningStep("step A")
                .build();

        String json = original.toJson();
        ReasoningTrace restored = ReasoningTrace.fromJson(json);

        assertThat(restored.tick()).isEqualTo(original.tick());
        assertThat(restored.observation()).isEqualTo(original.observation());
        assertThat(restored.thought()).isEqualTo(original.thought());
        assertThat(restored.action().type()).isEqualTo(original.action().type());
        assertThat(restored.actionResult()).isEqualTo(original.actionResult());
        assertThat(restored.actionSuccess()).isEqualTo(original.actionSuccess());
        assertThat(restored.reflection()).isEqualTo(original.reflection());
        assertThat(restored.timestampMs()).isEqualTo(original.timestampMs());
        assertThat(restored.reasoningChain()).isEqualTo(original.reasoningChain());
    }

    @Test
    void shouldHandleNullActionInSerialization() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(1).observation(0L).thought("no action")
                .build();

        String json = trace.toJson();
        assertThat(json).contains("\"actionType\" : \"NONE\"");

        ReasoningTrace restored = ReasoningTrace.fromJson(json);
        assertThat(restored.action()).isNull();
    }

    @Test
    void shouldHaveImmutableReasoningChain() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(1).observation(0L).thought("test")
                .addReasoningStep("step 1")
                .build();

        assertThat(trace.reasoningChain()).isInstanceOf(List.class);
        // List.copyOf returns unmodifiable list
        assertThat(trace.reasoningChain()).hasSize(1);
    }

    @Test
    void shouldHaveReadableToString() {
        ReasoningTrace trace = new ReasoningTrace.Builder()
                .tick(7)
                .observation(0L)
                .thought("A very long thought that should be truncated in toString for readability")
                .action(new AgentAction(AgentAction.ActionType.CRAFT))
                .actionSuccess(true)
                .reflection("A very long reflection that should also be truncated")
                .build();

        String str = trace.toString();
        assertThat(str).contains("tick=7");
        assertThat(str).contains("CRAFT");
        assertThat(str).contains("success=true");
    }
}
