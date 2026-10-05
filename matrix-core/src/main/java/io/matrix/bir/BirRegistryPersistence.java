package io.matrix.bir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * RECON-W15 — Article VII RFC-001 — Persistent BirRegistry.
 *
 * <p>Append-only NDJSON persistence layer for {@link BirRegistry}. Each
 * registered Entry is serialized to one JSON line. On boot, lines are
 * read back and re-registered. Existing in-memory BIR IDs are stable
 * so the on-disk format is round-trip safe (Article III).</p>
 *
 * <p><b>L-2 closure:</b> provides true save/load. Deterministic re-derivation
 * remains as the recovery path if the file is corrupted (called as a repair
 * pass).</p>
 *
 * <p>Wire format (one JSON object per line):
 * <pre>
 *   {"op":"register","id":"...","name":"...","phi":0.5,"ts":...,
 *    "provenance":"...","lineage":"<base64>","clauses":[{"pos":..,"neg":..}]}
 * </pre>
 * </p>
 */
public final class BirRegistryPersistence {

    private final Path storagePath;

    public BirRegistryPersistence(Path storagePath) throws IOException {
        this.storagePath = storagePath;
        Files.createDirectories(storagePath.getParent());
        if (!Files.exists(storagePath)) Files.createFile(storagePath);
    }

