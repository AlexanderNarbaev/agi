package io.matrix.imports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeightImporterExtendedTest {

    @Test
    void humanBytesFormats() {
        assertEquals("0 B", WeightImporter.humanBytes(0));
        assertEquals("1 B", WeightImporter.humanBytes(1));
        assertTrue(WeightImporter.humanBytes(1024).contains("KB"));
        assertTrue(WeightImporter.humanBytes(1024 * 1024).contains("MB"));
        assertTrue(WeightImporter.humanBytes(1024L * 1024 * 1024).contains("GB"));
    }

    @Test
    void humanBytesLarge() {
        assertTrue(WeightImporter.humanBytes(1024L * 1024 * 1024 * 1024).contains("TB"));
        assertTrue(WeightImporter.humanBytes(1024L * 1024 * 1024 * 1024 * 1024).contains("TB"));
    }

    @Test
    void modelIngestSkipped() {
        var skipped = WeightImporter.ModelIngest.skipped("test-model", "too large");
        assertFalse(skipped.isOk());
        assertEquals("too large", skipped.error());
    }

    @Test
    void modelIngestAllNeurons() {
        var skipped = WeightImporter.ModelIngest.skipped("test", "reason");
        assertTrue(skipped.allNeurons().isEmpty());
    }
}
