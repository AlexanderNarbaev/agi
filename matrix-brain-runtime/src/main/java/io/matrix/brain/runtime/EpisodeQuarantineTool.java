package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the operator tool for the reversible quarantine.
 *
 * <p><b>What it does.</b> It reads the durable episodic log and the quarantine
 * ledger, and it can ADD quarantine marks. It never deletes a record and never
 * rewrites the log: the only file it writes is the append-only ledger, and every
 * mark it adds is reversible with {@code release}.</p>
 *
 * <p><b>Why a tool and not a convention.</b> "Which records are contaminated?" has
 * to be answerable by a reader, on a machine, without reading the mind's source
 * code. This tool answers it and prints the numbers.</p>
 *
 * <pre>
 *   scan                     report the roles, the quarantine state and both windows
 *   quarantine-probes        quarantine every record that matches the frozen corpus
 *   quarantine-inputs FILE   quarantine every record whose INPUT is listed in FILE
 *   quarantine-ids FILE      quarantine every record whose ID is listed in FILE
 *   release &lt;id&gt;              reverse one quarantine (appends a RELEASE event)
 *   release-all              reverse every quarantine
 * </pre>
 *
 * <p><b>Why there is no command by POSITION (review fix F5).</b> The first version
 * of this tool could quarantine "the records at log positions A..B". A position is
 * not an identity: the moment one more interaction is appended, the same command
 * marks a different set of records — a FRESH ORGANIC interaction included — and frees
 * a record the operator meant to exclude. Every command here therefore names RECORDS
 * (by id or by their content), never positions.</p>
 *
 * <p>Usage:
 * {@code EpisodeQuarantineTool <command> <episodic-log> [--ledger <path>]
 * [--reason <text>] [--dry-run]}</p>
 *
 * <p>Article III: the tool's decisions are a pure function of the log and the ledger.
 * The only timestamp it writes is the one the caller supplies through
 * {@code --now} or, absent that, the wall clock — and it is written into the ledger
 * as an audit stamp, never used to decide anything.</p>
 */
public final class EpisodeQuarantineTool {