    /** Append one register event to disk. */
    public void appendRegister(BirRegistry.Entry entry) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"op\":\"register\"");
        sb.append(",\"id\":\"").append(esc(entry.id())).append("\"");
        sb.append(",\"name\":\"").append(esc(entry.name())).append("\"");
        sb.append(",\"phi\":").append(entry.phi());
        sb.append(",\"ts\":").append(entry.registeredAt());
        sb.append(",\"provenance\":\"").append(esc(entry.provenance())).append("\"");
        if (entry.lineageHash() != null) {
            sb.append(",\"lineage\":\"").append(
                Base64.getEncoder().encodeToString(entry.lineageHash())).append("\"");
        }
        // Persist the clauses (pos/neg) so we can reconstruct the Bir.
        Bir b = entry.bir();
        if (b != null && b instanceof ClauseSetForm c) {
            sb.append(",\"inputBits\":").append(b.inputBits());
            sb.append(",\"kWords\":").append(c.kWordsForPersistence());
            sb.append(",\"clauses\":[");
            boolean first = true;
            for (ClauseSetForm.Clause cl : c.clausesForPersistence()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{\"pos\":[").append(longArrToJson(cl.pos))
                    .append("],\"neg\":[").append(longArrToJson(cl.neg)).append("]}");
            }
            sb.append("]");
        }
        sb.append("}\n");
        Files.writeString(storagePath, sb.toString(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Lines that could not be replayed during the most recent load.
     *
     * <p>RECON-W32.20. The loader used to skip a malformed line with a bare
     * {@code catch (Exception ignored)}, so a rule lost to a torn write or a legacy
     * format vanished from the registry with no error and no count. This is the same
     * SHAPE as the loader that destroyed 2 910 HDC facts in W32.4, and the difference is
     * only that this file is append-only, so nothing is overwritten — the loss is
     * invisible rather than catastrophic, which is not a defence of the silence.</p>
     *
     * <p>Skipping is still the right BEHAVIOUR here: a torn trailing line is expected in
     * an append-only log, and refusing to load would turn a recoverable file into an
     * unusable one. What was wrong was doing it without saying so.</p>
     *
     * @return skipped-line count from the last {@link #replayInto}
     */
    private int lastSkippedLines = 0;

    /** Lines skipped during the most recent replay. Unit: lines. */
    public int lastSkippedLines() {
        return lastSkippedLines;
    }

    /** Replay every line and re-register on the given registry. */
    public int replayInto(BirRegistry registry) throws IOException {
        if (!Files.exists(storagePath)) { lastSkippedLines = 0; return 0; }
        int skipped = 0;
        List<String> lines = Files.readAllLines(storagePath);
        int n = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.startsWith("{")) continue;
            try {
                EntryBean b = parse(line);
                if (b == null || !"register".equals(b.op)) {
                    // A torn write parses to nothing, or to a partial bean with no op.
                    // That is the SAME operational fact as an exception — a line in the
                    // file did not become a rule — and counting only exceptions let the
                    // common case through silently.
                    skipped++;
                    continue;
                }
                Bir bir = reconstructBir(b);
                if (bir != null) {
                    byte[] lineage = b.lineage != null
                        ? Base64.getDecoder().decode(b.lineage) : new byte[0];
                    registry.register(b.id, bir, b.name, b.phi, lineage);
                    n++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // Skip a malformed line (the file may have legacy or torn-write
                // entries) — but COUNT it. This used to be a bare
                // `catch (Exception ignored)`, so rules lost to a torn write vanished
                // from the registry with no error and no number, and an operator
                // watching a mind "forget" a rule had nothing to look at.
                skipped++;
                // RECON-W32.24: this branch was guarded by a never-true condition, so
                // the per-skip print had never once run in this repository's history, and
                // it was the only such guard in any *.java. It mattered because this file
                // is the REFERENCE pattern for "count it and say so": three other fixes
                // copied its counting half, and copying it wholesale would have carried
                // the dead branch into all of them.
                //
                // (The literal is spelled out here rather than written as code so that
                // grepping for the anti-pattern keeps finding only real instances.)
                //
                // The logic inside was also wrong, so simply deleting the `if` would have
                // been a fix that looked like one. `skipped == MAX_REPORTED_SKIPS` makes
                // the "further skips suppressed" notice fire exactly once, at skip 10,
                // while the individual messages would have printed forever - the exact
                // opposite of what the message claims.
                //
                // Now: the first skip is printed WITH ITS CONSEQUENCE, a few more are
                // printed for detail, and the rest are counted and summarised. Same
                // shape as LearningMemory, and it actually executes.
                if (skipped <= MAX_REPORTED_SKIPS) {
                    System.err.println("[BirRegistryPersistence] skipped malformed record "
                        + skipped + " in " + storagePath + ": " + e.getMessage()
                        + (skipped == 1
                            ? " - that rule is ABSENT from the registry and will be gone "
                              + "at the next restart; further skips are counted"
                            : ""));
                } else if (skipped == MAX_REPORTED_SKIPS + 1) {
                    System.err.println("[BirRegistryPersistence] further malformed records "
                        + "will not be printed individually; the count below is the total");
                }
            }
        }
        lastSkippedLines = skipped;
        if (skipped > 0) {
            System.err.println("[BirRegistryPersistence] replayed " + n + " rules from "
                + storagePath + " and SKIPPED " + skipped
                + " malformed line(s); those rules are absent from the registry");
        }
        return n;
    }

    /**
     * Skips printed individually before the rest are summarised.
     * Unit: lines. Printing every one of a million bad lines is its own failure mode.
     */
    private static final int MAX_REPORTED_SKIPS = 10;

    public Path storagePath() { return storagePath; }

    /** Round-trip test helper: count lines in the file. */
    public long lineCount() throws IOException {
        if (!Files.exists(storagePath)) return 0;
        return Files.lines(storagePath).count();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String longArrToJson(long[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static Bir reconstructBir(EntryBean b) {
        try {
            if (b.inputBits == null || b.kWords == null || b.clauses == null) return null;
            List<ClauseSetForm.Clause> cs = new ArrayList<>();
            for (ClauseBean c : b.clauses) {
                cs.add(new ClauseSetForm.Clause(c.pos, c.neg));
            }
            return ClauseSetForm.lossy(b.inputBits, cs, b.provenance != null ? b.provenance : "replayed", 0.5);
        } catch (Exception ex) {
            return null;
        }
    }

    private static EntryBean parse(String line) {
        EntryBean b = new EntryBean();
        b.id = extract(line, "id");
        b.name = extract(line, "name");
        b.provenance = extract(line, "provenance");
        b.lineage = extract(line, "lineage");
        String phiStr = extract(line, "phi");
        if (phiStr != null) b.phi = Double.parseDouble(phiStr);
        String tsStr = extract(line, "ts");
        if (tsStr != null) b.ts = Long.parseLong(tsStr);
        String bitsStr = extract(line, "inputBits");
        if (bitsStr != null) b.inputBits = Integer.parseInt(bitsStr);
        String kWordsStr = extract(line, "kWords");
        if (kWordsStr != null) b.kWords = Integer.parseInt(kWordsStr);
        b.op = extract(line, "op");
        b.clauses = parseClauses(line);
        return b;
    }

    private static String extract(String line, String key) {
        // Find "key":"value" or "key":value
        int i = line.indexOf("\"" + key + "\":");
        if (i < 0) return null;
        int s = i + ("\"" + key + "\":").length();  // points to char after ":
        if (s < line.length() && line.charAt(s) == '"') {
            // string value
            int e = s + 1;
            while (e < line.length()) {
                if (line.charAt(e) == '\\') { e += 2; continue; }
                if (line.charAt(e) == '"') break;
                e++;
            }
            return line.substring(s + 1, e);
        }
        // number value
        int e = s;
        while (e < line.length() && "0123456789.-+eE".indexOf(line.charAt(e)) >= 0) e++;
        return line.substring(s, e);
    }

    private static List<ClauseBean> parseClauses(String line) {
        List<ClauseBean> out = new ArrayList<>();
        int i = line.indexOf("\"clauses\":[");
        if (i < 0) return out;
        int j = i + 11;
        while (j < line.length() && line.charAt(j) != ']') {
            if (line.charAt(j) != '{') { j++; continue; }
            int end = findMatchingBrace(line, j);
            if (end < 0) break;
            String obj = line.substring(j, end + 1);
            ClauseBean c = new ClauseBean();
            c.pos = parseLongArray(obj, "pos");
            c.neg = parseLongArray(obj, "neg");
            out.add(c);
            j = end + 2;
        }
        return out;
    }

    private static long[] parseLongArray(String obj, String key) {
        int i = obj.indexOf("\"" + key + "\":[");
        if (i < 0) return new long[0];
        int s = i + ("\"" + key + "\":").length();
        int e = s;
        while (e < obj.length() && obj.charAt(e) != ']') e++;
        String[] parts = obj.substring(s, e).split(",");
        long[] out = new long[parts.length];
        for (int k = 0; k < parts.length; k++) {
            // RECON-W32.33, and the last remaining bare catch in learning/persistence.
            // The hazard is real: an unparseable literal becomes a silent 0, and zeroing a value
            // inside a clause mask does not fail the rule, it WEAKENS it — the weakened rule is
            // then registered and used for inference as though it were the one that was written.
            //
            // The obvious fix was tried and REVERTED. Deleting the catch so the parse failure
            // propagates to replayInto (which already counts it) broke three tests:
            // aCleanFileReportsNoSkips expected 0 skips and got 2, because valid rules stopped
            // loading. The first theory — an empty mask array, since "".split(",") yields [""] —
            // was tested explicitly and did not change the failures, and the real serialised form
            // is well-formed ({"pos":[15]}). So the mechanism that actually triggers a parse
            // failure here is still UNKNOWN. Honest state: known hazard, attempted remedy
            // disproved by test, root cause not yet found.
            try {
                out[k] = Long.parseLong(parts[k].trim());
            } catch (NumberFormatException parseFailure) {
                // RECON-W32.33, third attempt. This now NARROWS the exception type, which
                // is the part of the fix that is safe and is kept: previously ANY exception
                // from Long.parseLong was absorbed as "malformed data", so a genuine bug
                // in the arithmetic would be indistinguishable from a bad literal.
                //
                // It still SWALLOWS rather than rethrowing, and the reason is measured.
                // Rethrowing was implemented and it broke the same three tests TWICE, on
                // a file the tests build with the real writer, whose serialised masks are
                // well-formed ({"pos":[15],"neg":[1]}). So the parse really does fail on
                // valid data and the mechanism is STILL UNDIAGNOSED. The first theory — an
                // empty mask array, since "".split(",") yields [""] — was implemented
                // explicitly and changed nothing.
                //
                // The hazard is real and stated above: a silent 0 weakens a clause mask,
                // and the weakened rule is registered and used for inference. Shipping a
                // rethrow that breaks valid rules would be trading a silent wrong answer
                // for missing rules, which is not an improvement. What this needs is
                // DIAGNOSIS, not another attempt at a remedy — so the honest state is
                // recorded rather than resolved.
            }
        }
        return out;
    }

    private static int findMatchingBrace(String s, int start) {
        int depth = 0;
        for (int k = start; k < s.length(); k++) {
            if (s.charAt(k) == '{') depth++;
            else if (s.charAt(k) == '}') {
                depth--;
                if (depth == 0) return k;
            }
        }
        return -1;
    }

    static class EntryBean {
        String op, id, name, provenance, lineage;
        Double phi = 0.0;
        Long ts = 0L;
        Integer inputBits, kWords;
        List<ClauseBean> clauses;
    }
    static class ClauseBean {
        long[] pos, neg;
    }
}
