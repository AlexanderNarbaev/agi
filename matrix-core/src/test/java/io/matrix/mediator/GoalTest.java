package io.matrix.mediator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoalTest {

    @Test
    void shouldCreateGoalWithPendingStatus() {
        Goal goal = Goal.create(DriverType.CURIOSITY, "explore", 0.8);

        assertThat(goal.status()).isEqualTo(GoalStatus.PENDING);
        assertThat(goal.driver()).isEqualTo(DriverType.CURIOSITY);
        assertThat(goal.description()).isEqualTo("explore");
        assertThat(goal.priority()).isEqualTo(0.8);
        assertThat(goal.id()).isNotNull();
    }

    @Test
    void shouldTransitionThroughLifecycle() {
        Goal goal = Goal.create(DriverType.ENERGY, "optimize", 0.7);

        assertThat(goal.status()).isEqualTo(GoalStatus.PENDING);

        Goal active = goal.activate();
        assertThat(active.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(active.id()).isEqualTo(goal.id());
        assertThat(active.description()).isEqualTo(goal.description());

        Goal satisfied = active.satisfy();
        assertThat(satisfied.status()).isEqualTo(GoalStatus.SATISFIED);

        Goal failed = active.fail();
        assertThat(failed.status()).isEqualTo(GoalStatus.FAILED);
    }

    @Test
    void shouldPreserveImmutability() {
        Goal goal = Goal.create(DriverType.SAFETY, "check", 0.5);

        Goal active = goal.activate();

        assertThat(goal.status()).isEqualTo(GoalStatus.PENDING);
        assertThat(active.status()).isEqualTo(GoalStatus.ACTIVE);
    }
}
