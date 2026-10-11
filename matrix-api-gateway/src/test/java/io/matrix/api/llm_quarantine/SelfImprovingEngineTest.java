package io.matrix.api.llm_quarantine;
import io.matrix.api.llm_quarantine.BrainLearningLoop;
import io.matrix.autonomy.SelfImprovingEngine;
import io.matrix.api.llm_quarantine.BrainLearningLoop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W497 — Tests for BrainLearningLoop.
 */
class BrainLearningLoopTest {
    
    @Test
    void testClassExists() {
        assertNotNull(BrainLearningLoop.class);
    }
    
    @Test
    void testMainMethod() throws NoSuchMethodException {
        var main = BrainLearningLoop.class.getMethod("main", String[].class);
        assertNotNull(main);
    }
    
    @Test
    void testTalkMethod() throws NoSuchMethodException {
        var method = BrainLearningLoop.class.getMethod("talk", String.class);
        assertNotNull(method);
    }
    
    @Test
    void testLearnMethod() throws NoSuchMethodException {
        var method = BrainLearningLoop.class.getMethod("learn");
        assertNotNull(method);
    }
    
    @Test
    void testGetKnowledgeBase() throws NoSuchMethodException {
        var method = BrainLearningLoop.class.getMethod("getKnowledgeBase");
        assertNotNull(method);
    }
    
    @Test
    void testGetTotalTalks() throws NoSuchMethodException {
        var method = BrainLearningLoop.class.getMethod("getTotalTalks");
        assertNotNull(method);
    }
    
    @Test
    void testGetTotalLearned() throws NoSuchMethodException {
        var method = BrainLearningLoop.class.getMethod("getTotalLearned");
        assertNotNull(method);
    }
    
    @Test
    void testClose() throws NoSuchMethodException {
        var method = BrainLearningLoop.class.getMethod("close");
        assertNotNull(method);
    }
}

class SelfImprovingEngineTest {
    
    @Test
    void testClassExists() {
        assertNotNull(SelfImprovingEngine.class);
    }
    
    @Test
    void testStart() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("start");
        assertNotNull(method);
    }
    
    @Test
    void testStop() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("stop");
        assertNotNull(method);
    }
    
    @Test
    void testTalk() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("talk", String.class);
        assertNotNull(method);
    }
    
    @Test
    void testLearnFromConversations() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("learnFromConversations");
        assertNotNull(method);
    }
    
    @Test
    void testGetLearnCycles() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("getLearnCycles");
        assertNotNull(method);
    }
    
    @Test
    void testGetTalkCycles() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("getTalkCycles");
        assertNotNull(method);
    }
    
    @Test
    void testGetLearningLoop() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("getLearningLoop");
        assertNotNull(method);
    }
    
    @Test
    void testIsRunning() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("isRunning");
        assertNotNull(method);
    }
    
    @Test
    void testClose() throws NoSuchMethodException {
        var method = SelfImprovingEngine.class.getMethod("close");
        assertNotNull(method);
    }
    
    @Test
    void testMainMethod() throws NoSuchMethodException {
        // W35.1: the CLI entry point moved out of the engine and into the Gateway launcher.
        // The engine itself must stay LLM-free in matrix-core because OvernightRunner
        // constructs it, so main() — the only part that loaded a Qwen model — went to
        // SelfImprovingEngineLauncher. The assertion is retargeted rather than deleted: a
        // launchable CLI still has to exist, and deleting the test would have quietly
        // dropped that requirement instead of relocating it.
        var main = SelfImprovingEngineLauncher.class.getMethod("main", String[].class);
        assertNotNull(main);
    }

    /**
     * W35.1 — the engine must no longer carry its own CLI.
     *
     * <p>This is the guard for the property the move exists to establish. Without it the
     * launcher could be removed and the engine's main() restored, and the suite would stay
     * green while matrix-core reacquired an LLM dependency.</p>
     */
    @Test
    void engineItselfNoLongerExposesAMainMethod() {
        boolean hasMain = java.util.Arrays.stream(SelfImprovingEngine.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("main")
                        && java.util.Arrays.equals(m.getParameterTypes(), new Class<?>[]{String[].class}));
        assertFalse(hasMain,
                "SelfImprovingEngine must stay free of its LLM-loading CLI; that belongs to "
                        + "SelfImprovingEngineLauncher in the Gateway quarantine");
    }
}
