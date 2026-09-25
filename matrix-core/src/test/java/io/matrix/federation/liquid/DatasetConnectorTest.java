package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W582 — Tests for Dataset Connector.
 */
class DatasetConnectorTest {

    @Test
    void testLoadCsv(@TempDir Path tempDir) throws IOException {
        // Create test CSV
        Path csvFile = tempDir.resolve("test.csv");
        Files.writeString(csvFile, "id,question,answer\n1,What is 1+1?,2\n2,What is 2+2?,4\n");

        DatasetConnector connector = new DatasetConnector(tempDir);
        List<DatasetConnector.DatasetEntry> entries = connector.loadCsv("test.csv", 1, 2);

        assertEquals(2, entries.size());
        assertEquals("What is 1+1?", entries.get(0).question());
        assertEquals("2", entries.get(0).answer());
    }

    @Test
    void testLoadJsonl(@TempDir Path tempDir) throws IOException {
        // Create test JSONL
        Path jsonlFile = tempDir.resolve("test.jsonl");
        Files.writeString(jsonlFile,
                "{\"question\":\"What is AI?\",\"answer\":\"Artificial Intelligence\"}\n" +
                "{\"question\":\"What is ML?\",\"answer\":\"Machine Learning\"}\n");

        DatasetConnector connector = new DatasetConnector(tempDir);
        List<DatasetConnector.DatasetEntry> entries = connector.loadJsonl("test.jsonl");

        assertEquals(2, entries.size());
        assertEquals("What is AI?", entries.get(0).question());
        assertEquals("Artificial Intelligence", entries.get(0).answer());
    }

    @Test
    void testLoadText(@TempDir Path tempDir) throws IOException {
        // Create test text file
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "Line 1\nLine 2\nLine 3\n");

        DatasetConnector connector = new DatasetConnector(tempDir);
        List<DatasetConnector.DatasetEntry> entries = connector.loadText("test.txt");

        assertEquals(3, entries.size());
        assertEquals("Line 1", entries.get(0).question());
    }

    @Test
    void testListDatasets(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("a.csv"), "test");
        Files.writeString(tempDir.resolve("b.jsonl"), "test");

        DatasetConnector connector = new DatasetConnector(tempDir);
        List<String> datasets = connector.listDatasets();

        assertEquals(2, datasets.size());
        assertTrue(datasets.contains("a.csv"));
        assertTrue(datasets.contains("b.jsonl"));
    }

    @Test
    void testDatasetExists(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("exists.csv"), "test");

        DatasetConnector connector = new DatasetConnector(tempDir);
        assertTrue(connector.datasetExists("exists.csv"));
        assertFalse(connector.datasetExists("notexists.csv"));
    }
}
