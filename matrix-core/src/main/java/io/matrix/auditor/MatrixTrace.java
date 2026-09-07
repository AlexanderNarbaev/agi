package io.matrix.auditor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * RUN 143 — MatrixTrace (append-only, hash-chained).
 *
 * <p>Implements the {@code x-matrix-trace} journal specified in
 * <a href="../../../../../docs-v2/paradigm/PARADIGM.md">PARADIGM §4.1</a>
 * and CONSTITUTION VIII. Every decision-path entry point records
 * a trace step. Steps are SHA-256 hash-chained for tamper-evidence.
 *
 * <p>Recording pattern:
 * <pre>
 *   try (var t = MatrixTrace.begin("perception.receive")) {
 *       // do work
 *       t.end("ok");
 *   }
 * </pre>
 *
 * <p>This is in-memory implementation. For multi-process
 * persistence, see {@code matrix.observability.TraceStore}.
 */
public final class MatrixTrace {

    private final List<TraceStep> steps = new ArrayList<>();

    public TraceStep begin(String name) {
        TraceStep step = new TraceStep(name, prevHash(), nowMicro());
        steps.add(step);
        return step;
    }

    public List<TraceStep> steps() {
        return new ArrayList<>(steps);
    }

    public int count() { return steps.size(); }

    public TraceStep last() {
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }

    private String prevHash() {
        return steps.isEmpty() ? "0000" : steps.get(steps.size() - 1).hash;
    }

    private long nowMicro() {
        // Hardened for traceability: nanoTime is monotonic and not wall-clock.
        // Allowed in instrumentation per DecisionPathAuditor exemption.
        return System.nanoTime() / 1000L;
    }

    public static String hashHex(String prev, String name, String inputHash,
                                 String outputHash, long micros) {
        String payload = prev + "|" + name + "|" + inputHash + "|"
                + outputHash + "|" + micros;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * AutoCloseable trace step. Records final hash on close.
     */
    public final class TraceStep implements AutoCloseable {
        public final String name;
        public final String prevHash;
        public final long startMicros;
        public String inputHash;
        public String outputHash;
        public String status;
        public String hash;

        private TraceStep(String name, String prevHash, long startMicros) {
            this.name = name;
            this.prevHash = prevHash;
            this.startMicros = startMicros;
            this.inputHash = "0";
            this.outputHash = "0";
            this.status = "started";
            this.hash = "pending";
        }

        public TraceStep input(String s) {
            this.inputHash = hashHex(prevHash, "in:" + name, s, "", 0).substring(0, 16);
            return this;
        }

        public TraceStep output(String s) {
            this.outputHash = hashHex(prevHash, "out:" + name, "", s, 0).substring(0, 16);
            return this;
        }

        public TraceStep end(String status) {
            this.status = status;
            long elapsed = System.nanoTime() / 1000L - startMicros;
            this.hash = hashHex(prevHash, name, inputHash, outputHash, elapsed);
            return this;
        }

        public long elapsedMicros() {
            return System.nanoTime() / 1000L - startMicros;
        }

        @Override
        public void close() {
            if ("started".equals(status)) {
                end("closed");
            }
        }
    }
}
