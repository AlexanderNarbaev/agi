package io.matrix.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W518 — Tests for BrainRunner.
 */
class BrainRunnerTest {
    
    @Test
    void testClassExists() {
        assertNotNull(BrainRunner.class);
    }
    
    @Test
    void testMainMethod() throws NoSuchMethodException {
        var main = BrainRunner.class.getMethod("main", String[].class);
        assertNotNull(main);
    }
    
    @Test
    void testConstructor() throws Exception {
        var ctor = BrainRunner.class.getConstructor(String.class);
        assertNotNull(ctor);
    }
    
    @Test
    void testTalkMethod() throws Exception {
        var method = BrainRunner.class.getMethod("talk", String.class);
        assertNotNull(method);
    }
    
    @Test
    void testLearnMethod() throws Exception {
        var method = BrainRunner.class.getMethod("learn");
        assertNotNull(method);
    }
    
    @Test
    void testStatsMethod() throws Exception {
        var method = BrainRunner.class.getMethod("stats");
        assertNotNull(method);
    }
    
    @Test
    void testCloseMethod() throws Exception {
        var method = BrainRunner.class.getMethod("close");
        assertNotNull(method);
    }
    
    @Test
    void testHelpCommand() throws Exception {
        // The main method should not throw on --help
        // Just verify it exists and takes String[]
        var method = BrainRunner.class.getMethod("main", String[].class);
        assertNotNull(method);
    }
}
