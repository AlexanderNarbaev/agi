package io.matrix.mediator;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceMediatorTest {

    @Test
    void shouldCreateWithDefaults() {
        InstanceMediator mediator = InstanceMediator.withDefaults(new Random(42));

        assertThat(mediator.energy()).isNotNull();
        assertThat(mediator.safety()).isNotNull();
        assertThat(mediator.curiosity()).isNotNull();
        assertThat(mediator.drivers()).hasSize(8);
        assertThat(mediator.goals()).isEmpty();
        assertThat(mediator.tasks()).isEmpty();
        assertThat(mediator.tickCount()).isEqualTo(0);
    }

    @Test
    void shouldIncrementTickCount() {
        InstanceMediator mediator = InstanceMediator.withDefaults(new Random(42));

        mediator.tick();

        assertThat(mediator.tickCount()).isEqualTo(1);
    }

    @Test
    void shouldUpdateDriversOnTick() {
        InstanceMediator mediator = InstanceMediator.withDefaults(new Random(42));

        for (int i = 0; i < 10; i++) {
            mediator.tick();
        }

        assertThat(mediator.energy().level()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldGenerateGoalWhenDriverHigh() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 5);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.energy().nudge(0.9);

        mediator.tick();

        assertThat(mediator.goals()).isNotEmpty();
        assertThat(mediator.goals().stream()
                .anyMatch(g -> g.driver() == DriverType.ENERGY)).isTrue();
    }

    @Test
    void shouldScheduleTaskAndActivateIt() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 1);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.curiosity().nudge(0.9);
        mediator.tick();

        assertThat(mediator.allTasks()).isNotEmpty();
        assertThat(mediator.allTasks().stream()
                .anyMatch(t -> t.driver() == DriverType.CURIOSITY && t.isActive())).isTrue();
    }

    @Test
    void shouldCompleteGoalAndTask() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 2);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.energy().nudge(0.9);
        mediator.tick();

        var goal = mediator.goals().stream()
                .filter(g -> g.driver() == DriverType.ENERGY).findFirst().orElseThrow();
        mediator.completeGoal(goal.id());

        var updated = mediator.goals().stream()
                .filter(g -> g.id().equals(goal.id())).findFirst().orElseThrow();
        assertThat(updated.status()).isEqualTo(GoalStatus.SATISFIED);

        var task = mediator.allTasks().stream()
                .filter(t -> t.goalId().equals(goal.id())).findFirst().orElseThrow();
        assertThat(task.isTerminal()).isTrue();
    }

    @Test
    void shouldFailGoal() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 2);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.safety().nudge(0.9);
        mediator.tick();

        var goal = mediator.goals().stream()
                .filter(g -> g.driver() == DriverType.SAFETY).findFirst().orElseThrow();
        mediator.failGoal(goal.id());

        var updated = mediator.goals().stream()
                .filter(g -> g.id().equals(goal.id())).findFirst().orElseThrow();
        assertThat(updated.status()).isEqualTo(GoalStatus.FAILED);
    }

    @Test
    void shouldReportExternalSignals() {
        InstanceMediator mediator = InstanceMediator.withDefaults(new Random(42));
        double beforeEnergy = mediator.energy().level();
        double beforeSafety = mediator.safety().level();
        double beforeCuriosity = mediator.curiosity().level();

        mediator.reportLowResources();
        mediator.reportAnomaly();
        mediator.reportStagnation();

        assertThat(mediator.energy().level()).isGreaterThan(beforeEnergy);
        assertThat(mediator.safety().level()).isGreaterThan(beforeSafety);
        assertThat(mediator.curiosity().level()).isGreaterThan(beforeCuriosity);
    }

    @Test
    void shouldReturnActionsFromTick() {
        InstanceMediator mediator = InstanceMediator.withDefaults(new Random(42));
        var actions = mediator.tick();

        assertThat(actions).isNotNull();
    }

    @Test
    void shouldLogActions() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 2);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.energy().nudge(0.9);
        mediator.tick();

        assertThat(mediator.actionLog()).isNotEmpty();
    }

    @Test
    void shouldNotGenerateDuplicateGoals() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 5);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.energy().nudge(0.9);

        mediator.tick();
        mediator.tick();
        mediator.tick();

        long energyGoals = mediator.goals().stream()
                .filter(g -> g.driver() == DriverType.ENERGY).count();
        assertThat(energyGoals).isEqualTo(1);
    }

    @Test
    void shouldRespectMaxActiveGoals() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 1);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.energy().nudge(0.9);
        mediator.safety().nudge(0.9);

        mediator.tick();

        long activeGoals = mediator.goals().stream()
                .filter(g -> g.status() == GoalStatus.ACTIVE).count();
        assertThat(activeGoals).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldFilterTerminalTasks() {
        var config = new InstanceMediator.MediatorConfig(100, 1.0, 2);
        InstanceMediator mediator = new InstanceMediator(config, new Random(42));

        mediator.energy().nudge(0.9);
        mediator.tick();

        var goal = mediator.goals().stream()
                .filter(g -> g.driver() == DriverType.ENERGY).findFirst().orElseThrow();
        mediator.completeGoal(goal.id());

        long visibleTasks = mediator.tasks().stream()
                .filter(t -> t.goalId().equals(goal.id())).count();
        assertThat(visibleTasks).isEqualTo(0);
    }
}
