package io.matrix.verification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class TlaIntegrationTest {

    @Test
    void getSpecFilesReturnsList() {
        var tla = new TlaIntegration();
        List<String> specs = tla.getSpecFiles();
        assertNotNull(specs);
    }

    @Test
    void isTlcAvailableDoesNotThrow() {
        var tla = new TlaIntegration();
        assertDoesNotThrow(() -> tla.isTlcAvailable());
    }

    @Test
    void getSpecsSummaryContainsCount() {
        var tla = new TlaIntegration();
        Map<String, Object> summary = tla.getSpecsSummary();
        assertNotNull(summary);
        assertTrue(summary.containsKey("count"));
        assertTrue(summary.containsKey("specs"));
        assertTrue(summary.containsKey("tlcAvailable"));
    }

    @Test
    void getSpecContentsForMissingReturnsEmpty() {
        var tla = new TlaIntegration();
        Map<String, String> contents = tla.getSpecContents("nonexistent.tla");
        assertNotNull(contents);
    }
}
