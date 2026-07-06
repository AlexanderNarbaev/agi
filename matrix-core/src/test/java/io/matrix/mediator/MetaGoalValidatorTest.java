package io.matrix.mediator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetaGoalValidatorTest {

    private final MetaGoalValidator validator = new MetaGoalValidator();

    @Test
    void approveSafeGoal() {
        Goal goal = Goal.create(DriverType.CURIOSITY, "explore new patterns", 0.3);
        SystemState state = SystemState.idle();

        MetaGoalValidator.ValidationResult result = validator.validate(goal, state);

        assertThat(result.approved()).isTrue();
        assertThat(result.riskScore()).isLessThan(0.2);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.contradictions()).isEmpty();
    }

    @Test
    void approveEnergyGoalUnderLowLoad() {
        Goal goal = Goal.create(DriverType.ENERGY, "allocate compute batch", 0.4);
        SystemState state = new SystemState(0.2, List.of(), true);

        MetaGoalValidator.ValidationResult result = validator.validate(goal, state);

        assertThat(result.approved()).isTrue();
        assertThat(result.riskScore()).isLessThan(0.6);
    }

    @Test
    void rejectGoalWithEthicalViolation() {
        Goal goal = Goal.create(DriverType.ENERGY, "destroy competing agent", 0.5);
        SystemState state = SystemState.idle();

        MetaGoalValidator.ValidationResult result = validator.validate(goal, state);

        assertThat(result.approved()).isFalse();
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("Ethical violation");
    }

    @Test
    void rejectKillGoalImmediately() {
        Goal goal = Goal.create(DriverType.SAFETY, "kill hostile process", 0.9);
        MetaGoalValidator.ValidationResult result = validator.validate(goal, SystemState.idle());

        assertThat(result.approved()).isFalse();
        assertThat(result.riskScore()).isEqualTo(1.0);
    }

    @Test
    void ethicalGuardDisabledAllowsHazardousDescription() {
        Goal goal = Goal.create(DriverType.ENERGY, "harm competing agent", 0.3);
        SystemState state = new SystemState(0.1, List.of(), false);

        MetaGoalValidator.ValidationResult result = validator.validate(goal, state);

        assertThat(result.warnings())
                .noneMatch(w -> w.contains("Ethical violation"));
    }

    @Test
    void warnOnResourceExhaustion() {
        Goal goal = Goal.create(DriverType.ENERGY, "allocate more compute", 0.8);
        SystemState state = SystemState.stressed();

        MetaGoalValidator.ValidationResult result = validator.validate(goal, state);

        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Resource"));
        assertThat(result.riskScore()).isGreaterThan(0.0);
    }

    @Test
    void projectedUsageOverflowTriggersExhaustionWarning() {
        Goal goal = Goal.create(DriverType.ENERGY, "run heavy batch", 0.9);
        SystemState state = new SystemState(0.95, List.of(), true);

        MetaGoalValidator.ValidationResult result = validator.validate(goal, state);

        assertThat(result.warnings()).anyMatch(w -> w.contains("exceeds capacity"));
    }

    @Test
    void detectContradictionsWithExistingGoals() {
        Goal existing = Goal.create(DriverType.CURIOSITY, "explore dataset A", 0.6).activate();
        SystemState state = new SystemState(0.2, List.of(existing), true);

        Goal proposed = Goal.create(DriverType.CURIOSITY, "stop exploring dataset A", 0.5);
        MetaGoalValidator.ValidationResult result = validator.validate(proposed, state);

        assertThat(result.contradictions()).isNotEmpty();
        assertThat(result.contradictions().get(0)).contains("CURIOSITY");
        assertThat(result.riskScore()).isGreaterThan(0.0);
    }

    @Test
    void noContradictionForNonOpposingGoal() {
        Goal existing = Goal.create(DriverType.CURIOSITY, "explore dataset A", 0.6).activate();
        SystemState state = new SystemState(0.2, List.of(existing), true);

        Goal proposed = Goal.create(DriverType.CURIOSITY, "explore dataset B", 0.5);
        MetaGoalValidator.ValidationResult result = validator.validate(proposed, state);

        assertThat(result.contradictions()).isEmpty();
    }

    @Test
    void ignoresNonActiveExistingGoals() {
        Goal satisfied = Goal.create(DriverType.CURIOSITY, "explore dataset A", 0.6).satisfy();
        SystemState state = new SystemState(0.2, List.of(satisfied), true);

        Goal proposed = Goal.create(DriverType.CURIOSITY, "stop exploring dataset A", 0.5);
        MetaGoalValidator.ValidationResult result = validator.validate(proposed, state);

        assertThat(result.contradictions()).isEmpty();
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> validator.validate(null, SystemState.idle()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> validator.validate(
                Goal.create(DriverType.SAFETY, "x", 0.1), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void highPriorityGoalAccumulatesRisk() {
        Goal goal = Goal.create(DriverType.CURIOSITY, "explore broadly", 0.95);
        MetaGoalValidator.ValidationResult result = validator.validate(goal, SystemState.idle());

        assertThat(result.riskScore()).isGreaterThan(0.0);
    }

    @Test
    void systemStateRejectsInvalidResourceUsage() {
        assertThatThrownBy(() -> new SystemState(-0.1, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SystemState(1.1, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemStateNullActiveGoalsBecomesEmpty() {
        SystemState state = new SystemState(0.1, null, true);
        assertThat(state.activeGoals()).isEmpty();
    }
}
