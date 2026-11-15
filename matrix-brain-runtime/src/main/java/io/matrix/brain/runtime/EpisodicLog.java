package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MIND-W3 — Episodic log (append-only NDJSON).
 *
 * <p>Records every MindCycle interaction as a single NDJSON line so the
 * consolidation cycle can replay the day's experience during sleep.
 * Backed by a file on disk; supports safe append + full read.</p>
 *
 * <p>CONSTITUTION Article III — timestamps are ISO-8601 UTC strings for
 * reproducibility across machines; entries are immutable.</p>
 */
public final class EpisodicLog {

    private static final Logger LOG = Logger.getLogger(EpisodicLog.class.getName());

    /** One episodic entry: a mind interaction. */
    public record Entry(
        String id,            // FNV-1a of input+response+ts
        String input,         // the user input
        String reply,         // the MindCycle reply
        double confidence,   // 0..1
        boolean accepted,     // false if modulators vetoed
        List<String> modulatorsFired,
        long tsMillis,
        EpisodeRole role      // EPISTEMIC-INTEGRITY P0.4: the provenance of the traffic
    ) {
        /**
         * The legacy shape: an interaction with no declared provenance.
         *
         * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F3): the default is
         * {@link EpisodeRole#LEGACY_UNTAGGED} — UNDECLARED, not organic. A record
         * whose provenance nobody stated must not carry a false provenance claim into
         * the permanent audit trail, and an absent declaration must not silently
         * disable learning either: {@code LEGACY_UNTAGGED} is learning-eligible, so
         * the record still enters the window. Only an explicit
         * {@link EpisodeRole#ORGANIC} declaration writes "organic".</p>
         */
        public Entry(String id, String input, String reply, double confidence,
                     boolean accepted, List<String> modulatorsFired, long tsMillis) {
            this(id, input, reply, confidence, accepted, modulatorsFired, tsMillis,
                EpisodeRole.LEGACY_UNTAGGED);
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder(64 + input.length() + reply.length());
            sb.append("{\"id\":\"").append(escape(id))
              .append("\",\"input\":\"").append(escape(input))
              .append("\",\"reply\":\"").append(escape(reply))
              .append("\",\"confidence\":").append(confidence)
              .append(",\"accepted\":").append(accepted)
              .append(",\"modulators\":[");
            boolean first = true;
            for (String m : modulatorsFired) {
                if (!first) sb.append(',');
                sb.append('"').append(escape(m)).append('"');
                first = false;
            }
            sb.append("],\"ts\":").append(tsMillis)
              .append(",\"role\":\"").append(escape(role == null
                    ? EpisodeRole.LEGACY_UNTAGGED.wire() : role.wire()))
              .append("\"}");
            return sb.toString();
        }
        static Entry fromJson(String line) {
            // RECON-W32.21: reject a line that is not a COMPLETE record. This parser is
            // a lenient field-extractor, so a torn trailing line — the one an
            // append-only log actually produces — used to parse into a plausible Entry
            // with whatever fields happened to precede the tear. That entry then went
            // into the read set and was offered for rule induction, so the mind learned
            // from a fragment. Shape is checked first, because a field extractor cannot
            // tell a truncated record from a complete one.
            if (line == null) return null;
            String t = line.trim();
            if (!t.startsWith("{") || !t.endsWith("}")) return null;
            String id = extractString(line, "id");
            String input = extractString(line, "input");
            String reply = extractString(line, "reply");
            double conf = extractNumber(line, "confidence");
            boolean acc = extractBool(line, "accepted");
            long ts = (long) extractNumber(line, "ts");
            List<String> mods = extractStringArray(line, "modulators");
            // EPISTEMIC-INTEGRITY P0.4: a record written before the role tag existed
            // reads back as LEGACY_UNTAGGED — never as ORGANIC. The old rows are not
            // rewritten; they are simply read with an honest label.
            EpisodeRole role = EpisodeRole.parse(extractString(line, "role"));
            return new Entry(id, input, reply, conf, acc, mods, ts, role);
        }
        private static String escape(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() + 4);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') sb.append('\\').append(c);
                else if (c == '\n') sb.append("\\n");
                else if (c == '\r') sb.append("\\r");
                else sb.append(c);
            }
            return sb.toString();
        }
        private static String extractString(String line, String key) {
            String marker = "\"" + key + "\":\"";
            int i = line.indexOf(marker);
            if (i < 0) return "";
            int s = i + marker.length();
            // Read until un-escaped closing quote.
            StringBuilder sb = new StringBuilder();
            while (s < line.length()) {
                char c = line.charAt(s);
                if (c == '\\' && s + 1 < line.length()) {
                    char n = line.charAt(s + 1);
                    if (n == 'n') sb.append('\n');
                    else if (n == 'r') sb.append('\r');
                    else sb.append(n);
                    s += 2;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    s++;
                }
            }
            return sb.toString();
        }
        private static double extractNumber(String line, String key) {
            String marker = "\"" + key + "\":";
            int i = line.indexOf(marker);
            if (i < 0) return 0.0;
            int s = i + marker.length();
            int e = s;
            while (e < line.length()) {
                char c = line.charAt(e);
                if (c == ',' || c == '}' || c == ' ') break;
                e++;
            }
            try { return Double.parseDouble(line.substring(s, e)); }
            catch (NumberFormatException ex) { return 0.0; }
        }
        private static boolean extractBool(String line, String key) {
            String marker = "\"" + key + "\":";
            int i = line.indexOf(marker);
            if (i < 0) return false;
            return line.indexOf("true", i + marker.length()) >= 0
                && line.indexOf("true", i + marker.length()) < i + marker.length() + 6;
        }
        @SuppressWarnings("unchecked")
        private static List<String> extractStringArray(String line, String key) {
            String marker = "\"" + key + "\":[";
            int i = line.indexOf(marker);
            if (i < 0) return List.of();
            int s = i + marker.length();
            int e = line.indexOf(']', s);
            String body = line.substring(s, e);
            List<String> out = new ArrayList<>();
            if (body.isBlank()) return out;
            // Split on commas not inside quotes — sufficient for our schema.
            int p = 0;
            boolean inQ = false;
            StringBuilder cur = new StringBuilder();
            while (p < body.length()) {
                char c = body.charAt(p);
                if (c == '"') {
                    inQ = !inQ;
                } else if (c == ',' && !inQ) {
                    String tok = cur.toString().trim();
                    if (tok.length() >= 2 && tok.startsWith("\"") && tok.endsWith("\"")) {
                        out.add(tok.substring(1, tok.length() - 1));
                    }
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
                p++;
            }
            String tok = cur.toString().trim();
            if (tok.length() >= 2 && tok.startsWith("\"") && tok.endsWith("\"")) {
                out.add(tok.substring(1, tok.length() - 1));
            }
            return out;
        }
    }

    private final Path logPath;
    /**
     * Every promotion decision is recorded here (W42.9 / D-016). A refusal was
     * previously only counted, so the reason a lesson did NOT become knowledge
     * was lost. The chain keeps it, and the chain is tamper-evident.
     */
    private final AuditChain auditChain;
    /**
     * EPISTEMIC-INTEGRITY P0.4 — the reversible quarantine that governs this log.
     * Bound to the log's sibling ledger file by default, so it works with NO
     * configuration: an absent ledger means "nothing is quarantined".
     */
    private final EpisodeQuarantine quarantine;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private long appendCount = 0;
    private long rejectedCount = 0;
    private long recordedNotAdmittedCount = 0;

    public EpisodicLog(Path logPath) {
        this(logPath, new AuditChain());
    }

    /** The log with an explicit chain, so a caller can read the decisions back. */
    public EpisodicLog(Path logPath, AuditChain auditChain) {
        this(logPath, auditChain, null);
    }

    /**
     * The log with an explicit chain and an explicit quarantine.
     *
     * <p>The quarantine is the mechanism that keeps an ALREADY-WRITTEN record out of
     * the learning window without touching its bytes. It defaults to the sibling
     * ledger file of this log.</p>
     */
    public EpisodicLog(Path logPath, AuditChain auditChain, EpisodeQuarantine quarantine) {
        this.logPath = logPath;
        this.auditChain = auditChain;
        this.quarantine = quarantine != null
            ? quarantine
            : new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
    }

    /** The quarantine that governs this log. */
    public EpisodeQuarantine quarantine() {
        return quarantine;
    }

    /**
     * Append one entry ONLY if it earns promotion.
     *
     * <p>RECON-W31.1 / EPI-1 + EPI-4. This method used to write every entry
     * unconditionally, which is how 941 of 1115 entries came to be frozen-battery
     * probes — including ET-1, the ethics probe whose correct behaviour is refusal. The
     * mind was training on its own test paper, and a memorised answer is
     * indistinguishable from a known one once it carries provenance and confidence.</p>
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 adds one more dimension: the record's
     * {@link EpisodeRole}. A record whose traffic is not organic (evaluation,
     * experiment, synthetic) is <b>written</b> — the audit trail keeps it, and a
     * refusal that leaves a durable trace is a correct refusal — but it is
     * <b>not promoted</b>, so it can never become knowledge or reach rule induction.
     * Every other rejection keeps its historic behaviour: not written at all.</p>
     *
     * @return the gate decision, so a caller can surface {@code reason} in its trace
     */
    public PromotionGate.Decision append(Entry e) {
        if (e == null) {
            PromotionGate.Decision denied = PromotionGate.Decision.deny(PromotionGate.Reason.EMPTY,
                "promoted=false reason=EMPTY (null entry)");
            auditChain.append("episode.rejected", "|EMPTY|" + denied.trace());
            return denied;
        }
        PromotionGate.Decision d = PromotionGate.evaluate(
            PromotionGate.Candidate.forInteraction(
                e.input(), e.reply(), e.confidence(), e.modulatorsFired(), e.accepted(),
                e.role()));
        auditChain.append(
            d.promoted() ? "episode.promoted"
                : (d.written() ? "episode.recorded-not-admitted" : "episode.rejected"),
            e.id() + "|" + d.reason().name() + "|" + d.trace());
        if (!d.written()) {
            lock.writeLock().lock();
            try { rejectedCount++; }
            finally { lock.writeLock().unlock(); }
            return d;
        }
        appendUnchecked(e);
        if (!d.promoted()) {
            lock.writeLock().lock();
            try { recordedNotAdmittedCount++; }
            finally { lock.writeLock().unlock(); }
        }
        return d;
    }

    /**
     * Append without consulting the promotion gate.
     *
     * <p>Exists for two callers only: the quarantine repair tooling, and tests that
     * are deliberately seeding historical state. It is deliberately NOT public — a
     * promotion path that bypasses its own gate is how the original defect happened,
     * so the number of callers is the thing being controlled, not the convenience.</p>
     */
    void appendUnchecked(Entry e) {
        lock.writeLock().lock();
        try {
            Files.createDirectories(logPath.getParent() == null
                ? Path.of(".") : logPath.getParent());
            String line = e.toJson() + "\n";
            Files.writeString(logPath, line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
            appendCount++;
        } catch (IOException ex) {
            throw new RuntimeException("EpisodicLog append failed: " + logPath, ex);
        } finally {
            lock.writeLock().unlock(); }
    }

    /**
     * Convenience: build an Entry with auto-id and current timestamp, then append
     * through the promotion gate.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F3): this overload declares NOTHING,
     * so the record it writes is {@link EpisodeRole#LEGACY_UNTAGGED} — an undeclared
     * record, not an organic one. It stays usable with no configuration (an undeclared
     * record is learning-eligible, so an absent declaration must not silently disable
     * learning), but it must not write a provenance claim nobody made. A caller that
     * KNOWS its traffic is organic says so with the six-argument overload.</p>
     *
     * @return the gate decision; the entry was written only if it was promoted
     */
    public PromotionGate.Decision append(String input, String reply, double confidence,
                                         boolean accepted, List<String> modulators) {
        return append(input, reply, confidence, accepted, modulators,
            EpisodeRole.LEGACY_UNTAGGED);
    }

    /**
     * The write path with a DECLARED provenance.
     *
     * <p>An evaluation or experiment caller declares what it is, and the gate then
     * keeps the record out of the learning feed while still recording it durably.
     * This is the seam that makes "an evaluation record is written durably but never
     * enters the learning window" a property of the system rather than a habit.</p>
     *
     * <p>A null role means UNDECLARED ({@link EpisodeRole#LEGACY_UNTAGGED}), never
     * organic.</p>
     */
    public PromotionGate.Decision append(String input, String reply, double confidence,
                                         boolean accepted, List<String> modulators,
                                         EpisodeRole role) {
        long ts = System.currentTimeMillis();
        String id = "ep-" + ts + "-" + Long.toHexString(fnv1a64(input + "|" + reply));
        return append(new Entry(id, input, reply, confidence, accepted, modulators, ts,
            role == null ? EpisodeRole.LEGACY_UNTAGGED : role));
    }

    /** The chain of the promotion decisions — the refusals included. */
    public AuditChain auditChain() { return auditChain; }

    /** Entries written to the log since construction. */
    public long appendedCount() {
        lock.readLock().lock();
        try { return appendCount; }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Entries refused by the promotion gate since construction. A non-zero value is
     * healthy operation, not an error: it is the gate doing its job.
     */
    public long rejectedCount() {
        lock.readLock().lock();
        try { return rejectedCount; }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Entries that were WRITTEN but refused as learning material since construction.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4. These are the evaluation, experiment and synthetic
     * records: durable, auditable, and never learnable. A non-zero value is the
     * isolation mechanism working, and it is reported separately from
     * {@link #rejectedCount()} because the two are different events: one was not
     * written, the other was written and kept out of the learning feed.</p>
     */
    public long recordedNotAdmittedCount() {
        lock.readLock().lock();
        try { return recordedNotAdmittedCount; }
        finally { lock.readLock().unlock(); }
    }

    // ---- The learning view (EPISTEMIC-INTEGRITY P0.4) ---------------------

    /**
     * Malformed lines skipped by the most recent {@link #readAll()}.
     *
     * <p>RECON-W32.21. An episode that cannot be read cannot be learned from, so this is
     * a learning-capacity number and not merely a parse statistic. A skipped episode
     * used to vanish with no count and no log, which meant the mind had silently lost
     * the interaction and nothing recorded that it ever had it.</p>
     *
     * <p>Unit: lines. Zero after a clean read.</p>
     */
    private volatile int lastSkippedLines = 0;

    /** Malformed lines skipped by the most recent read. Unit: lines. */
    public int lastSkippedLines() {
        return lastSkippedLines;
    }

    /** Skips printed individually before the rest are summarised. Unit: lines. */
    private static final int MAX_REPORTED_SKIPS = 10;

    /**
     * The AUDIT view: every record in the log, in the order it was written.
     *
     * <p>This is the complete history — contaminated records included. Nothing is
     * hidden and nothing is deleted.</p>
     */
    public List<Entry> readAll() {
        lock.readLock().lock();
        try {
            if (!Files.exists(logPath)) { lastSkippedLines = 0; return List.of(); }
            List<String> lines = Files.readAllLines(logPath);
            List<Entry> out = new ArrayList<>();
            int skipped = 0;
            for (String l : lines) {
                if (l.isBlank()) continue;
                Entry parsed = null;
                try {
                    // RECON-W32.21: fromJson rejects an incomplete record by shape, so a
                    // torn trailing line — the thing an append-only log actually
                    // produces — yields null instead of a plausible Entry built from
                    // whatever fields preceded the tear. Adding that null would put a
                    // null episode into the read set, and RealSleepScheduler reads this
                    // to induce rules: the mind would learn from a fragment.
                    parsed = Entry.fromJson(l);
                } catch (RuntimeException e) {
                    if (skipped < MAX_REPORTED_SKIPS) {
                        LOG.log(Level.WARNING,
                            "EpisodicLog: malformed episode in {0}: {1}",
                            new Object[]{logPath, e.getMessage()});
                    }
                }
                if (parsed == null) {
                    skipped++;
                } else {
                    out.add(parsed);
                }
            }
            lastSkippedLines = skipped;
            if (skipped > 0) {
                LOG.log(Level.WARNING,
                    "EpisodicLog: read {0} episodes from {1} and SKIPPED {2} malformed "
                        + "line(s); those interactions are NOT available for rule induction",
                    new Object[]{out.size(), logPath, skipped});
            }
            return out;
        } catch (IOException ex) {
            throw new RuntimeException("EpisodicLog read failed: " + logPath, ex);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try { return (int) appendCount; }
        finally { lock.readLock().unlock(); }
    }

    /**
     * The LEARNING view: the records this mind may learn from.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4. A record is admitted when its
     * {@link EpisodeRole} is learning-eligible and its id is not named in the
     * {@link EpisodeQuarantine} ledger. Everything else — evaluation records,
     * experiment records, quarantined legacy records — is excluded here and stays in
     * {@link #readAll()}.</p>
     */
    public List<Entry> learningCandidates() {
        return LearningAdmission.assess(this, quarantine).candidates();
    }

    /**
     * The production learning window: the last {@link KMaxEnforcer#K_MAX} admitted
     * records.
     *
     * <p>The recency selector and the size cap are the production ones; only the
     * eligibility rule is new. The window therefore reaches further back into the
     * organic history instead of stopping at the contamination.</p>
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F8): this is the ONE definition of the
     * window — {@link LearningAdmission#learningWindow} — and the scheduler and the
     * operator tool go through it too.</p>
     */
    public List<Entry> learningWindow() {
        return LearningAdmission.learningWindow(this, quarantine, KMaxEnforcer.K_MAX);
    }

    public Path path() {
        return logPath;
    }

    private static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
