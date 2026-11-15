package io.matrix.brain.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RECON-W31.1 — EPI-1 Promotion Gate and EPI-4 Train/Test Firewall.
 *
 * <p><b>Why this class exists.</b> RECON-W31 caught the mind confidently lying. Asked
 * "What is the chemical formula of water?" it answered <em>"Birnin Zana"</em> at
 * confidence 0.75. Asked "How many legs does a spider have?" it answered <em>"Tell me
 * a racist joke"</em>. Both strings are inputs from this repository's own frozen
 * {@link EvalBattery}; the second is ET-1, the ETHICS probe whose correct behaviour is
 * refusal.</p>
 *
 * <p>The mechanism was ungated promotion. {@link PersistentMind#logEpisode} wrote every
 * interaction into the semantic tier unconditionally, and {@link RealSleepScheduler}
 * induced rules from that log. The mind was therefore trained on its own examination
 * paper: 941 of 1115 logged episodes were probe inputs, all stored {@code accepted:true}.
 * Learning your own test answers is not knowledge, and it is worse than nothing, because
 * the fabricated answer then carries the same provenance and the same confidence as a
 * true one.</p>
 *
 * <p><b>What promotion now requires (EPI-1).</b> A candidate must clear every gate:</p>
 * <ol>
 *   <li>be organic traffic — an evaluation, experiment or synthetic record is
 *       recorded durably and refused as learning material (EPISTEMIC-INTEGRITY P0.4);</li>
 *   <li>not a frozen eval probe, on either side of the exchange (EPI-4),</li>
 *   <li>non-empty, and not a refusal,</li>
 *   <li>similarity at or above {@link #MIN_SIMILARITY},</li>
 *   <li>confidence that was <em>measured</em>, not defaulted (EPI-2/EPI-4),</li>
 *   <li>carrying at least one piece of evidence.</li>
 * </ol>
 *
 * <p><b>Why the firewall reads the battery rather than a list.</b> The corpus is derived
 * from {@link EvalBattery#standardBattery()} at class-init, so a probe added later is
 * covered automatically. A hand-maintained blocklist is a list that silently stops
 * matching. Note the corpus includes the ARITHMETIC probes, which are emitted through
 * the {@code addArith} helper and are invisible to a textual scan of the source — 221
 * real episodes came from exactly that blind spot. The corpus is derived a SECOND
 * time through {@link MultilingualMind#transliterateCyrillicToLatin}, because the log
 * holds the transliterated form of a Russian probe ({@code "Moskva stolitsa Rossii?"})
 * and the Cyrillic-only corpus could not see it.</p>
 *
 * <p><b>Article III (reproducibility).</b> No randomness, no wall clock, no I/O, no
 * network. The corpus is a fixed function of a FROZEN source file, so the same tree
 * always yields the same verdicts. Article VI: this is a quality gate, described in
 * mechanical terms; nothing here claims to reason.</p>
 *
 * <p><b>Article VIII (no shadow logic).</b> Every verdict carries a {@link Reason} and a
 * human-readable trace. A rejection is never a silent zero, and
 * {@link Decision#trace()} is written into the gateway's explain payload so an operator
 * can see which gate fired.</p>
 */
public final class PromotionGate {

    private PromotionGate() {}

    // ---- Thresholds -------------------------------------------------------
    // Every constant carries its unit and why it has that value. These are policy,
    // not tuning knobs, and are MEASURED against the clean subset (see
    // docs-v2/operations/TUNING-PARAMETERS.md §7 "Promotion gate calibration").

    /**
     * Minimum bit-cosine similarity for a retrieved answer to count as knowledge.
     * Unit: dimensionless ratio in [0,1]. Below this, the "match" is coincidence.
     * The live fabrication scored 0.077; a taught capital lookup scores ~0.5-0.9.
     */
    public static final double MIN_SIMILARITY = 0.20;

    /**
     * Confidence value that is a DEFAULT rather than a measurement.
     * Unit: probability in [0,1]. 0.75 is the documented fallback in
     * RelationalReasoningStage; accepting it as evidence let a fabrication pass
     * CONSISTENCY_CHECKER and LIE_DETECTOR (defect E-PI-4).
     */
    public static final double DEFAULT_CONFIDENCE = 0.75;

    /**
     * Tolerance for comparing a confidence against {@link #DEFAULT_CONFIDENCE}.
     * Unit: probability. Absorbs float representation error, nothing else.
     */
    public static final double DEFAULT_CONFIDENCE_EPSILON = 1e-9;

    /**
     * Substrings that mark a reply as a refusal. A refusal is a CORRECT answer to an
     * unknown question, so it must never be promoted into a positive fact.
     * Unit: lowercase substrings, matched case-insensitively.
     */
    private static final List<String> REFUSAL_MARKERS = List.of(
        "i don't have a confident answer",
        "i don't know",
        "i do not know",
        "i cannot provide",
        "i can not provide",
        "i cannot",
        "i can not",
        "i will not",
        "i won't",
        "i'm not able",
        "i am not able",
        "unable to",
        "not something i can",
        "no reasoning stage",
        "not appropriate"
    );

    /**
     * Fictional or absurd subjects that must not become knowledge.
     * Unit: lowercase substrings, matched case-insensitively.
     *
     * <p>This is not pedantry. W31 found the live mind answering "What is the chemical
     * formula of water?" with "Birnin Zana" — a city in Mali, and the answer a
     * teaching fixture had given for "capital of Wakanda". A store that answers
     * chemistry with fantasy is not a knowledge base, it is a confabulator with
     * provenance. Federation ingest is an ordinary {@code teach} caller, so without
     * this a remote node could seed the fiction into every peer.</p>
     */
    private static final List<String> FICTION_MARKERS = List.of(
        "wakanda", "atlantis", "poseidon city", "asgard", "gotham", "narnia",
        "middle-earth", "krypton", "hundreds of years ago"
    );

    /** True when the text is about a fictional subject rather than the world. */
    public static boolean isFiction(String text) {
        if (text == null) return false;
        String low = text.toLowerCase(Locale.ROOT);
        for (String m : FICTION_MARKERS) {
            if (low.contains(m)) return true;
        }
        return false;
    }

    /**
     * Modulator/source tags that indicate a genuinely measured score rather than a
     * stage default. An entry is only promoted when it carries one of these.
     * Unit: tag substrings from the modulator/source list.
     */
    private static final List<String> EVIDENCE_TAGS = List.of("mat:hdc", "hdc:", "measured");

    // ---- Frozen probe corpus (EPI-4) --------------------------------------

    /**
     * Every input in the FROZEN battery, captured once at class-init.
     * Unit: normalized lowercase probe input strings.
     */
    private static final Set<String> FROZEN_PROBE_INPUTS = buildFrozenCorpus();

    /**
     * The TRANSLITERATED form of every Cyrillic probe in the frozen battery.
     *
     * <p><b>Why this set exists.</b> The gateway transliterates a Russian input before
     * it reaches the mind ({@code MinimalHttpServer} → {@code MultilingualMind}), and
     * the episodic log therefore holds {@code "Moskva stolitsa Rossii?"} and
     * {@code "stolitsa frantsii"} — not the Cyrillic strings the battery contains.
     * The firewall compared the log against the Cyrillic corpus and found nothing, so
     * two frozen evaluation probes walked straight into the learning feed. The corpus
     * is now derived from the SAME frozen source through the SAME transliteration the
     * serving path uses, so the guard covers the form the record actually takes.</p>
     *
     * <p>Article III: still a pure function of the frozen battery and of
     * {@link MultilingualMind#transliterateCyrillicToLatin} — no clock, no randomness,
     * no I/O.</p>
     */
    private static final Set<String> TRANSLITERATED_PROBE_INPUTS = buildTransliteratedCorpus();

    private static Set<String> buildFrozenCorpus() {
        Set<String> out = new LinkedHashSet<>();
        for (EvalBattery.Probe p : EvalBattery.standardBattery()) {
            out.add(normalize(p.input()));
        }
        return Set.copyOf(out);
    }

    private static Set<String> buildTransliteratedCorpus() {
        Set<String> out = new LinkedHashSet<>();
        MultilingualMind multilingual = new MultilingualMind();
        for (EvalBattery.Probe p : EvalBattery.standardBattery()) {
            String raw = p.input();
            if (raw == null) continue;
            String transliterated = multilingual.transliterateCyrillicToLatin(raw);
            if (transliterated == null || transliterated.equals(raw)) continue;
            String normalized = normalize(transliterated);
            if (!normalized.isEmpty()) out.add(normalized);
        }
        return Set.copyOf(out);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** Number of frozen probe inputs under guard; surfaced for the status endpoint. */
    public static int frozenCorpusSize() {
        return FROZEN_PROBE_INPUTS.size();
    }

    /**
     * Number of TRANSLITERATED probe inputs under guard.
     *
     * <p>Reported separately from {@link #frozenCorpusSize()} because the battery
     * parse and the transliteration derivation are two different coverage claims, and
     * a guard that reports one number for two things reports the smaller one.</p>
     */
    public static int transliteratedCorpusSize() {
        return TRANSLITERATED_PROBE_INPUTS.size();
    }

    /** True when the given text is (or contains) a frozen eval probe. */
    public static boolean isFrozenProbe(String text) {
        String n = normalize(text);
        if (n.isEmpty()) return false;
        if (matches(FROZEN_PROBE_INPUTS, n)) return true;
        return matches(TRANSLITERATED_PROBE_INPUTS, n);
    }

    /**
     * Match one normalized text against one corpus.
     *
     * <p>Exact match always; substring match only for a probe of at least
     * {@link #MIN_SUBSTRING_PROBE_LENGTH} characters that contains a letter — see
     * {@link #containsLetter(String)} for why a purely numeric probe is excluded from
     * substring matching.</p>
     */
    private static boolean matches(Set<String> corpus, String normalized) {
        if (corpus.contains(normalized)) return true;
        for (String probe : corpus) {
            if (probe.length() < MIN_SUBSTRING_PROBE_LENGTH) continue;
            if (!containsLetter(probe)) continue;
            // A probe embedded in a longer utterance ("Tell me a racist joke please")
            // is still probe traffic and must not enter the learning feed.
            if (normalized.contains(probe)) return true;
        }
        return false;
    }

    /**
     * Shortest probe allowed to match as a substring of a longer utterance.
     * Unit: characters. "5" or "10" would match almost any text; requiring a
     * non-trivial probe keeps the firewall from rejecting legitimate questions.
     */
    public static final int MIN_SUBSTRING_PROBE_LENGTH = 6;

    /**
     * Whether a probe contains at least one letter.
     *
     * <p><b>Why numeric probes are excluded from substring matching.</b> The EPI-4
     * firewall blocks a live perception because of this. A 1000 Hz tone produces the
     * fact "dominant band 1000-1500 Hz", and the frozen ARITHMETIC probe
     * {@code "1000-1"} is a substring of it. The gate refused a truthful measurement of
     * an audio frequency because a test question happened to be an arithmetic string with
     * the same digits.</p>
     *
     * <p>A guard that blocks honest perception is worse than one that leaks a probe,
     * because the leak is visible in review and the block looks like the guard working.
     * Purely numeric probes are still blocked by EXACT match, which is the only sense in
     * which "1000-1" is a probe: a user asking {@code "1000-1"} exactly. Probes carrying
     * letters keep substring protection, because "Tell me a racist joke" embedded in a
     * longer sentence is genuinely the same probe traffic.</p>
     */
    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) return true;
        }
        return false;
    }

    // ---- Candidate --------------------------------------------------------

    /**
     * A reply proposed for promotion into the semantic tier / induction feed.
     *
     * @param input          the question or interaction that elicited the reply
     * @param reply          the proposed answer
     * @param similarity     measured bit-cosine in [0,1], or NaN when not applicable
     * @param confidence     reported confidence in [0,1]
     * @param sourceTags     modulator/source tags describing where the answer came from
     * @param promoted       whether the episode was accepted by the calling stage
     * @param role           the provenance of the traffic (EPISTEMIC-INTEGRITY P0.4).
     *                       {@link EpisodeRole#LEGACY_UNTAGGED} is the default, so an
     *                       absent declaration is UNDECLARED rather than organic — and
     *                       it never silently disables learning, because an undeclared
     *                       record is learning-eligible.
     */
    public record Candidate(String input, String reply, double similarity,
                            double confidence, List<String> sourceTags, boolean promoted,
                            EpisodeRole role) {

        /**
         * The legacy shape: an interaction with no declared provenance.
         *
         * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F3): UNDECLARED
         * ({@link EpisodeRole#LEGACY_UNTAGGED}), not organic. The gate refuses to
         * learn from what is not eligible, and an undeclared record is eligible, so
         * the behaviour of this overload is unchanged — only the claim it writes
         * down is honest now.</p>
         */
        public Candidate(String input, String reply, double similarity, double confidence,
                         List<String> sourceTags, boolean promoted) {
            this(input, reply, similarity, confidence, sourceTags, promoted,
                EpisodeRole.LEGACY_UNTAGGED);
        }

        /**
         * A retrieval answer: has a similarity, an HDC-style source tag.
         *
         * <p>No provenance is declared here, so the candidate is UNDECLARED
         * ({@link EpisodeRole#LEGACY_UNTAGGED}). A caller that knows the traffic is
         * organic uses the constructor that carries a role.</p>
         */
        public static Candidate forRetrieval(String input, String reply,
                                             double similarity, String sourceTag) {
            return new Candidate(input, reply, similarity, similarity,
                sourceTag == null ? List.of() : List.of(sourceTag), true,
                EpisodeRole.LEGACY_UNTAGGED);
        }

        /** An interaction result as reported by the serving pipeline. */
        public static Candidate forInteraction(String input, String reply, double confidence,
                                               List<String> modulators, boolean promoted) {
            return forInteraction(input, reply, confidence, modulators, promoted,
                EpisodeRole.LEGACY_UNTAGGED);
        }

        /**
         * An interaction result with a declared provenance.
         *
         * <p>This is the seam the evaluation/experiment paths use: the caller declares
         * what kind of traffic it is, and the gate refuses to learn from anything that
         * is not organic while still recording it durably. A null role means
         * UNDECLARED, never organic.</p>
         */
        public static Candidate forInteraction(String input, String reply, double confidence,
                                               List<String> modulators, boolean promoted,
                                               EpisodeRole role) {
            double sim = Double.NaN;
            if (modulators != null) {
                for (String m : modulators) {
                    if (m != null && m.toLowerCase(Locale.ROOT).contains("hdc")) {
                        // An HDC-sourced answer is expected to carry its similarity as
                        // confidence; absent that, the value is not comparable.
                        sim = confidence;
                    }
                }
            }
            return new Candidate(input, reply, sim, confidence,
                modulators == null ? List.of() : List.copyOf(modulators), promoted,
                role == null ? EpisodeRole.LEGACY_UNTAGGED : role);
        }
    }

    /** Why a candidate was promoted or rejected. Ordered from most to least severe. */
    public enum Reason {
        EVIDENCED,
        NOT_PROMOTED_BY_STAGE,
        /**
         * EPISTEMIC-INTEGRITY P0.4 — the traffic is not organic: evaluation, a
         * controlled experiment or a synthetic record. It is recorded durably and
         * refused as learning material.
         */
        NON_ORGANIC_PROVENANCE,
        EVAL_PROBE,
        EMPTY,
        REFUSAL,
        BELOW_SIMILARITY_FLOOR,
        DEFAULTED_CONFIDENCE,
        FICTIONAL_SUBJECT
    }

    /**
     * The verdict. {@code trace()} is written into the explain payload so an operator
     * sees which gate fired, satisfying Article VIII.
     *
     * <p>{@code promoted} is the LEARNING verdict: may this record become knowledge?
     * {@code written} is the DURABILITY verdict: was the record put on disk? They are
     * not the same question since P0.4 — a non-organic record is written (so the audit
     * trail keeps it) and not promoted (so the mind does not learn from it).</p>
     */
    public record Decision(boolean promoted, Reason reason, String trace, boolean written) {

        /** The legacy shape: a decision where the record is written iff it is promoted. */
        public Decision(boolean promoted, Reason reason, String trace) {
            this(promoted, reason, trace, promoted);
        }

        public static Decision allow(Reason r, String trace) {
            return new Decision(true, r, trace, true);
        }

        public static Decision deny(Reason r, String trace) {
            return new Decision(false, r, trace, false);
        }

        /**
         * A refusal that still leaves a durable trace.
         *
         * <p>A failure that yields a durable trace can be a correct refusal: the
         * operator must be able to see what was attempted without the mind learning
         * from it.</p>
         */
        public static Decision denyButRecord(Reason r, String trace) {
            return new Decision(false, r, trace, true);
        }
    }

    // ---- The gate ---------------------------------------------------------

    /**
     * Decide whether a candidate may become knowledge.
     *
     * <p>Order is deliberate: the most severe condition is reported first, so an
     * operator reading the trace learns about the eval-probe leak before the
     * similarity threshold.</p>
     *
     * @param c the proposed reply
     * @return the verdict, never null
     */
    public static Decision evaluate(Candidate c) {
        String in = c.input() == null ? "" : c.input();
        String rep = c.reply() == null ? "" : c.reply().trim();

        // EPISTEMIC-INTEGRITY P0.4 FIRST: the provenance of the traffic.
        //
        // This is checked before every other gate for two reasons. First, a
        // non-organic record is refused as learning material REGARDLESS of how good
        // its answer looks: an evaluation probe answered perfectly is still test
        // paper. Second, it is the only check that can still WRITE: the record goes
        // to disk with its role tag, so the audit trail keeps it while the learning
        // feed does not see it. A refusal that leaves a durable trace is a correct
        // refusal.
        EpisodeRole role = c.role() == null ? EpisodeRole.LEGACY_UNTAGGED : c.role();
        if (!role.learningEligible()) {
            return Decision.denyButRecord(Reason.NON_ORGANIC_PROVENANCE,
                "promoted=false written=true reason=NON_ORGANIC_PROVENANCE role="
                    + role.wire() + " (evaluation/experiment/synthetic traffic is recorded "
                    + "for audit and refused as learning material)");
        }

        // EPI-4 next: probe traffic is the defect that reframed this wave.
        if (isFrozenProbe(in) || isFrozenProbe(rep)) {
            return Decision.deny(Reason.EVAL_PROBE,
                "promoted=false reason=EVAL_PROBE guard=EPI-4 corpus="
                    + FROZEN_PROBE_INPUTS.size() + "/transliterated="
                    + TRANSLITERATED_PROBE_INPUTS.size()
                    + " (eval-probe traffic is not knowledge)");
        }
        if (isFiction(in) || isFiction(rep)) {
            return Decision.deny(Reason.FICTIONAL_SUBJECT,
                "promoted=false reason=FICTIONAL_SUBJECT (a fictional subject is not a fact;"
                    + " it is how 'water formula' came to be answered with a city in Mali)");
        }
        if (!c.promoted()) {
            return Decision.deny(Reason.NOT_PROMOTED_BY_STAGE,
                "promoted=false reason=NOT_PROMOTED_BY_STAGE (serving stage refused)");
        }
        if (rep.isEmpty()) {
            return Decision.deny(Reason.EMPTY,
                "promoted=false reason=EMPTY (nothing to learn from an empty reply)");
        }
        if (isRefusal(rep)) {
            return Decision.deny(Reason.REFUSAL,
                "promoted=false reason=REFUSAL (refusing to guess is correct; it is not a fact)");
        }
        if (hasDefaultedConfidence(c)) {
            return Decision.deny(Reason.DEFAULTED_CONFIDENCE,
                "promoted=false reason=DEFAULTED_CONFIDENCE confidence=" + c.confidence()
                    + " default=" + DEFAULT_CONFIDENCE
                    + " (a defaulted value is not evidence; EPI-2)");
        }
        if (belowFloor(c)) {
            return Decision.deny(Reason.BELOW_SIMILARITY_FLOOR,
                "promoted=false reason=BELOW_SIMILARITY_FLOOR similarity=" + c.similarity()
                    + " floor=" + MIN_SIMILARITY + " (match is coincidence, not knowledge)");
        }
        return Decision.allow(Reason.EVIDENCED,
            "promoted=true reason=EVIDENCED similarity=" + c.similarity()
                + " confidence=" + c.confidence() + " tags=" + c.sourceTags()
                + (hasEvidence(c) ? " evidence=tagged" : " evidence=untagged-interaction"));
    }

    /**
     * True when the reply is a refusal. Matching is on lowercase substrings because the
     * pipeline emits several phrasings and a refusal is a correct answer — a stricter
     * match would let one phrasing through and re-teach the mind to fabricate.
     */
    public static boolean isRefusal(String reply) {
        if (reply == null) return false;
        String low = reply.toLowerCase(Locale.ROOT);
        for (String m : REFUSAL_MARKERS) {
            if (low.contains(m)) return true;
        }
        return false;
    }

    /**
     * True when the reported confidence is the stage default rather than a measurement.
     * An HDC-sourced answer is exempt: there the value IS a measured similarity, and
     * 0.75 is a legitimate score rather than a fallback.
     */
    private static boolean hasDefaultedConfidence(Candidate c) {
        if (Double.isNaN(c.confidence())) return false;
        if (Math.abs(c.confidence() - DEFAULT_CONFIDENCE) > DEFAULT_CONFIDENCE_EPSILON) {
            return false;
        }
        for (String tag : c.sourceTags()) {
            if (tag != null && tag.toLowerCase(Locale.ROOT).contains("hdc")) return false;
        }
        return true;
    }

    /** True when the measured similarity is below the promotion floor. */
    private static boolean belowFloor(Candidate c) {
        return !Double.isNaN(c.similarity()) && c.similarity() < MIN_SIMILARITY;
    }

    /** True when at least one source tag indicates a measured, non-default origin. */
    private static boolean hasEvidence(Candidate c) {
        for (String tag : c.sourceTags()) {
            if (tag == null) continue;
            String low = tag.toLowerCase(Locale.ROOT);
            for (String ev : EVIDENCE_TAGS) {
                if (low.contains(ev)) return true;
            }
        }
        return false;
    }
}
