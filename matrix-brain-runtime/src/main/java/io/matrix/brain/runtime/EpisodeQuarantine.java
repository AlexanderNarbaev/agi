package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the reversible quarantine of already-contaminated records.
 *
 * <p><b>Why this class exists.</b> The real episodic log of this machine grew from
 * 108 records to 226: a collection harness wrote 118 deliberately constructed
 * executions into the production log through the production write path, and two
 * frozen evaluation probes ({@code stolitsa frantsii}, {@code Moskva stolitsa Rossii?})
 * were already there. Those records must not be silently deleted and their history
 * must not be rewritten — the log is the audit trail, and a deletion is
 * indistinguishable from a cover-up. But they must also stop being learning
 * material.</p>
 *
 * <p><b>The mechanism.</b> A quarantine is a <em>mark</em>, not an edit. It lives in a
 * separate append-only ledger file next to the log; the log rows themselves are never
 * touched. The ledger is a sequence of events:</p>
 * <pre>
 * {"op":"QUARANTINE","id":"ep-...","reason":"...","ts":1763...}
 * {"op":"RELEASE","id":"ep-...","reason":"...","ts":1763...}
 * </pre>
 * <p>The current state of a record is the <b>last</b> event naming it, so a quarantine
 * is reversed by appending a {@code RELEASE} — the ledger is append-only, so the
 * quarantine itself stays in the history and the reversal is auditable too. Nothing is
 * ever rewritten; a reader can reconstruct every state the ledger has ever been in.</p>
 *
 * <p><b>Backward compatibility.</b> An absent ledger file means "nothing is
 * quarantined" — the admission gate then admits every role-eligible record, which is
 * exactly the behaviour before this class existed. An unreadable or malformed ledger
 * line is <em>counted and reported</em> ({@link #lastSkippedLines()}), never silently
 * dropped: a quarantine mark that cannot be read is a record that could silently
 * re-enter the learning feed.</p>
 *
 * <p><b>Article III.</b> Reading and writing the ledger involves no clock in a decision
 * path: {@link #quarantine(String, String)} and {@link #release(String, String)} take
 * the timestamp from the caller. The state of the ledger is a pure function of its
 * bytes.</p>
 */
public final class EpisodeQuarantine {

    /** The ledger's own file name, next to the episodic log it governs. */
    public static final String LEDGER_FILE_NAME = "episodic.quarantine.ndjson";

    /**
     * The state of the ledger, as of its LAST read.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 2). The contract is
     * explicit: <b>read first</b>. {@link #NOT_READ_YET} is not a healthy ledger —
     * it is the absence of an answer — so a caller cannot mistake "nobody has looked"
     * for "the ledger is fine".</p>
     */
    public enum State {
        /** The ledger has not been read yet; nothing is known about it. */
        NOT_READ_YET,
        /** The last read parsed every line: the ledger is whole. */
        READABLE,
        /** The last read skipped at least one line: the ledger is not whole. */
        MALFORMED
    }

    /**
     * The suffix of the writer-lock file that serialises ledger appends.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F4): the ledger is written with a
     * read-modify-write (into a temporary file that is moved over it), so two
     * concurrent marks would otherwise lose one of them. This lock file serialises
     * the writers. It is an empty file and it is not state: the ledger is.</p>
     */
    private static final String LOCK_SUFFIX = ".lock";

    /** What an event does to a record. */
    public enum Op {
        /** The record is excluded from the learning window. */
        QUARANTINE,
        /** The record is admitted to the learning window again. */
        RELEASE
    }

    /** One ledger event. */
    public record Event(Op op, String id, String reason, long tsMillis) {

        public String toJson() {
            StringBuilder sb = new StringBuilder(96);
            sb.append("{\"op\":\"").append(op.name())
              .append("\",\"id\":\"").append(escape(id))
              .append("\",\"reason\":\"").append(escape(reason))
              .append("\",\"ts\":").append(tsMillis)
              .append("}");
            return sb.toString();
        }

        static Event fromJson(String line) {
            if (line == null) return null;
            String t = line.trim();
            if (!t.startsWith("{") || !t.endsWith("}")) return null;
            String op = extractString(t, "op");
            String id = extractString(t, "id");
            String reason = extractString(t, "reason");
            long ts = (long) extractNumber(t, "ts");
            Op parsed;
            try {
                parsed = Op.valueOf(op.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (RuntimeException ex) {
                return null;
            }
            if (id.isEmpty()) return null;
            return new Event(parsed, id, reason, ts);
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
    }

    private final Path ledgerPath;
    private volatile int lastSkippedLines = 0;
    /**
     * The state of the ledger's LAST read.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 2): the state before the
     * first read is its OWN value, not "readable". A caller that asks "is the ledger
     * healthy?" before it has read the ledger must not be told "yes" — that answer
     * belongs to a read that has not happened.</p>
     */
    private volatile State state = State.NOT_READ_YET;
    /**
     * How many times the ledger has been read since construction.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F7): the admission gate reads the
     * ledger ONCE per pass. This counter makes that observable, so a regression that
     * goes back to asking the ledger about every record separately is visible
     * instead of merely slow.</p>
     */
    private final java.util.concurrent.atomic.AtomicLong readCount =
        new java.util.concurrent.atomic.AtomicLong();

    public EpisodeQuarantine(Path ledgerPath) {
        this.ledgerPath = ledgerPath;
    }

    /**
     * The ledger that governs a given episodic log: the sibling file with the
     * documented name.
     *
     * <p>Deriving it from the log's own path is what makes the mechanism work with
     * <em>no configuration</em>: a caller that constructs an {@link EpisodicLog} gets a
     * quarantine bound to the right file without naming it.</p>
     */
    public static Path defaultLedgerPath(Path episodicLogPath) {
        Path p = episodicLogPath == null ? Path.of(LEDGER_FILE_NAME) : episodicLogPath;
        Path parent = p.toAbsolutePath().getParent();
        return (parent == null ? Path.of(LEDGER_FILE_NAME) : parent.resolve(LEDGER_FILE_NAME));
    }

    public Path path() {
        return ledgerPath;
    }

    /** Malformed ledger lines skipped by the most recent read. Unit: lines. */
    public int lastSkippedLines() {
        return lastSkippedLines;
    }

    /**
     * How many times the ledger has been read since construction. Unit: reads.
     *
     * @return the read count
     */
    public long ledgerReads() {
        return readCount.get();
    }

    /**
     * The state of the ledger, as of its LAST read.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 2). The contract is
     * explicit: <b>read first</b>. Before the first read the state is
     * {@link State#NOT_READ_YET}, which is NOT a healthy ledger — it is the absence
     * of an answer — so a caller cannot mistake "nobody has looked" for "the ledger
     * is fine".</p>
     *
     * @return the state of the last read
     */
    public State state() {
        return state;
    }

    /**
     * True when the last read of the ledger parsed every line.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F4). A ledger line this build cannot
     * read is a quarantine mark that cannot be honoured: the record it names would
     * re-enter the learning feed while the ledger still claims to exclude it. The
     * admission gate therefore FAILS CLOSED on a malformed ledger — it admits
     * nothing and reports the count, instead of quietly learning from a record the
     * operator excluded.</p>
     *
     * <p><b>The contract (review fix, LOW finding 2): READ FIRST.</b> This method
     * answers "did the last read find a malformed line?", and before the first read
     * there is no such line — so it answers true. That is a statement about the
     * ledger's PARSING, not about its health. A caller that wants to know whether
     * the ledger has actually been looked at asks {@link #state()}: it answers
     * {@link State#NOT_READ_YET} until the ledger has been read, and that is the
     * value a caller must not mistake for a healthy ledger.</p>
     *
     * @return true when the last read found no malformed line
     */
    public boolean readable() {
        return lastSkippedLines == 0;
    }

    /**
     * True when the ledger holds lines this build cannot read.
     *
     * <p>The counterpart of {@link #readable()}: the learning window is refused
     * while the ledger is in this state, and the count of the unreadable lines is
     * reported on the runtime path ({@code RealSleepScheduler.DreamReport}).</p>
     *
     * @return true when the last read skipped at least one line
     */
    public boolean malformed() {
        return lastSkippedLines > 0;
    }

    /** Every event in the ledger, in the order it was written. */
    public List<Event> events() {
        readCount.incrementAndGet();
        List<Event> out = new ArrayList<>();
        if (ledgerPath == null || !Files.exists(ledgerPath)) {
            lastSkippedLines = 0;
            state = State.READABLE;
            return out;
        }
        int skipped = 0;
        try {
            for (String line : Files.readAllLines(ledgerPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                Event e = Event.fromJson(line);
                if (e == null) skipped++;
                else out.add(e);
            }
        } catch (IOException ex) {
            throw new RuntimeException("EpisodeQuarantine read failed: " + ledgerPath, ex);
        }
        lastSkippedLines = skipped;
        state = skipped == 0 ? State.READABLE : State.MALFORMED;
        return out;
    }

    /** The ids that are currently quarantined. The LAST event per id decides. */
    public Set<String> quarantinedIds() {
        Map<String, Event> last = new LinkedHashMap<>();
        for (Event e : events()) last.put(e.id(), e);
        Set<String> out = new LinkedHashSet<>();
        for (Event e : last.values()) {
            if (e.op() == Op.QUARANTINE) out.add(e.id());
        }
        return out;
    }

    /** True when the record is currently quarantined. */
    public boolean isQuarantined(String id) {
        if (id == null) return false;
        return quarantinedIds().contains(id);
    }

    /**
     * Mark a record as excluded from the learning window. Appends an event; never
     * rewrites the log.
     *
     * @param id      the episodic record id
     * @param reason  why, in human-readable form; part of the audit trail
     * @param tsMillis the event timestamp, supplied by the caller (no clock here)
     */
    public void quarantine(String id, String reason, long tsMillis) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a quarantine must name a record id");
        }
        append(new Event(Op.QUARANTINE, id, reason == null ? "" : reason, tsMillis));
    }

    /** Reverse a quarantine. The quarantine itself stays in the ledger's history. */
    public void release(String id, String reason, long tsMillis) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a release must name a record id");
        }
        append(new Event(Op.RELEASE, id, reason == null ? "" : reason, tsMillis));
    }

    /**
     * Appends one event to the ledger, ATOMICALLY.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F4). The first implementation appended
     * with a plain {@code Files.writeString(..., APPEND)}: a crash in the middle of
     * that write left a TORN line in the ledger, the torn line was skipped on the next
     * read, and the quarantine mark it carried was silently lost — the very record
     * the operator excluded re-entered the learning feed. The write is now a
     * read-modify-write of the whole ledger into a temporary file that is moved over
     * the ledger atomically, so the ledger is never observed half-written. The
     * previous bytes are preserved exactly (the ledger stays append-only in content),
     * and a writer lock serialises concurrent marks so no event is lost.</p>
     */
    private void append(Event event) {
        if (ledgerPath == null) {
            throw new IllegalStateException("EpisodeQuarantine has no ledger path");
        }
        Path absolute = ledgerPath.toAbsolutePath();
        Path parent = absolute.getParent();
        try {
            if (parent != null) Files.createDirectories(parent);
            Path lockPath = absolute.resolveSibling(absolute.getFileName() + LOCK_SUFFIX);
            try (java.nio.channels.FileChannel lockChannel =
                     java.nio.channels.FileChannel.open(lockPath,
                         StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 java.nio.channels.FileLock held = lockChannel.lock()) {
                String existing = Files.exists(absolute)
                    ? Files.readString(absolute, StandardCharsets.UTF_8) : "";
                Path temp = Files.createTempFile(parent, LEDGER_FILE_NAME, ".tmp");
                try {
                    Files.writeString(temp, existing + event.toJson() + "\n",
                        StandardCharsets.UTF_8);
                    try {
                        Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                        Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("EpisodeQuarantine append failed: " + ledgerPath, ex);
        }
    }

    @Override
    public String toString() {
        return "EpisodeQuarantine[" + ledgerPath + "]";
    }
}
