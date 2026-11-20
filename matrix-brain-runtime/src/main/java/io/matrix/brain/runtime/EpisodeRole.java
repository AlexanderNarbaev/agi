package io.matrix.brain.runtime;

import java.util.Locale;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the provenance/role tag that travels with an episodic record.
 *
 * <p><b>Why this class exists.</b> The durable episodic log is the mind's learning
 * feed: {@link RealSleepScheduler} reads its last {@link KMaxEnforcer#K_MAX} records
 * and induces rules from them. Until now the log had no way to say <em>who</em>
 * produced a record, so a deliberately constructed evaluation probe and a real user
 * conversation were physically indistinguishable. Two frozen probes
 * ({@code столица франции} and {@code Москва столица России?}, in their transliterated
 * form) reached the log that way, and a collection harness wrote 118 designed
 * executions into the production log through the production write path. The learner
 * then trained on its own examination paper.</p>
 *
 * <p><b>What the tag does.</b> It travels with the record through the production write
 * path — {@link EpisodicLog#append} → {@link PromotionGate} → {@link TrueMindCycle} —
 * so the runtime can distinguish an <em>organic interaction</em> from
 * <em>evaluation/experiment/synthetic</em> traffic by construction rather than by
 * convention. A record that carries a non-organic role is still written durably (an
 * audit trail must not be destroyed by a refusal — a failure that yields a durable
 * trace can be a correct refusal) but it is <b>never</b> admitted to the learning
 * window.</p>
 *
 * <p><b>Article III (determinism).</b> The tag is a closed enum with no clock, no
 * randomness and no I/O. The admission decision is a pure function of the record and
 * the quarantine ledger.</p>
 *
 * <p><b>Why {@link #LEGACY_UNTAGGED} is admitted to learning.</b> A record written
 * before this tag existed carries no provenance. Admitting it is the default because
 * the alternative silently disables learning on every log written before the tag —
 * and "an absent configuration must not silently disable learning" is a hard
 * requirement. The precise tool for excluding a specific legacy record is the
 * quarantine ledger ({@link EpisodeQuarantine}), which names the record by id. The
 * 108 historical records of the real log are therefore still learnable, except the
 * ones the ledger names, and the ledger is reversible.</p>
 */
public enum EpisodeRole {

    /**
     * A real user interaction. The only role that may enter the learning window.
     */
    ORGANIC("organic"),

    /**
     * Evaluation or probe traffic: a frozen battery item, a held-out test item, or a
     * designed probe. Audited, never learned from.
     */
    EVALUATION("evaluation"),

    /**
     * A controlled, deliberately constructed execution — a collection harness run, a
     * replay, a designed workload. Real code paths, real observations, but not
     * organic traffic. Audited, never learned from.
     */
    EXPERIMENT("experiment"),

    /**
     * A fabricated or controlled synthetic record. Audited, never learned from.
     */
    SYNTHETIC("synthetic"),

    /**
     * A record whose provenance was NEVER DECLARED: written before the role tag
     * existed, or written by a caller that did not state it.
     *
     * <p>Admitted to learning by default (see the class javadoc) and never counted as
     * organic. A reader can always tell it apart from a tagged record on disk.</p>
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F3): this is the DEFAULT of every
     * constructor that carries no role. An undeclared record is labelled with what is
     * known about it — nothing — and not with a claim that it was organic. Only an
     * explicit {@link #ORGANIC} declaration may write "organic".</p>
     */
    LEGACY_UNTAGGED("legacy-untagged");

    private final String wire;

    EpisodeRole(String wire) {
        this.wire = wire;
    }

    /** The stable spelling written to the log. Never change it: it is on-disk data. */
    public String wire() {
        return wire;
    }

    /**
     * True when a record with this role may be admitted to the learning window.
     *
     * <p>Non-organic roles are excluded by construction, not by convention: the
     * admission gate ({@link LearningAdmission}) consults this predicate, so a
     * non-organic record cannot reach the induction input even if every other gate
     * would have admitted it.</p>
     */
    public boolean learningEligible() {
        return this == ORGANIC || this == LEGACY_UNTAGGED;
    }

    /** True only for a real, organic user interaction. */
    public boolean organic() {
        return this == ORGANIC;
    }

    /** True for evaluation, experiment and synthetic traffic. */
    public boolean nonOrganic() {
        return !learningEligible();
    }

    /**
     * Parse a role from its on-disk spelling.
     *
     * @param s the wire spelling; null or blank means "no tag was written"
     * @return the role; {@link #LEGACY_UNTAGGED} when the spelling is absent or
     *         unknown. An unknown spelling is treated as legacy rather than as
     *         organic: a record whose provenance cannot be read is not silently
     *         promoted into the class of trusted traffic, and the quarantine ledger
     *         can still name it.
     */
    public static EpisodeRole parse(String s) {
        if (s == null) return LEGACY_UNTAGGED;
        String t = s.trim();
        if (t.isEmpty()) return LEGACY_UNTAGGED;
        for (EpisodeRole r : values()) {
            if (r.wire.equalsIgnoreCase(t) || r.name().equalsIgnoreCase(t)) return r;
        }
        return LEGACY_UNTAGGED;
    }

    @Override
    public String toString() {
        return wire;
    }

    /** Lowercase wire spelling, for messages and logs. */
    public String lower() {
        return wire.toLowerCase(Locale.ROOT);
    }
}
