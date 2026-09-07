package io.matrix.goals;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 215 — GoalTracker unit tests. */
class GoalTrackerTest {

    @Test
    void emptyTracker() {
        var t = new GoalTracker();
        assertThat(t.size()).isZero();
        assertThat(t.activeGoals()).isEmpty();
    }

    @Test
    void addCreatesActiveGoal() {
        var t = new GoalTracker();
        var g = t.add("Solve problem X", 5);
        assertThat(g.status()).isEqualTo(GoalTracker.Status.ACTIVE);
        assertThat(g.priority()).isEqualTo(5);
        assertThat(t.activeCount()).isEqualTo(1);
    }

    @Test
    void completeChangesStatus() {
        var t = new GoalTracker();
        var g = t.add("X", 5);
        t.complete(g.id());
        assertThat(t.activeCount()).isZero();
        assertThat(t.completedCount()).isEqualTo(1);
    }

    @Test
    void abandonChangesStatus() {
        var t = new GoalTracker();
        var g = t.add("X", 5);
        t.abandon(g.id());
        assertThat(t.activeCount()).isZero();
        assertThat(t.completedCount()).isZero();
    }

    @Test
    void activeSortedByPriority() {
        var t = new GoalTracker();
        t.add("low", 1);
        t.add("high", 10);
        t.add("mid", 5);
        var sorted = t.activeGoals();
        assertThat(sorted.get(0).description()).isEqualTo("high");
        assertThat(sorted.get(1).description()).isEqualTo("mid");
        assertThat(sorted.get(2).description()).isEqualTo("low");
    }

    @Test
    void idsAreUnique() {
        var t = new GoalTracker();
        var a = t.add("A", 5);
        var b = t.add("B", 5);
        var c = t.add("C", 5);
        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(b.id()).isNotEqualTo(c.id());
        assertThat(a.id()).isLessThan(b.id());
    }

    @Test
    void completeUnknownIdNoOp() {
        var t = new GoalTracker();
        t.add("X", 5);
        // No exception
        t.complete(999);
        assertThat(t.activeCount()).isEqualTo(1);
    }

    @Test
    void sizeReflectsAllGoals() {
        var t = new GoalTracker();
        t.add("A", 1);
        t.add("B", 2);
        t.add("C", 3);
        assertThat(t.size()).isEqualTo(3);
        var g = t.activeGoals().get(0);
        t.complete(g.id());
        assertThat(t.size()).isEqualTo(3);  // not removed, just status changed
    }
}
