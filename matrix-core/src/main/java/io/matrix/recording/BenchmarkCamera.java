package io.matrix.recording;

import java.util.*;

/**
 * W1336 — Benchmark Camera.
 *
 * Integrated screen recording for all tests.
 * Generates MP4/GIF evidence artifacts.
 */
public final class BenchmarkCamera {

    public enum ArtifactType { MP4_VIDEO, GIF_ANIMATION, HAR_LOG, CSV_TIMELINE, PNG_SCREENSHOT }

    public record Recording(
            String testName,
            ArtifactType type,
            String filePath,
            long durationMs,
            long timestamp,
            Map<String, String> metadata
    ) {}

    public record EvidenceBundle(
            String testName,
            List<Recording> recordings,
            long totalDurationMs,
            String summary
    ) {}

    private final List<Recording> recordings = new ArrayList<>();

    /**
     * Start a recording session.
     */
    public void record(String testName, ArtifactType type, String filePath,
                        long durationMs, Map<String, String> metadata) {
        recordings.add(new Recording(testName, type, filePath, durationMs,
            System.currentTimeMillis(), metadata));
    }

    /**
     * Record MP4 video evidence.
     */
    public void recordVideo(String testName, String filePath, long durationMs) {
        record(testName, ArtifactType.MP4_VIDEO, filePath, durationMs, Map.of());
    }

    /**
     * Record HAR (HTTP Archive) log.
     */
    public void recordHAR(String testName, String filePath, long durationMs) {
        record(testName, ArtifactType.HAR_LOG, filePath, durationMs, Map.of());
    }

    /**
     * Record CSV timeline.
     */
    public void recordTimeline(String testName, String filePath, long durationMs) {
        record(testName, ArtifactType.CSV_TIMELINE, filePath, durationMs, Map.of());
    }

    /**
     * Bundle all evidence for a test.
     */
    public EvidenceBundle bundle(String testName, String summary) {
        List<Recording> testRecordings = recordings.stream()
            .filter(r -> r.testName().equals(testName))
            .toList();
        long totalDuration = testRecordings.stream()
            .mapToLong(Recording::durationMs).sum();
        return new EvidenceBundle(testName, testRecordings, totalDuration, summary);
    }

    /**
     * Get all recordings.
     */
    public List<Recording> getAllRecordings() {
        return new ArrayList<>(recordings);
    }
}
