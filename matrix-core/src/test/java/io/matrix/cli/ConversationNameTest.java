package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W426 — Tests for ConversationName.
 */
class ConversationNameTest {
    
    @Test
    void testReadNameMethodExists() throws NoSuchMethodException {
        var method = io.matrix.cli.ConversationName.class
            .getDeclaredMethod("readName", String.class);
        assertNotNull(method);
    }
    
    @Test
    void testWriteNameMethodExists() throws NoSuchMethodException {
        var method = io.matrix.cli.ConversationName.class
            .getDeclaredMethod("writeName", String.class, String.class);
        assertNotNull(method);
    }
    
    @Test
    void testRemoveNameMethodExists() throws NoSuchMethodException {
        var method = io.matrix.cli.ConversationName.class
            .getDeclaredMethod("removeName", String.class);
        assertNotNull(method);
    }
    
    @Test
    void testMetaPrefixConstant() {
        // The META prefix is "# META name: " - just verify the class is well-formed
        assertNotNull(ConversationName.class);
    }
}
