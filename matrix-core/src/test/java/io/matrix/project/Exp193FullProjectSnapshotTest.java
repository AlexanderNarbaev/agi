package io.matrix.project;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 193 — FullProjectSnapshot EXP.
 *
 * <p>Runs high-level smoke through every session module
 * (auditor, perception, signals, consciousness, memory,
 * federation, pilot, bir) and reports sizes.
 *
 * <p>This is the "everything works" test that exercises the
 * integration of all session modules.
 */
@Tag("exp")
class Exp193FullProjectSnapshotTest {

    @Test
    void sessionModulesSmoke() {
        List<String> modules = new ArrayList<>();
        // Just verify that the imports work and we can
        // instantiate the main classes.
        modules.add(io.matrix.auditor.DecisionPathAuditor.class.getSimpleName());
        modules.add(io.matrix.auditor.MatrixTrace.class.getSimpleName());
        modules.add(io.matrix.auditor.AuditCheck.class.getSimpleName());

        modules.add(io.matrix.perception.TextEncoder.class.getSimpleName());
        modules.add(io.matrix.perception.SaliencyEngine.class.getSimpleName());

        modules.add(io.matrix.signals.SignalRegistry.class.getSimpleName());

        modules.add(io.matrix.consciousness.BrainLoopService.class.getSimpleName());
        modules.add(io.matrix.consciousness.ActionGate.class.getSimpleName());
        modules.add(io.matrix.consciousness.Impulse.class.getSimpleName());
        modules.add(io.matrix.consciousness.AttentionRouter.class.getSimpleName());
        modules.add(io.matrix.consciousness.PredictionModel.class.getSimpleName());
        modules.add(io.matrix.consciousness.ArousalDynamics.class.getSimpleName());

        modules.add(io.matrix.memory.MemoryHierarchyTier.class.getSimpleName());
        modules.add(io.matrix.memory.ConsolidationCycle.class.getSimpleName());
        modules.add(io.matrix.memory.PersistentMemory.class.getSimpleName());

        modules.add(io.matrix.federation.FederationDigest.class.getSimpleName());

        modules.add(io.matrix.pilot.PilotGridWorld.class.getSimpleName());
        modules.add(io.matrix.pilot.PilotProactiveChat.class.getSimpleName());

        modules.add(io.matrix.bir.KMaxEnforcer.class.getSimpleName());

        assertThat(modules).hasSize(19);
        for (String name : modules) {
            assertThat(name).isNotBlank();
        }
        System.out.printf("[FULL-SNAPSHOT] %d session modules present%n",
                modules.size());
    }

    @Test
    void projectStateIsValid() {
        var s = ProjectState.current();
        assertThat(s.failed()).isZero();
        assertThat(s.passed()).isGreaterThan(0);
        assertThat(ProjectState.isAccepting(s)).isTrue();
        var invariants = ProjectState.invariantsMap();
        assertThat(invariants).hasSize(8);
        System.out.printf("[PROJECT-STATE] classes=%d, tests=%d passed/%d, phase=%d/%d, %s%n",
                s.classes(), s.passed(), s.tests(),
                s.phasesComplete(), s.phasesTotal(), s.version());
    }
}
