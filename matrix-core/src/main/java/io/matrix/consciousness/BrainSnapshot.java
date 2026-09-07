package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * RUN 183 — BrainSnapshot (save/restore brain state).
 *
 * <p>Captures arousal, trace hash chain, and impulse counter.
 * Persists as JSON for portability.
 *
 * <p>Deterministic: same state → same bytes.
 */
public final class BrainSnapshot {

    public record Snapshot(double arousal,
                           int traceCount,
                           String traceHead,
                           String traceTail,
                           long createdAtMillis) {}

    public static Snapshot capture(BrainLoopService svc) {
        MatrixTrace t = svc.trace();
        int count = t.count();
        String head = count == 0 ? "" : t.steps().get(0).hash;
        String tail = count == 0 ? "" : t.last().hash;
        return new Snapshot(svc.arousal(), count, head, tail, System.nanoTime() / 1_000_000L);
    }

    public static String toJson(Snapshot s) {
        return "{" +
                "\"arousal\":" + s.arousal() +
                ",\"traceCount\":" + s.traceCount() +
                ",\"traceHead\":\"" + s.traceHead() + "\"" +
                ",\"traceTail\":\"" + s.traceTail() + "\"" +
                ",\"createdAtMillis\":" + s.createdAtMillis() +
                "}";
    }

    public static Snapshot fromJson(String json) {
        double arousal = Double.parseDouble(jsonRead(json, "arousal"));
        int traceCount = Integer.parseInt(jsonRead(json, "traceCount"));
        String head = jsonRead(json, "traceHead");
        String tail = jsonRead(json, "traceTail");
        long created = Long.parseLong(jsonRead(json, "createdAtMillis"));
        return new Snapshot(arousal, traceCount, head, tail, created);
    }

    public static void save(Path file, Snapshot s) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, toJson(s), StandardCharsets.UTF_8);
    }

    public static Snapshot load(Path file) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static String jsonRead(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return "";
        int colon = json.indexOf(":", idx);
        if (colon < 0) return "";
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '"') end++;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && "0123456789-.e".indexOf(json.charAt(end)) >= 0) end++;
            return json.substring(start, end);
        }
    }
}