    private EpisodeQuarantineTool() {}

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    /**
     * Runs one command and returns its exit code.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F5): the command dispatch is a
     * separate method that RETURNS instead of calling {@code System.exit}, so the
     * command surface — including the absence of a positional command — can be
     * asserted by a test instead of being described in a comment.</p>
     *
     * @param args the command and its arguments
     * @return 0 when the command succeeded, 2 when it was refused
     */
    static int run(String[] args) {
        if (args.length < 2) {
            usage();
            return 2;
        }
        String command = args[0];
        Path logPath = Path.of(args[1]);
        Path ledgerPath = null;
        String reason = "operator quarantine (P0.4)";
        boolean dryRun = false;
        long now = System.currentTimeMillis();
        List<String> positional = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--ledger" -> ledgerPath = Path.of(args[++i]);
                case "--reason" -> reason = args[++i];
                case "--now" -> now = Long.parseLong(args[++i]);
                case "--dry-run" -> dryRun = true;
                default -> positional.add(args[i]);
            }
        }
        if (ledgerPath == null) {
            ledgerPath = EpisodeQuarantine.defaultLedgerPath(logPath);
        }
        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledgerPath);
        try {
        switch (command) {
            case "scan" -> scan(logPath, quarantine);
            case "quarantine-probes" -> quarantineProbes(logPath, quarantine, reason, now, dryRun);
            case "quarantine-inputs" -> {
                if (positional.isEmpty()) {
                    System.err.println("quarantine-inputs needs a file of inputs");
                    return 2;
                }
                quarantineInputs(logPath, quarantine, Path.of(positional.get(0)),
                    reason, now, dryRun);
            }
            case "quarantine-ids" -> {
                if (positional.isEmpty()) {
                    System.err.println("quarantine-ids needs a file of record ids");
                    return 2;
                }
                quarantineIds(logPath, quarantine, Path.of(positional.get(0)),
                    reason, now, dryRun);
            }
            case "release" -> {
                if (positional.isEmpty()) {
                    System.err.println("release needs a record id");
                    return 2;
                }
                quarantine.release(positional.get(0), reason, now);
                System.out.println("released " + positional.get(0));
            }
            case "release-all" -> {
                int n = 0;
                for (String id : quarantine.quarantinedIds()) {
                    quarantine.release(id, reason, now);
                    n++;
                }
                System.out.println("released " + n + " record(s)");
            }
            default -> {
                usage();
                return 2;
            }
        }
        } catch (IllegalStateException ex) {
            System.err.println(ex.getMessage());
            return 2;
        }
        return 0;
    }

    private static void usage() {
        System.err.println("usage: EpisodeQuarantineTool <scan|quarantine-probes|"
            + "quarantine-inputs|quarantine-ids|release|release-all> <episodic-log> "
            + "[--ledger <path>] [--reason <text>] [--now <millis>] [--dry-run]");
    }

    // ---- commands ---------------------------------------------------------

    private static void scan(Path logPath, EpisodeQuarantine quarantine) {
        List<EpisodicLog.Entry> all = new EpisodicLog(logPath).readAll();
        Set<String> quarantined = quarantine.quarantinedIds();
        Map<EpisodeRole, Integer> byRole = new LinkedHashMap<>();
        int probeMatches = 0;
        int organic = 0;
        int legacy = 0;
        int nonOrganic = 0;
        for (EpisodicLog.Entry e : all) {
            byRole.merge(e.role(), 1, Integer::sum);
            if (e.role().organic()) organic++;
            if (e.role() == EpisodeRole.LEGACY_UNTAGGED) legacy++;
            if (e.role().nonOrganic()) nonOrganic++;
            if (PromotionGate.isFrozenProbe(e.input()) || PromotionGate.isFrozenProbe(e.reply())) {
                probeMatches++;
            }
        }
        // EPISTEMIC-INTEGRITY P0.4 (review fix F8): ONE read-and-admit — the same
        // assessment the production scheduler makes.
        LearningAdmission.Assessment assessment =
            LearningAdmission.assess(new EpisodicLog(logPath), quarantine);
        List<EpisodicLog.Entry> rawWindow = LearningAdmission.window(all, KMaxEnforcer.K_MAX);
        List<EpisodicLog.Entry> admittedWindow =
            assessment.window(KMaxEnforcer.K_MAX);

        System.out.println("log:     " + logPath.toAbsolutePath());
        System.out.println("ledger:  " + quarantine.path().toAbsolutePath());
        System.out.println("records: " + all.size());
        for (Map.Entry<EpisodeRole, Integer> r : byRole.entrySet()) {
            System.out.println("  role " + r.getKey().wire() + " = " + r.getValue());
        }
        System.out.println("organic=" + organic + " legacyUntagged=" + legacy
            + " nonOrganic=" + nonOrganic);
        System.out.println("quarantined=" + quarantined.size()
            + " frozenProbeMatches=" + probeMatches);
        System.out.println("admitted=" + assessment.candidates().size()
            + " excluded=" + assessment.excluded());
        System.out.println("ledgerReadable=" + assessment.ledgerReadable()
            + " ledgerMalformedLines=" + assessment.ledgerMalformedLines());
        System.out.println("window(raw)      : " + describe(rawWindow));
        System.out.println("window(admitted) : " + describe(admittedWindow));
    }

    private static String describe(List<EpisodicLog.Entry> window) {
        int positives = 0;
        for (EpisodicLog.Entry e : window) {
            if (e.confidence() >= 0.7 && e.accepted()) positives++;
        }
        return "size=" + window.size() + " pos=" + positives + " neg=" + (window.size() - positives);
    }

    private static void quarantineProbes(Path logPath, EpisodeQuarantine quarantine,
                                         String reason, long now, boolean dryRun) {
        List<EpisodicLog.Entry> all = new EpisodicLog(logPath).readAll();
        int n = 0;
        for (EpisodicLog.Entry e : all) {
            if (!PromotionGate.isFrozenProbe(e.input()) && !PromotionGate.isFrozenProbe(e.reply())) {
                continue;
            }
            if (quarantine.isQuarantined(e.id())) continue;
            n++;
            if (!dryRun) quarantine.quarantine(e.id(), reason, now);
        }
        System.out.println((dryRun ? "[dry-run] would quarantine " : "quarantined ")
            + n + " frozen-probe record(s)");
    }

    /**
     * Quarantine every record whose INPUT is listed in the given file.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F5): the rule is by CONTENT, so it
     * means the same thing before and after the log grows — appending an organic
     * interaction cannot change which records this rule names.</p>
     */
    private static void quarantineInputs(Path logPath, EpisodeQuarantine quarantine,
                                         Path inputsFile, String reason, long now,
                                         boolean dryRun) {
        Set<String> inputs = readLines(inputsFile);
        List<EpisodicLog.Entry> all = new EpisodicLog(logPath).readAll();
        int n = 0;
        for (EpisodicLog.Entry e : all) {
            if (!inputs.contains(e.input())) continue;
            if (quarantine.isQuarantined(e.id())) continue;
            n++;
            if (!dryRun) quarantine.quarantine(e.id(), reason + " (designed input)", now);
        }
        System.out.println((dryRun ? "[dry-run] would quarantine " : "quarantined ")
            + n + " record(s) by designed input");
    }

    /**
     * Quarantine every record whose ID is listed in the given file.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F5): the rule is by IDENTITY, which is
     * the only rule that survives the log growing: a record keeps its id, and a
     * record that was never listed is never marked by accident.</p>
     */
    private static void quarantineIds(Path logPath, EpisodeQuarantine quarantine,
                                      Path idsFile, String reason, long now, boolean dryRun) {
        Set<String> ids = readLines(idsFile);
        List<EpisodicLog.Entry> all = new EpisodicLog(logPath).readAll();
        Set<String> known = new LinkedHashSet<>();
        for (EpisodicLog.Entry e : all) known.add(e.id());
        int n = 0;
        int unknown = 0;
        for (String id : ids) {
            if (!known.contains(id)) {
                unknown++;
                continue;
            }
            if (quarantine.isQuarantined(id)) continue;
            n++;
            if (!dryRun) quarantine.quarantine(id, reason + " (named id)", now);
        }
        System.out.println((dryRun ? "[dry-run] would quarantine " : "quarantined ")
            + n + " record(s) by id" + (unknown > 0 ? " (" + unknown + " id(s) not in the log)" : ""));
    }

    /** Reads a file of one entry per line, ignoring blanks. */
    private static Set<String> readLines(Path file) {
        Set<String> out = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (!t.isEmpty()) out.add(t);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read " + file + ": " + ex.getMessage(), ex);
        }
        return out;
    }

    /** Read the log and print its sha256 — the audit anchor for a report. */
    static String sha256(Path path) {
        try {
            if (!Files.isRegularFile(path)) return "absent";
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return "error: " + ex.getMessage();
        }
    }

    /** The bytes of the log, for a report that quotes it. */
    static byte[] bytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (Exception ex) {
            return new byte[0];
        }
    }

    static String utf8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
