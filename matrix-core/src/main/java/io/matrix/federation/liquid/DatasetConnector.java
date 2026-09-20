package io.matrix.federation.liquid;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * W582 — Dataset Connector for HuggingFace datasets.
 *
 * Supports loading datasets from CSV/JSON files.
 * Focus on logical/reasoning datasets (logiqa, clutrr).
 */
public final class DatasetConnector {

    private final Path dataDir;

    public DatasetConnector(Path dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * A dataset entry.
     */
    public record DatasetEntry(
            String id,
            String question,
            String answer,
            String domain,
            Map<String, String> metadata
    ) {}

    /**
     * Load a CSV dataset.
     *
     * @param filename CSV filename
     * @param questionColumn column index for question
     * @param answerColumn   column index for answer
     * @return list of entries
     */
    public List<DatasetEntry> loadCsv(String filename, int questionColumn, int answerColumn) throws IOException {
        Path path = dataDir.resolve(filename);
        List<DatasetEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String header = reader.readLine(); // Skip header
            String line;
            int id = 0;

            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length > Math.max(questionColumn, answerColumn)) {
                    entries.add(new DatasetEntry(
                            String.valueOf(id++),
                            parts[questionColumn].trim(),
                            parts[answerColumn].trim(),
                            "csv",
                            Map.of()
                    ));
                }
            }
        }

        return entries;
    }

    /**
     * Load a JSONL dataset.
     *
     * @param filename JSONL filename
     * @return list of entries
     */
    public List<DatasetEntry> loadJsonl(String filename) throws IOException {
        Path path = dataDir.resolve(filename);
        List<DatasetEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int id = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String question = extractJsonField(line, "question");
                String answer = extractJsonField(line, "answer");
                String domain = extractJsonField(line, "domain");

                if (!question.isEmpty() && !answer.isEmpty()) {
                    entries.add(new DatasetEntry(
                            String.valueOf(id++),
                            question,
                            answer,
                            domain.isEmpty() ? "jsonl" : domain,
                            Map.of()
                    ));
                }
            }
        }

        return entries;
    }

    /**
     * Load a text dataset (one entry per line).
     *
     * @param filename text filename
     * @return list of entries
     */
    public List<DatasetEntry> loadText(String filename) throws IOException {
        Path path = dataDir.resolve(filename);
        List<DatasetEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int id = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    entries.add(new DatasetEntry(
                            String.valueOf(id++),
                            line,
                            "",
                            "text",
                            Map.of()
                    ));
                }
            }
        }

        return entries;
    }

    /**
     * Get available datasets in the data directory.
     */
    public List<String> listDatasets() throws IOException {
        if (!Files.exists(dataDir)) return List.of();

        List<String> datasets = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    datasets.add(path.getFileName().toString());
                }
            }
        }
        return datasets;
    }

    /**
     * Check if a dataset file exists.
     */
    public boolean datasetExists(String filename) {
        return Files.exists(dataDir.resolve(filename));
    }

    // Simple CSV line parser
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }

    // Simple JSON field extractor
    private static String extractJsonField(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) return "";
        start += needle.length();
        int end = json.indexOf('"', start);
        if (end < 0) return "";
        return json.substring(start, end);
    }
}
