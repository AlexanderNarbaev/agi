package io.matrix.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStateTest {

    // ── AgentAction tests ──

    @Test
    void actionTypeShouldHaveValidDescriptions() {
        for (AgentAction.ActionType type : AgentAction.ActionType.values()) {
            assertThat(type.description()).isNotBlank();
        }
    }

    @Test
    void actionShouldBeImmutable() {
        var action = new AgentAction(AgentAction.ActionType.MOVE,
                Map.of("direction", "north"));

        assertThat(action.type()).isEqualTo(AgentAction.ActionType.MOVE);
        assertThat(action.parameters()).containsEntry("direction", "north");
        assertThat(action.hasResult()).isFalse();
    }

    @Test
    void actionWithResultShouldCreateCopy() {
        var original = new AgentAction(AgentAction.ActionType.MINE);
        var result = AgentAction.ActionResult.success("mined ore", 50);
        var withResult = original.withResult(result);

        assertThat(original.hasResult()).isFalse();
        assertThat(withResult.hasResult()).isTrue();
        assertThat(withResult.result().success()).isTrue();
        assertThat(withResult.result().output()).isEqualTo("mined ore");
        assertThat(withResult.result().durationMs()).isEqualTo(50);
    }

    @Test
    void actionShouldSupportEquality() {
        var a1 = new AgentAction(AgentAction.ActionType.CRAFT, Map.of("item", "sword"));
        var a2 = new AgentAction(AgentAction.ActionType.CRAFT, Map.of("item", "sword"));
        var a3 = new AgentAction(AgentAction.ActionType.MINE);

        assertThat(a1).isEqualTo(a2);
        assertThat(a1).isNotEqualTo(a3);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void actionParamOrDefaultShouldWork() {
        var action = new AgentAction(AgentAction.ActionType.EXPLORE,
                Map.of("radius", 5));

        assertThat(action.paramOrDefault("radius", 0)).isEqualTo(5);
        assertThat(action.paramOrDefault("missing", "default")).isEqualTo("default");
    }

    @Test
    void actionResultShouldDistinguishSuccessAndFailure() {
        var success = AgentAction.ActionResult.success("ok", 10);
        var failure = AgentAction.ActionResult.failure("error", 20);
        var empty = AgentAction.ActionResult.empty();

        assertThat(success.success()).isTrue();
        assertThat(failure.success()).isFalse();
        assertThat(empty.success()).isTrue();
        assertThat(empty.durationMs()).isZero();
    }

    // ── AgentState tests ──

    @Test
    void stateShouldCaptureObservationAndThought() {
        var action = new AgentAction(AgentAction.ActionType.MOVE);
        var state = new AgentState(0xABCDE, new boolean[]{true, false, true},
                action, new double[]{0.5, 0.3}, 1);

        assertThat(state.observation()).isEqualTo(0xABCDE);
        assertThat(state.thought()).containsExactly(true, false, true);
        assertThat(state.action()).isEqualTo(action);
        assertThat(state.driverLevels()).containsExactly(0.5, 0.3);
        assertThat(state.tick()).isEqualTo(1);
    }

    @Test
    void stateThoughtShouldBeDefensiveCopy() {
        var thought = new boolean[]{true, false};
        var state = new AgentState(0, thought, null, null, 0);

        // Modify original — should not affect state
        thought[0] = false;
        assertThat(state.thought()[0]).isTrue();

        // Modify returned copy — should not affect state
        boolean[] returned = state.thought();
        returned[1] = true;
        assertThat(state.thought()[1]).isFalse();
    }

    @Test
    void stateDriverLevelsShouldBeDefensiveCopy() {
        var levels = new double[]{0.5, 0.8};
        var state = new AgentState(0, null, null, levels, 0);

        levels[0] = 0.0;
        assertThat(state.driverLevels()[0]).isEqualTo(0.5);
    }

    @Test
    void stateThoughtActivationShouldCountTrues() {
        var state = new AgentState(0, new boolean[]{true, true, false, true, false},
                null, null, 0);

        assertThat(state.thoughtActivation()).isEqualTo(3);
    }

    @Test
    void stateWithEmptyThoughtShouldHaveZeroActivation() {
        var state = new AgentState(0, new boolean[0], null, null, 0);
        assertThat(state.thoughtActivation()).isZero();
    }

    @Test
    void stateWithNullThoughtShouldHandleGracefully() {
        var state = new AgentState(0, null, null, null, 0);
        assertThat(state.thought()).isEmpty();
        assertThat(state.thoughtActivation()).isZero();
    }

    @Test
    void stateActionTypeShouldReturnNullWhenNoAction() {
        var state = new AgentState(0, null, null, null, 0);
        assertThat(state.actionType()).isNull();
    }

    @Test
    void stateSameActionTypeShouldWork() {
        var move = new AgentAction(AgentAction.ActionType.MOVE);
        var mine = new AgentAction(AgentAction.ActionType.MINE);

        var s1 = new AgentState(0, null, move, null, 0);
        var s2 = new AgentState(1, null, move, null, 1);
        var s3 = new AgentState(2, null, mine, null, 2);

        assertThat(s1.sameActionType(s2)).isTrue();
        assertThat(s1.sameActionType(s3)).isFalse();
        assertThat(s1.sameActionType(null)).isFalse();
    }

    @Test
    void stateShouldHaveTimestamp() {
        long before = System.currentTimeMillis();
        var state = new AgentState(0, null, null, null, 0);
        long after = System.currentTimeMillis();

        assertThat(state.timestampMs()).isBetween(before, after);
    }

    @Test
    void stateEqualityShouldBeBasedOnObservationTickAction() {
        var action = new AgentAction(AgentAction.ActionType.WAIT);
        var s1 = new AgentState(100, new boolean[]{true}, action, new double[]{0.5}, 1);
        var s2 = new AgentState(100, new boolean[]{false}, action, new double[]{0.9}, 1);
        var s3 = new AgentState(200, new boolean[]{true}, action, new double[]{0.5}, 2);

        // Same observation + tick + action → equal (thought/driverLevels ignored)
        assertThat(s1).isEqualTo(s2);
        // Different observation → not equal
        assertThat(s1).isNotEqualTo(s3);
    }

    @Test
    void immutableHistoryShouldReturnUnmodifiableList() {
        var action = new AgentAction(AgentAction.ActionType.WAIT);
        var state = new AgentState(0, null, action, null, 0);
        List<AgentState> history = AgentState.immutableHistory(List.of(state));

        assertThat(history).hasSize(1);
        assertThat(history.get(0)).isEqualTo(state);
    }

    @Test
    void stateToStringShouldBeReadable() {
        var action = new AgentAction(AgentAction.ActionType.CRAFT);
        var state = new AgentState(0xFF, new boolean[]{true, false, true}, action, null, 42);

        String str = state.toString();
        assertThat(str).contains("tick=42");
        assertThat(str).contains("CRAFT");
        assertThat(str).contains("2/3");
    }
}
