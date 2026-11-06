package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The assessor: does this claim still hold? (RECON-W43.0)
 *
 * <p>This is the engine of the mission's §5 scenario. It reads the evidence
 * that is linked to a claim and produces a {@link ClaimAssessment} — the
 * reasoning result, with its evidence, its contradictions, its scope, its
 * unresolved questions, and, when the evidence supports one, a proposed
 * narrower formulation.
 *
 * <p><b>What it does not do.</b> It does not decide whether the claim is
 * true. It decides what the recorded evidence says about the claim, and it
 * says which parts it could not decide. The evidence is a set of links
 * between claims and sources; this class reads them and nothing else.
 *
 * <p><b>Where the condition of a counterexample lives.</b> An observation
 * that contradicts a claim is stored as a {@link Source} of kind
 * {@link SourceKind#OBSERVATION}, and the condition under which it was made
 * is stored in that source's {@code locator} — for an observation, the
 * locator IS where and under what it was seen. The reading of the
 * counterexample therefore depends on what the observer recorded, and when
 * the observer recorded nothing, the assessor says so instead of guessing.
 *
 * <p><b>The scope comparison is lexical, and it is used in one direction
 * only.</b> The system compares the claim's scope with the
 * counterexample's condition by their stems. When they overlap, the
 * contradiction reaches the scope the claim states. When they do not, the
 * system CANNOT decide whether the observation falls inside that scope —
 * the mission's own example: a penguin refutes "all birds fly" and does not
 * automatically refute "most birds fly under ordinary conditions", because
 * whether a penguin is an ordinary case is a question about the world. The
 * word test is therefore never allowed to produce a REFINEMENT on its own;
 * where it cannot decide, the assessor asks.
 */
public final class ClaimAssessor {

    private ClaimAssessor() {
    }

    /** What the assessor decided about ONE counterexample. */
    record Reading(CounterexampleReading reading, String reason, List<String> alternatives,
                   String clarification) {
    }

    /**
     * Assesses a claim against the evidence linked to it.
     *
     * @param workspaceId the workspace
     * @param claim the claim
     * @param evidence the evidence of that workspace
     * @param sources the sources of that workspace
     * @return the assessment
     */
    public static ClaimAssessment assess(String workspaceId, Claim claim,
                                          List<Evidence> evidence, List<Source> sources) {
        List<ClaimAssessment.EvidenceRef> supporting = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> contradicting = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> qualifying = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> exemplifying = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> insufficient = new ArrayList<>();
        List<Evidence> contextual = new ArrayList<>();

        for (Evidence e : evidence) {
            if (!claim.id().equals(e.claimId())) {
                continue;
            }
            ClaimAssessment.EvidenceRef ref = ref(e, sources);
            switch (e.relation()) {
                case SUPPORTS -> supporting.add(ref);
                case CONTRADICTS -> contradicting.add(ref);
                case QUALIFIES -> qualifying.add(ref);
                case EXEMPLIFIES -> exemplifying.add(ref);
                case FAILS_TO_SUPPORT, INSUFFICIENT -> insufficient.add(ref);
                case CONTEXTUAL -> contextual.add(e);
            }
        }

        // The standing follows the READING, not the raw relation: a
        // counterexample that falls outside the claim's scope, or whose
        // scope relation cannot be decided, does not refute the claim.
        List<ClaimAssessment.EvidenceRef> refuting = new ArrayList<>();
        List<ClaimAssessment.EvidenceRef> undecided = new ArrayList<>();
        CounterexampleReading reading = null;
        String readingReason = null;
        List<String> alternatives = List.of();
        List<String> unresolved = new ArrayList<>();

        for (Evidence e : evidence) {
            if (!claim.id().equals(e.claimId()) || e.relation() != EvidenceRelation.CONTRADICTS) {
                continue;
            }
            Reading decided = readingOf(e, sourceOf(e.sourceId(), sources), claim);
            if (reading == null) {
                reading = decided.reading();
                readingReason = decided.reason();
                alternatives = decided.alternatives();
                if (!decided.clarification().isBlank()) {
                    unresolved.add(decided.clarification());
                }
            }
            // Both REFUTES and REVEALS_MISSING_CONDITION mean that the
            // contradiction REACHES the scope the claim states: the claim
            // is false in it. The other two readings mean that the
            // evidence does not decide, and they do not count against the
            // claim.
            if (decided.reading() == CounterexampleReading.REFUTES
                    || decided.reading() == CounterexampleReading.REVEALS_MISSING_CONDITION) {
                refuting.add(ref(e, sources));
            } else {
                undecided.add(ref(e, sources));
            }
        }
        if (reading != null) {
            unresolved.add("whether the reading itself is the right one");
        }

        ClaimStatus standing = standing(supporting, refuting, undecided, exemplifying,
                insufficient);
        String standingReason = standingReason(standing, supporting, refuting, undecided,
                exemplifying, insufficient, contextual);

        if (insufficient.isEmpty() && supporting.isEmpty() && contradicting.isEmpty()
                && exemplifying.isEmpty() && standing == ClaimStatus.UNKNOWN) {
            unresolved.add("no evidence has been recorded for this claim");
        }

        String proposal = null;
        String proposalReason = null;
        if (reading == CounterexampleReading.REVEALS_MISSING_CONDITION) {
            Evidence counterexample = firstCounterexample(claim, evidence);
            String condition = conditionOf(sourceOf(counterexample.sourceId(), sources));
            proposal = claim.proposition() + " (except where " + condition + ")";
            proposalReason = "the condition is quoted verbatim from the counterexample's "
                    + "observation; the system assembled the sentence and did not decide the "
                    + "wording — that is the user's to accept or reject";
        } else if (!contradicting.isEmpty()) {
            proposalReason = "the recorded evidence does not support a narrower formulation: "
                    + (reading == CounterexampleReading.INCONCLUSIVE
                            ? "the counterexample states no condition"
                            : reading == CounterexampleReading.SCOPE_UNRESOLVED
                                    ? "whether the observation falls inside the claim's scope is "
                                            + "unsettled, and a narrowing the evidence does not "
                                            + "support would be an invention"
                                    : "the claim is contradicted as stated, and narrowing it would "
                                            + "not be a refinement");
        }

        return new ClaimAssessment(
                workspaceId,
                claim.id(),
                claim.proposition(),
                claim.scope(),
                standing,
                standingReason,
                reading,
                readingReason,
                alternatives,
                supporting,
                contradicting,
                qualifying,
                exemplifying,
                insufficient,
                unresolved,
                proposal,
                proposalReason,
                assessedAt());
    }

    /**
     * Reads ONE counterexample against the claim it challenges.
     *
     * @param evidence the counterexample
     * @param source its source
     * @param claim the claim
     * @return what was decided
     */
    static Reading readingOf(Evidence evidence, Source source, Claim claim) {
        String condition = conditionOf(source);
        String claimScope = claim.scope() == null ? "" : claim.scope().strip();

        if (condition == null || condition.isBlank()) {
            return new Reading(CounterexampleReading.INCONCLUSIVE,
                    "the counterexample records no condition, so it cannot be read against the "
                            + "claim's scope; the claim may be false, or the counterexample may be "
                            + "unreliable, or the condition may simply not have been recorded",
                    CounterexampleReading.unresolvedExplanations(),
                    "Under which conditions does this observation contradict the claim?");
        }
        if (claimScope.isBlank()) {
            return new Reading(CounterexampleReading.REFUTES,
                    "the claim is stated without qualification and the observation contradicts it "
                            + "under the recorded condition (" + condition + ")",
                    List.of(),
                    "");
        }
        if (overlaps(claimScope, condition)) {
            // The contradiction reaches the scope the claim states: the claim
            // is not false in general, its SCOPE is too wide.
            return new Reading(CounterexampleReading.REVEALS_MISSING_CONDITION,
                    "the contradiction reaches the scope the claim states (" + claimScope + "), so "
                            + "the claim is not false in general but its scope is too wide; the "
                            + "recorded condition (" + condition + ") narrows it",
                    List.of(),
                    "");
        }
        // The mission's own worked example: a penguin refutes "all birds fly"
        // and does not automatically refute "most birds fly under ordinary
        // conditions". Whether this observation falls inside that scope is a
        // question about the world. Deciding it from the words would be
        // treating lexical similarity as semantic equivalence, and it would
        // decide in the direction of a REFINEMENT — so the system does not
        // decide, and it asks.
        return new Reading(CounterexampleReading.SCOPE_UNRESOLVED,
                "the observation's condition (" + condition + ") and the claim's scope ("
                        + claimScope + ") do not overlap, so whether the observation falls inside "
                        + "the scope cannot be decided from the text; no narrowing is proposed "
                        + "and the question is asked",
                CounterexampleReading.unresolvedExplanations(),
                CounterexampleReading.SCOPE_UNRESOLVED.clarification());
    }

    /** The first counterexample of a claim. */
    private static Evidence firstCounterexample(Claim claim, List<Evidence> evidence) {
        for (Evidence e : evidence) {
            if (claim.id().equals(e.claimId()) && e.relation() == EvidenceRelation.CONTRADICTS) {
                return e;
            }
        }
        throw new IllegalStateException("no counterexample for " + claim.id());
    }

    /** The epistemic standing implied by the evidence relations. */
    private static ClaimStatus standing(List<ClaimAssessment.EvidenceRef> supporting,
                                        List<ClaimAssessment.EvidenceRef> refuting,
                                        List<ClaimAssessment.EvidenceRef> undecided,
                                        List<ClaimAssessment.EvidenceRef> exemplifying,
                                        List<ClaimAssessment.EvidenceRef> insufficient) {
        if (!refuting.isEmpty() && !supporting.isEmpty()) {
            return ClaimStatus.CONTESTED;
        }
        if (!refuting.isEmpty()) {
            return ClaimStatus.REFUTED;
        }
        if (!supporting.isEmpty()) {
            return ClaimStatus.SUPPORTED;
        }
        if (!undecided.isEmpty()) {
            // The evidence was recorded and does not decide: the claim is
            // neither established nor refuted.
            return ClaimStatus.INSUFFICIENT_EVIDENCE;
        }
        if (!exemplifying.isEmpty()) {
            return ClaimStatus.SUPPORTED;
        }
        if (!insufficient.isEmpty()) {
            return ClaimStatus.INSUFFICIENT_EVIDENCE;
        }
        return ClaimStatus.UNKNOWN;
    }

    /** Why the standing is what it is — what the evidence does and does not establish. */
    private static String standingReason(ClaimStatus standing,
                                         List<ClaimAssessment.EvidenceRef> supporting,
                                         List<ClaimAssessment.EvidenceRef> refuting,
                                         List<ClaimAssessment.EvidenceRef> undecided,
                                         List<ClaimAssessment.EvidenceRef> exemplifying,
                                         List<ClaimAssessment.EvidenceRef> insufficient,
                                         List<Evidence> contextual) {
        return switch (standing) {
            case CONTESTED -> "the evidence both supports and contradicts the claim; the sources "
                    + "disagree and both accounts are kept";
            case REFUTED -> "no evidence supports the claim and " + refuting.size()
                    + " contradicts it within the scope it states";
            case SUPPORTED -> {
                if (supporting.isEmpty()) {
                    yield "supported by " + exemplifying.size() + " example(s) only; an example "
                            + "is consistent with the claim and does not establish it in general";
                }
                if (exemplifying.isEmpty()) {
                    yield "supported by " + supporting.size() + " source(s)";
                }
                yield "supported by " + supporting.size() + " source(s) and " + exemplifying.size()
                        + " example(s); an example is consistent with the claim and does not "
                        + "establish it in general";
            }
            case INSUFFICIENT_EVIDENCE -> undecided.isEmpty()
                    ? "the evidence was sought and does not decide; the absence of support is not "
                            + "evidence of absence"
                    : "the counterexample was recorded, but whether it falls inside the claim's "
                            + "scope is unsettled, so it neither refutes nor supports the claim";
            case UNKNOWN -> contextual.isEmpty()
                    ? "no evidence has been recorded for this claim"
                    : "nothing in the recorded evidence bears on the claim";
            default -> standing.name();
        };
    }

    /**
     * Whether two phrases are about the same thing.
     *
     * <p>It is a STEM-EQUALITY test, and it is used in ONE direction only:
     * to decide that a scope and a condition DO overlap, so that the
     * contradiction reaches the stated scope. The failure mode of a word
     * test is a false "these are the same thing", and it is used where a
     * false "same" makes the system propose a narrowing — a change it would
     * rather not make on its own.
     *
     * <p><b>Why equality and not a substring.</b> The first version of this
     * test matched a stem INSIDE a longer word, and the benchmark caught
     * what that did: "daylight" and "light" matched, so the English cases
     * were read one way, while "свете" and "света" — the same idea in
     * Russian, inflected — did not match, so the Russian cases were read
     * another way. The same logical case was decided differently in two
     * languages. Equality has no such asymmetry: it is wrong in both
     * languages or right in both, which is the only property a lexical test
     * can honestly claim.
     */
    static boolean overlaps(String left, String right) {
        Set<String> a = significantWords(left);
        Set<String> b = significantWords(right);
        for (String word : a) {
            if (b.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** The words of a phrase that carry meaning for a scope comparison. */
    private static Set<String> significantWords(String phrase) {
        Set<String> words = new LinkedHashSet<>();
        if (phrase == null) {
            return words;
        }
        for (String word : phrase.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= MINIMUM_SIGNIFICANT_WORD && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    /** A word shorter than this carries no scope. */
    private static final int MINIMUM_SIGNIFICANT_WORD = 4;

    /** The words that are the same in every scope. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "into", "when", "where",
            "under", "over", "only", "also", "than", "then", "they", "them", "their", "there",
            "these", "those", "will", "shall", "must", "does", "have", "been", "were", "while",
            "где", "или", "как", "при", "под", "над", "это", "эти", "для", "без", "если",
            "когда", "который", "которая", "которые", "так", "же", "бы", "ли", "все",
            "всё", "этом", "того");

    /**
     * The timestamp of the assessment.
     *
     * <p>It records WHEN the reasoning ran and nothing reads it: the
     * conclusion depends on the evidence, not on the clock.
     */
    @io.matrix.auditor.ArticleIPermitted(value = "the timestamp of the assessment; it records "
            + "WHEN the reasoning ran, and no decision reads it")
    private static String assessedAt() {
        return java.time.Instant.now().toString();
    }

    /** The condition of an observation, which is where it was observed. */
    private static String conditionOf(Source source) {
        if (source == null || source.kind() != SourceKind.OBSERVATION) {
            return null;
        }
        return source.locator();
    }

    /** The source of an evidence row. */
    private static Source sourceOf(String sourceId, List<Source> sources) {
        for (Source s : sources) {
            if (s.id().equals(sourceId)) {
                return s;
            }
        }
        return null;
    }

    /** An evidence row with the source it rests on. */
    private static ClaimAssessment.EvidenceRef ref(Evidence e, List<Source> sources) {
        Source source = sourceOf(e.sourceId(), sources);
        return new ClaimAssessment.EvidenceRef(
                e.id(),
                e.relation() == null ? null : e.relation().name(),
                e.sourceId(),
                source == null ? null : source.title(),
                source == null ? null : source.kind().name(),
                e.excerpt(),
                e.recordedAt());
    }
}
