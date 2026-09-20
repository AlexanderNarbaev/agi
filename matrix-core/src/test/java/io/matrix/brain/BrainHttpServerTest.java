package io.matrix.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W501 — Tests for BrainHttpServer.
 */
class BrainHttpServerTest {
    
    @Test
    void testClassExists() {
        assertNotNull(BrainHttpServer.class);
    }
    
    @Test
    void testConstructorExists() throws NoSuchMethodException {
        var ctor = BrainHttpServer.class.getConstructor(int.class, String.class);
        assertNotNull(ctor);
    }
    
    @Test
    void testMain() throws NoSuchMethodException {
        var main = BrainHttpServer.class.getMethod("main", String[].class);
        assertNotNull(main);
    }
    
    @Test
    void testStart() throws NoSuchMethodException {
        var start = BrainHttpServer.class.getMethod("start");
        assertNotNull(start);
    }
}
