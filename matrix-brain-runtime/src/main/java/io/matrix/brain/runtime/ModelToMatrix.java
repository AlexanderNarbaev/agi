package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MIND-W5 — ModelToMatrix distillation pipeline.
 *
 * <p>Offline tool (NOT in the runtime mind path per CONSTITUTION Article I)
 * that ingests a small ONNX model file (or a flat dataset of facts) and
 * compiles its knowledge into matrix-native structures:</p>
 * <ul>
 *   <li><b>HDC codebook</b> via {@link PersistentHdcStore#teach(String, String)} —
 *       tokenized embeddings become HDC vectors.</li>
 *   <li><b>BIR boolean clauses</b> inferred via a simple (head, relation, tail) extractor.</li>
 *   <li><b>Tsetlin literals</b> counted per unique token (placeholder for W6).</li>
 * </ul>
 *
 * <p>After distillation, the resulting structures live inside the
 * {@link PersistentHdcStore} AND {@link EpisodicLog}; the source ONNX file
 * is now inert data (CONSTITUTION Article I: zero LLM in runtime).</p>
 *
 * <p>Super-additivity: a distilled corpus A and corpus B merged into the
 * same live matrix must score &ge; max(score(A), score(B)) on a fixed eval.</p>
 */
public final class ModelToMatrix {

    /** Per-source distillation report. */
    public record Report(
        String source,
        int tokensExtracted,
        int hdcPromoted,
        int birClausesInduced,
        int tsetlinLiterals,
        long inputsBytes,
        long durationMs,
        String artifactHash
    ) {}

    /** Eval function: takes a PersistentHdcStore and returns a 0..1 score. */
    @FunctionalInterface
    public interface EvalFn {
        double score(PersistentHdcStore store);
    }

    /** Distillation target — what kind of source we're processing. */
    public enum SourceType { ONNX, DATASET, TEXT_CORPUS }

    /**
     * Distill a small ONNX file's metadata into the live matrix.
     *
     * <p>We do NOT execute the ONNX graph at runtime (CONSTITUTION I).
     * We extract initializer names + op names + output names as a token
     * stream and feed it to the HDC store. The result is a coarse but
     * reproducible fingerprint of the model in the live matrix.</p>
     *
     * @param onnxPath   path to a small ONNX file (model.onnx)
     * @param hdcStore   destination persistent HDC store
     * @param ledger     ledger to append the run to
     * @return a {@link Report} summarising what was promoted
     */
    public Report distillOnnx(Path onnxPath, PersistentHdcStore hdcStore,
                              DistillationLedger ledger) throws IOException {
        long t0 = System.currentTimeMillis();
        byte[] bytes = Files.readAllBytes(onnxPath);
        String text = new String(bytes, StandardCharsets.UTF_8);
        // ONNX is protobuf; we extract quoted strings as a coarse token stream.
        List<String> tokens = extractQuotedStrings(text);
        return finalizeRun("onnx:" + onnxPath.getFileName(), tokens, bytes.length,
            t0, hdcStore, ledger);
    }

    /**
     * Distill a flat fact dataset (one fact per line) into the live matrix.
     *
     * @param lines     one fact per line
     * @param hdcStore  destination
     * @param ledger    ledger
     */
    public Report distillDataset(List<String> lines, PersistentHdcStore hdcStore,
                                 DistillationLedger ledger) {
        long t0 = System.currentTimeMillis();
        long totalBytes = 0;
        for (String l : lines) totalBytes += l.length();
        return finalizeRun("dataset:inline", lines, totalBytes, t0, hdcStore, ledger);
    }

    /** Common path: turn a token stream into HDC + BIR + Tsetlin entries. */
    private Report finalizeRun(String source, List<String> tokens, long inputsBytes,
                               long t0, PersistentHdcStore hdcStore,
                               DistillationLedger ledger) {
        // 1) HDC promotion: every non-trivial token gets an entry.
        int hdcPromoted = 0;
        java.util.Set<String> seenTokens = new java.util.HashSet<>();
        for (String tok : tokens) {
            String t = tok == null ? "" : tok.trim();
            if (t.isEmpty() || t.length() < 3 || t.length() > 80) continue;
            if (!seenTokens.add(t)) continue;
            String id = "distill-" + Long.toHexString(fnv1a64(t));
            hdcStore.teach(id, t);
            hdcPromoted++;
        }

        // 2) BIR clauses: extract (head, relation, tail) triples from sentences
        //    that look like "X is Y of Z" or "X has Y".
        int birClauses = 0;
        Pattern triplePattern = Pattern.compile(
            "(?i)\\b([A-Za-z][A-Za-z\\-]{1,30})\\s+(?:is|are|was)\\s+(?:the\\s+)?([A-Za-z][A-Za-z\\-]{1,30})\\s+(?:of|in|at|for)\\s+([A-Za-z][A-Za-z\\-]{1,30})\\b"
        );
        for (String tok : tokens) {
            Matcher m = triplePattern.matcher(tok);
            while (m.find()) {
                String head = m.group(1).toLowerCase();
                String rel = m.group(2).toLowerCase();
                String tail = m.group(3).toLowerCase();
                String clause = "BIR:" + head + ":" + rel + ":" + tail;
                hdcStore.teach("bir-" + Long.toHexString(fnv1a64(clause)),
                    "triple " + clause);
                birClauses++;
            }
        }

        // 3) Tsetlin literals: just count unique tokens (placeholder for W6)
        int tsetlinLiterals = seenTokens.size();

        long durationMs = System.currentTimeMillis() - t0;
        String artifactHash = sha256Hex(source + ":" + hdcPromoted + ":"
            + birClauses + ":" + tsetlinLiterals + ":" + inputsBytes);

        Report r = new Report(source, tokens.size(), hdcPromoted,
            birClauses, tsetlinLiterals, inputsBytes, durationMs, artifactHash);
        ledger.append(new DistillationLedger.Entry(
            "run-" + System.currentTimeMillis() + "-" + Long.toHexString(fnv1a64(source)),
            source, tokens.size(), inputsBytes, hdcPromoted, birClauses,
            tsetlinLiterals, 0.0 /* eval_delta filled by caller */,
            durationMs, System.currentTimeMillis(), artifactHash));
        return r;
    }

    /** Extract quoted strings (used as a coarse token stream for ONNX). */
    private static List<String> extractQuotedStrings(String s) {
        List<String> out = new ArrayList<>();
        Pattern p = Pattern.compile("\"([^\"\\\\]{2,80})\"");
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** FNV-1a 64-bit hash. */
    private static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "no-sha256";
        }
    }
}
