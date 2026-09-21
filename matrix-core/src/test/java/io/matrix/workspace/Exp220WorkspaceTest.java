package io.matrix.workspace;

import io.matrix.consciousness.BrainLoopService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 220 — Workspace EXP.
 *
 * <p>100 brain cycles with workspace, verifying scope state
 * accumulates correctly.
 */
@Tag("exp")
class Exp220WorkspaceTest {

    @Test
    void hundredCyclesWithWorkspace() {
        var brain = new BrainLoopService();
        var ws = new Workspace();
        ws.put("started_at", 0);
        for (int i = 0; i < 100; i++) {
            ws.put("cycle_" + i, brain.cycle("Q-" + i).action());
        }
        System.out.printf("[WORKSPACE-100] size=%d, started_at=%s%n",
                ws.size(), ws.get("started_at"));
        assertThat(ws.size()).isGreaterThan(0);
        assertThat(ws.get("started_at")).isEqualTo(0);
        assertThat(ws.get("cycle_50", String.class)).isNotNull();
    }
}
