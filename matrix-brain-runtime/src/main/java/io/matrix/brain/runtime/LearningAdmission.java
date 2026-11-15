package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the admission gate in front of the learning window.
 *
 * <p><b>What it decides.</b> Given the records of the durable episodic log, which of
 * them may the mind learn from? Two independent reasons exclude a record:</p>
 * <ol>
 *   <li><b>provenance</b> — the record's {@link EpisodeRole} is not learning-eligible
 *       (evaluation, experiment, synthetic). This is a property of the record itself,
 *       so it travels with the record wherever it goes: a record that was written by an
 *       evaluation harness is excluded even if it is physically mixed into the
 *       production log;</li>
 *   <li><b>quarantine</b> — the record's id is named in the {@link EpisodeQuarantine}
 *       ledger. This is how a record that was written <em>before</em> the role tag
 *       existed is excluded, reversibly and without touching its bytes.</li>
 * </ol>
 *
 * <p><b>Where it is applied.</b> {@link RealSleepScheduler} builds the induction
 * input through this gate, so a non-organic record cannot reach
 * {@link RuleInductionEngine#induce}. The gate is a pure function: no clock, no
 * randomness, no I/O beyond reading the ledger, and the same log and ledger always
 * produce the same window.</p>
 *
 * <p><b>What the gate does NOT do.</b> It does not delete anything and it does not
 * touch the log. A quarantined record is still in the durable log, still readable with
 * {@link EpisodicLog#readAll()}, and still counted by every audit report. The gate
 * decides only what the mind may <em>learn from</em>.</p>
 *
 * <p><b>ONE definition of the window (review fix F8).</b> {@link #window} is the only
 * place that says which records the production induction sees, and {@link #assess} is
 * the only place that reads the log and applies the gate. The scheduler, the log's own
 * API and the operator tool all go through them, so they cannot drift apart.</p>
 *
 * <p><b>Fail closed (review fix F4).</b> A ledger line this build cannot read is a
 * quarantine mark that cannot be honoured. When the ledger is malformed the gate
 * admits NOTHING and says so: a contaminated record must not re-enter the learning
 * feed because the operator's ledger was torn.</p>
 */
public final class LearningAdmission {

    private LearningAdmission() {}

    /**
     * The result of one admission pass.
     *
     * @param candidates   the records that may be learned from, in log order
     * @param total        how many records the log holds
     * @param excluded     how many records the gate excluded
     * @param ledgerReadable whether the quarantine ledger was read completely
     * @param ledgerMalformedLines how many ledger lines could not be read
     */
    public record Assessment(List<EpisodicLog.Entry> candidates,
                              int total,
                              int excluded,
                              boolean ledgerReadable,
                              int ledgerMalformedLines) {

        /**
         * The learning window of this assessment: the last {@code kMax} candidates.
         *
         * @param kMax the size cap of the window
         * @return the window, never null
         */
        public List<EpisodicLog.Entry> window(int kMax) {
            return LearningAdmission.window(candidates, kMax);
        }
    }

    /**
     * True when a record may be learned from.
     *
     * @param entry      the record; null is never admitted
     * @param quarantine the quarantine ledger; null means "nothing is quarantined"
     */
    public static boolean admits(EpisodicLog.Entry entry, EpisodeQuarantine quarantine) {
        if (entry == null) return false;
        if (!eligible(entry)) return false;
        if (quarantine == null) return true;
        Set<String> quarantined = quarantine.quarantinedIds();
        if (!quarantine.readable()) return false;
        return !quarantined.contains(entry.id());
    }

    /**
     * True when the record's own provenance permits learning.
     *
     * <p>A null role is not eligible: an unreadable provenance is not a claim of
     * eligibility.</p>
     */
    private static boolean eligible(EpisodicLog.Entry entry) {
        EpisodeRole role = entry.role();
        return role != null && role.learningEligible();
    }

    /** The admitted subset of a list of records, in the original order. */
    public static List<EpisodicLog.Entry> admitted(List<EpisodicLog.Entry> all,
                                                    EpisodeQuarantine quarantine) {
        List<EpisodicLog.Entry> out = new ArrayList<>();
        if (all == null) return out;
        if (quarantine == null) {
            for (EpisodicLog.Entry e : all) {
                if (eligible(e)) out.add(e);
            }
            return out;
        }
        // ONE read of the ledger for the whole pass (review fix F7): the previous
        // implementation asked the ledger about every record separately, and each
        // question re-read and re-parsed the whole file.
        Set<String> quarantined = quarantine.quarantinedIds();
        if (!quarantine.readable()) {
            // Fail closed: an unreadable ledger is an unreadable exclusion, and the
            // count is reported on the runtime path.
            return out;
        }
        for (EpisodicLog.Entry e : all) {
            if (eligible(e) && !quarantined.contains(e.id())) out.add(e);
        }
        return out;
    }

    /**
     * The learning window: the last {@code kMax} admitted records.
     *
     * <p>The recency selector is the production one — the most recent records — and
     * the size cap is {@link KMaxEnforcer#K_MAX}. The only thing this changes relative
     * to the previous behaviour is <em>which</em> records are eligible to be in the
     * window: a non-organic or quarantined record is skipped, and the window reaches
     * further back for an organic record instead of stopping at the contamination.</p>
     */
    public static List<EpisodicLog.Entry> window(List<EpisodicLog.Entry> admitted, int kMax) {
        if (admitted == null || admitted.isEmpty()) return List.of();
        int n = Math.min(admitted.size(), Math.max(0, kMax));
        return admitted.subList(admitted.size() - n, admitted.size());
    }

    /**
     * Reads the log, applies the gate and reports what happened.
     *
     * <p>This is the ONE read-and-admit of the system: the scheduler, the log's own
     * API and the operator tool all use it, so the numbers they report are the same
     * numbers (review fix F8).</p>
     *
     * @param log        the log to read; null means "nothing to read"
     * @param quarantine the quarantine ledger; null means "nothing is quarantined"
     * @return the assessment, never null
     */
    public static Assessment assess(EpisodicLog log, EpisodeQuarantine quarantine) {
        List<EpisodicLog.Entry> all = log == null ? List.of() : log.readAll();
        List<EpisodicLog.Entry> candidates = admitted(all, quarantine);
        boolean readable = quarantine == null || quarantine.readable();
        int malformed = quarantine == null ? 0 : quarantine.lastSkippedLines();
        return new Assessment(candidates, all.size(), all.size() - candidates.size(),
            readable, malformed);
    }

    /**
     * The production window of a log: read everything (the audit view), admit what may
     * be learned from, then take the last {@link KMaxEnforcer#K_MAX}.
     */
    public static List<EpisodicLog.Entry> learningWindow(EpisodicLog log,
                                                         EpisodeQuarantine quarantine,
                                                         int kMax) {
        if (log == null) return List.of();
        return assess(log, quarantine).window(kMax);
    }
}
