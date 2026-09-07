package io.matrix.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 178 — ProjectState unit tests. */
class ProjectStateTest {

    @Test
    void currentStatusIsValid() {
        ProjectState.Status s = ProjectState.current();
        assertThat(s.tests()).isGreaterThan(0);
        assertThat(s.passed()).isGreaterThan(0);
        assertThat(s.failed()).isZero();
        assertThat(s.classes()).isGreaterThan(0);
    }

    @Test
    void isAcceptingReturnsTrueForCleanStatus() {
        ProjectState.Status s = ProjectState.current();
        assertThat(ProjectState.isAccepting(s)).isTrue();
    }

    @Test
    void isAcceptingRejectsFailures() {
        ProjectState.Status bad = new ProjectState.Status(
                10, 0, 100, 95, 5, 4, 4, "v0.0.1");
        assertThat(ProjectState.isAccepting(bad)).isFalse();
    }

    @Test
    void invariantsAllPresent() {
        var map = ProjectState.invariantsMap();
        assertThat(map).containsKey("DETERMINISM");
        assertThat(map).containsKey("K_MAX");
        assertThat(map).containsKey("FROZEN_ZONES");
        assertThat(map).containsKey("4_PROHIBITIONS");
        assertThat(map).containsKey("COVERAGE");
        assertThat(map).containsKey("CLAIMS");
        assertThat(map).containsKey("STACK");
        assertThat(map).containsKey("AUDITABILITY");
        assertThat(map).hasSize(8);
    }

    @Test
    void phaseCountValid() {
        ProjectState.Status s = ProjectState.current();
        assertThat(s.phasesComplete()).isLessThanOrEqualTo(s.phasesTotal());
        assertThat(s.phasesTotal()).isEqualTo(4);
    }

    @Test
    void componentCountMatches() {
        // ProjectState uses BrainLoopArchitecture's count
        assertThat(ProjectState.componentCount()).isEqualTo(7);
    }
}
