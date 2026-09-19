package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W404 — Tests for ConversationStats.
 */
class ConversationStatsTest {
    
    @Test
    void testStatsFromEmptyDir(@TempDir Path tempDir) throws Exception {
        var statsClass = io.matrix.cli.ConversationStats.Stats.class;
        var stats = statsClass.getDeclaredConstructor().newInstance();
        
        var totalSessionsMethod = statsClass.getDeclaredField("totalSessions");
        totalSessionsMethod.setAccessible(true);
        assertEquals(0, totalSessionsMethod.getInt(stats));
    }
    
    @Test
    void testStatsCountsTurns(@TempDir Path tempDir) throws Exception {
        Path ndjson = tempDir.resolve("session.ndjson");
        Files.writeString(ndjson,
            "{\"role\":\"user\",\"content\":\"Hi\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Hello!\"}\n" +
            "{\"role\":\"user\",\"content\":\"Q?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"A!\"}\n"
        );
        
        var statsClass = io.matrix.cli.ConversationStats.Stats.class;
        var stats = statsClass.getDeclaredConstructor().newInstance();
        var addFileMethod = statsClass.getDeclaredMethod("addFile", Path.class);
        addFileMethod.invoke(stats, ndjson);
        
        var totalTurnsField = statsClass.getDeclaredField("totalTurns");
        totalTurnsField.setAccessible(true);
        var userTurnsField = statsClass.getDeclaredField("userTurns");
        userTurnsField.setAccessible(true);
        var assistantTurnsField = statsClass.getDeclaredField("assistantTurns");
        assistantTurnsField.setAccessible(true);
        
        assertEquals(4, totalTurnsField.getInt(stats));
        assertEquals(2, userTurnsField.getInt(stats));
        assertEquals(2, assistantTurnsField.getInt(stats));
    }
    
    @Test
    void testStatsSkipsMalformed(@TempDir Path tempDir) throws Exception {
        Path ndjson = tempDir.resolve("malformed.ndjson");
        Files.writeString(ndjson,
            "{\"role\":\"user\",\"content\":\"valid\"}\n" +
            "not valid json\n" +
            "{\"role\":\"assistant\",\"content\":\"valid too\"}\n"
        );
        
        var statsClass = io.matrix.cli.ConversationStats.Stats.class;
        var stats = statsClass.getDeclaredConstructor().newInstance();
        var addFileMethod = statsClass.getDeclaredMethod("addFile", Path.class);
        addFileMethod.invoke(stats, ndjson);
        
        var totalTurnsField = statsClass.getDeclaredField("totalTurns");
        totalTurnsField.setAccessible(true);
        assertEquals(2, totalTurnsField.getInt(stats));
    }
}
