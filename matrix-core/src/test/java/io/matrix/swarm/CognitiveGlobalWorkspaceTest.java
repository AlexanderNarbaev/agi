package io.matrix.swarm;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CognitiveGlobalWorkspaceTest {

    @Test
    void testCreateWorkspace() {
        CognitiveGlobalWorkspace ws = new CognitiveGlobalWorkspace();
        assertNotNull(ws);
        assertEquals(0.0, ws.getGlobalActivation(), 0.001);
    }

    @Test
    void testSubmitMessage() {
        CognitiveGlobalWorkspace ws = new CognitiveGlobalWorkspace();
        ws.submit(CognitiveGlobalWorkspace.Module.BIR, "test", 0.5);
        Optional<CognitiveGlobalWorkspace.Message> msg =
            ws.receive(CognitiveGlobalWorkspace.Module.BIR);
        assertTrue(msg.isPresent());
    }

    @Test
    void testHighUrgencyBroadcasts() {
        CognitiveGlobalWorkspace ws = new CognitiveGlobalWorkspace();
        ws.submit(CognitiveGlobalWorkspace.Module.MCTS, "urgent", 0.9);
        assertEquals(1, ws.getBroadcastCount());
    }

    @Test
    void testLowUrgencyNoBroadcast() {
        CognitiveGlobalWorkspace ws = new CognitiveGlobalWorkspace();
        ws.submit(CognitiveGlobalWorkspace.Module.HDC, "low", 0.3);
        assertEquals(0, ws.getBroadcastCount());
    }

    @Test
    void testActivationDecay() {
        CognitiveGlobalWorkspace ws = new CognitiveGlobalWorkspace();
        ws.submit(CognitiveGlobalWorkspace.Module.BIR, "x", 0.9);
        double after = ws.getGlobalActivation();
        double after2 = ws.getGlobalActivation();
        assertTrue(after2 < after, "Activation should decay: " + after + " -> " + after2);
    }

    @Test
    void testAllModulesAccessible() {
        CognitiveGlobalWorkspace ws = new CognitiveGlobalWorkspace();
        for (CognitiveGlobalWorkspace.Module m : CognitiveGlobalWorkspace.Module.values()) {
            ws.submit(m, "msg-for-" + m, 0.5);
        }
        for (CognitiveGlobalWorkspace.Module m : CognitiveGlobalWorkspace.Module.values()) {
            assertTrue(ws.receive(m).isPresent());
        }
    }
}
