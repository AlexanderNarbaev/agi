package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Assesses a USER ASSERTION against the workspace's own stored knowledge.
 *
 * <p><b>What this is for.</b> W50 forbids what the workbench used to do: a
 * user's statement became a {@code HUMAN_ASSERTION} source and a claim, so
 * the thing the user said could confirm the thing the user said. A user
 * assertion is an INPUT TO EVALUATE, never an independent confirmation.
 * This class is the missing half of that rule: it takes an assertion, reads
 * the claims the workspace already holds, and says what the stored knowledge
 * makes of the assertion — premise by premise.
 *
 * <p><b>Nothing is written.</b> The assessment reads a snapshot and returns a
 * value. No claim, no source, no evidence and no status is created or
 * changed; the returned {@link Assessment#knowledgeEffect()} says so in
 * words, and the test asserts it against the store.
 *
 * <p><b>It is deterministic.</b> There is no model, no network, no random
 * number and no wall-clock in any decision path. The same workspace and the
 * same assertion produce the same assessment, every time. The candidates
 * come from the canonical retrieval component ({@link KnowledgeRetriever}),
 * and the subject matching, the polarity reading and the verdict rules are
 * lexical rules that a reader can check by hand.
 *
 * <p><b>It is domain-neutral.</b> Nothing here knows what a plate is. The
 * relation between a premise and a claim is derived from the TEXT of the two
 * — the terms they share and the negations they carry — and never from a
 * list of topics. A rule that had to know the subject to work would be a
 * rule that would work on one subject and fail on the next.
 *
 * <h2>The rules, in the order they are applied</h2>
 *
 * <ol>
 *   <li><b>The assertion is split into premises.</b> Sentences, and the
 *       clause-like fragments separated by a comma, a semicolon, or the words
 *       "and", "but" and "because". Each premise keeps the wording the
 *       caller wrote, so the caller can quote it back.</li>
 *   <li><b>Each premise is matched against the workspace's claims.</b> The
 *       candidates come from {@link KnowledgeRetriever}, the same component
 *       that answers a question; at most five are kept, ranked by the share
 *       of the premise's distinctive terms they carry.</li>
 *   <li><b>A claim is "about the same subject" when it shares at least two
 *       distinctive terms with the premise and the overlap is at least
 *       {@value #MINIMUM_SUBJECT_OVERLAP}.</li>
 *   <li><b>The relation follows from the text.</b> CONTRADICTS when one side
 *       denies what the other asserts about that subject; SUPPORTS when the
 *       two state the same thing; MENTIONS when they are about the same
 *       subject and bear no relation to each other.</li>
 *   <li><b>The verdict follows from the relations.</b> CONTRADICTED when the
 *       workspace contradicts the premise; SUPPORTED when it supports it and
 *       does not contradict it; UNRESOLVED when the workspace holds
 *       contradicting accounts that do not settle each other; UNSUPPORTED
 *       when nothing in the workspace is about the premise's subject.</li>
 * </ol>
 *
 * <p><b>Absence of evidence is not a contradiction.</b> A premise the
 * workspace knows nothing about is UNSUPPORTED, and the reason says so
 * plainly: nothing here bears on it, and that is not the same as the
 * workspace saying it is false.
 *
 * <p><b>The negation is SCOPED.</b> "Plants release oxygen during the day,
 * not at night" does not contradict "Plants release oxygen during the day":
 * the denial is about the night, not about the day. The rule this class
 * follows is the one {@link PremiseChecker} already learned to its cost: a
 * denial counts against a premise when the denied clause is ABOUT the
 * premise's subject, and not otherwise. A denial is about the other side
 * when the other side carries the denied clause's terms — at least
 * {@value #MINIMUM_DENIAL_COVERAGE} of them. A claim that happens to name
 * the same two words without saying anything about what was denied does not
 * deny it.
 *
 * <p><b>The verdict's precedence.</b> A contradicting account decides the
 * premise: the workspace holds a claim that denies it, and the assessment
 * says so. What the system does NOT do is decide between two accounts that
 * contradict the premise AND contradict each other — then the premise is
 * UNRESOLVED, because the workspace does not say which of them is right. Two
 * contradicting accounts that state the SAME thing do settle each other,
 * and the premise is contradicted.
 *
 * <p><b>A limitation has to be quantified.</b> "Only 5 to 10% of the cases
 * show it" limits what "the cases show it" asserts, and the two contradict
 * each other. An unquantified "only" narrows a claim without denying it:
 * "X is driven only by Y" does not contradict "X is driven by Y" — it says
 * less, and less is not the opposite.
 */
public final class PremiseAssessment {

    /** The verdict on one premise. */
    public enum Verdict {
        /** The workspace holds a claim that states the same thing. */
        SUPPORTED,
        /** The workspace holds a claim that denies the premise. */
        CONTRADICTED,
        /** Nothing in the workspace bears on the premise. */
        UNSUPPORTED,
        /** The workspace holds contradicting accounts and does not settle them. */
        UNRESOLVED
    }

    /**
     * One piece of evidence bearing on a premise.
     *
     * <p>The excerpt is the text of the evidence record when the claim has
     * one, and the claim's own proposition when it does not — the reader is
     * always told WHICH text was read.
     */
    public record EvidenceRef(
            String claimId,
            String proposition,
            String sourceId,
            String sourceTitle,
            String sourceLocator,
            String excerpt,
            String relation,
            double claimConfidence,
            ClaimStatus claimStatus) {
    }

    /** One assessed premise of the assertion. */
    public record Finding(
            String premise,
            Verdict verdict,
            String reason,
            List<EvidenceRef> evidence,
            String disputedPremise) {

        public Finding {
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
        }
    }

    /** The assessment of the whole assertion. */
    public record Assessment(
            String workspaceId,
            String assertion,
            List<Finding> findings,
            String summary,
            boolean promoted,
            String knowledgeEffect) {

        public Assessment {
            findings = List.copyOf(findings == null ? List.of() : findings);
        }
    }

    /** How many claims one premise may bring as evidence. */
    public static final int MAXIMUM_EVIDENCE = 5;

    /**
     * The share of a premise's distinctive terms a claim must carry to be
     * "about the same subject".
     *
     * <p>It is a policy constant and not a fitted parameter. The share is
     * measured on the PREMISE, because the premise is the thing being
     * assessed: a claim that carries two of the premise's five terms is
     * about the premise's subject, and one that carries none of them is not.
     */
    public static final double MINIMUM_SUBJECT_OVERLAP = 0.30;

    /** The number of distinctive terms two texts must share to be one subject. */
    public static final int MINIMUM_SHARED_TERMS = 2;

    /**
     * The share of a denied clause's terms the other side must carry.
     *
     * <p>A denial counts against the other side only when the other side is
     * about what was denied. A claim that mentions the same two words
     * without saying anything about them does not deny them, and reading it
     * as a contradiction would be a contradiction the text does not contain.
     */
    public static final double MINIMUM_DENIAL_COVERAGE = 0.50;

    /**
     * The coverage a retrieved claim must reach before it is a candidate.
     *
     * <p>The floor is LOW on purpose: the retriever decides what it can
     * reach, and this class decides what is about the same subject. A floor
     * of the retriever's own would silently decide the second question with
     * the first one's rule.
     */
    private static final double MINIMUM_RETRIEVAL_COVERAGE = 0.10;

    /**
     * The words that DENY.
     *
     * <p>A denial flips the polarity of what is asserted. The multi-word
     * markers are phrases, and they are matched as phrases.
     */
    private static final List<String> DENIAL_MARKERS = List.of(
            "not", "no", "never", "cannot", "can't", "nothing", "neither", "nor",
            "isn't", "doesn't", "wasn't", "aren't", "weren't", "don't", "didn't",
            "without", "rather than", "instead of",
            // A rejection is a denial in the vocabulary of the science:
            // "widely rejected", "rejection of", "was denied", "refuted",
            // "discredited". Without these words a source that refutes a
            // theory reads as a source that merely mentions it.
            "rejected", "rejection", "denied", "denial", "refuted", "discredited",
            "opposed", "dismissed");

    /**
     * The words that LIMIT.
     *
     * <p>A limitation narrows what is asserted; it does not assert the
     * opposite. "Only", "merely" and "minor" say HOW MUCH is claimed, and a
     * text that carries one of them is not the opposite of a text that does
     * not — it is a text that says less. That is why the limitation markers
     * are kept apart from the denial markers: counting "only" as a denial
     * would make "X is driven only by Y" and "X is not driven by Z" look
     * like the same polarity, and the two would never be read as opposite.
     */
    private static final List<String> RESTRICTION_MARKERS = List.of(
            "only", "merely", "minor", "side effect", "minor side");

    /**
     * The single words that are markers and therefore carry no subject.
     *
     * <p>They are left out of the distinctive terms, so that "not" and
     * "cannot" do not make two texts about the same subject, and so that the
     * comparison of two propositions can ignore the polarity and look at
     * what is actually asserted.
     */
    private static final Set<String> MARKER_WORDS = Set.of(
            "not", "no", "never", "cannot", "can't", "nothing", "neither", "nor",
            "isn't", "doesn't", "wasn't", "aren't", "weren't", "don't", "didn't",
            "without", "only", "merely", "minor",
            "rejected", "rejection", "denied", "denial", "refuted", "discredited",
            "opposed", "dismissed",
            "не", "нет", "ни", "никогда", "ничего", "нельзя", "без");

    /** The words that separate one clause from the next. */
    private static final Set<String> CLAUSE_SEPARATORS = Set.of("and", "but", "because");

    /** What the assessment did to the workspace: nothing. */
    private static final String NO_CHANGE =
            "The assertion was evaluated against the workspace's stored claims and was not "
                    + "stored as a claim. No source, evidence, claim or status was created or "
                    + "changed: the workspace's claims, evidence and statuses are unchanged.";

    private PremiseAssessment() {
    }

    // ── the assessment ───────────────────────────────────────────────────────

    /**
     * Assesses an assertion against the workspace's stored knowledge.
     *
     * <p>The assertion is an INPUT TO EVALUATE. It is not stored, it is not
     * turned into a source, and it never confirms itself.
     *
     * @param workbench the workbench that holds the workspace
     * @param workspaceId the workspace to read
     * @param assertion what the user asserted
     * @return the assessment, with a finding per premise
     */
    public static Assessment assess(KnowledgeWorkbench workbench, String workspaceId,
            String assertion) {
        if (workbench == null) {
            throw new IllegalArgumentException("a workbench is required to assess an assertion");
        }
        String workspace = workspaceId == null ? "" : workspaceId.strip();
        String text = assertion == null ? "" : assertion.strip();
        if (text.isEmpty()) {
            return new Assessment(workspace, text, List.of(),
                    "There is no assertion to assess.", false, NO_CHANGE);
        }
        KnowledgeSnapshot snapshot = workbench.workspace(workspace);
        List<String> premises = premisesOf(text);
        if (premises.isEmpty()) {
            return new Assessment(workspace, text, List.of(),
                    "The assertion carries no premise that could be assessed.", false, NO_CHANGE);
        }
        KnowledgeRetriever retriever = new KnowledgeRetriever(snapshot, MINIMUM_RETRIEVAL_COVERAGE);
        List<Finding> findings = new ArrayList<>();
        for (String premise : premises) {
            findings.add(assessPremise(premise, retriever, snapshot));
        }
        return new Assessment(workspace, text, List.copyOf(findings), summaryOf(findings), false,
                NO_CHANGE);
    }

    /**
     * The assessment of one premise.
     *
     * @param premise the premise, in the caller's own words
     * @param retriever the canonical retrieval component
     * @param snapshot the workspace
     * @return the finding
     */
    private static Finding assessPremise(String premise, KnowledgeRetriever retriever,
            KnowledgeSnapshot snapshot) {
        Set<String> premiseStems = distinctiveStems(premise);
        List<Candidate> candidates = candidatesFor(premise, premiseStems, retriever, snapshot);
        if (candidates.isEmpty()) {
            return new Finding(premise, Verdict.UNSUPPORTED,
                    "No claim in this workspace is about the same subject as this premise, so "
                            + "nothing here bears on it. The absence of evidence is not a "
                            + "contradiction: the workspace neither confirms nor refutes this "
                            + "premise.",
                    List.of(), null);
        }

        List<EvidenceRef> contradicting = new ArrayList<>();
        List<EvidenceRef> supporting = new ArrayList<>();
        List<EvidenceRef> mentioning = new ArrayList<>();
        List<Claim> contradictingClaims = new ArrayList<>();
        String disputedClause = null;
        for (Candidate candidate : candidates) {
            Claim claim = candidate.claim();
            Reading reading = read(premise, claim);
            EvidenceRef ref = evidenceRef(claim, reading.relation(), snapshot);
            if ("CONTRADICTS".equals(reading.relation())) {
                contradicting.add(ref);
                contradictingClaims.add(claim);
                if (disputedClause == null) {
                    disputedClause = reading.disputedClause();
                }
            } else if ("SUPPORTS".equals(reading.relation())) {
                supporting.add(ref);
            } else {
                mentioning.add(ref);
            }
        }

        List<EvidenceRef> evidence = new ArrayList<>(contradicting);
        evidence.addAll(supporting);
        evidence.addAll(mentioning);

        Verdict verdict;
        String reason;
        String disputed = null;
        if (contradicting.size() >= 2 && !agreeOn(contradictingClaims)) {
            // The workspace holds contradicting accounts that do not settle
            // each other: it does not say which of them is right, and the
            // system does not decide.
            verdict = Verdict.UNRESOLVED;
            reason = unresolvedReason(contradicting, supporting);
            disputed = disputedClause == null ? premise : disputedClause;
        } else if (!contradicting.isEmpty()) {
            verdict = Verdict.CONTRADICTED;
            reason = contradictedReason(contradicting.get(0));
            disputed = disputedClause == null ? premise : disputedClause;
        } else if (!supporting.isEmpty()) {
            verdict = Verdict.SUPPORTED;
            reason = supportedReason(supporting.get(0));
        } else {
            verdict = Verdict.UNSUPPORTED;
            reason = mentionedOnlyReason(mentioning.get(0));
        }
        return new Finding(premise, verdict, reason, List.copyOf(evidence), disputed);
    }

    // ── the candidates ───────────────────────────────────────────────────────

    /** A claim that is about the premise's subject, with its overlap score. */
    private record Candidate(Claim claim, double overlap) {
    }

    /**
     * The claims that are about the premise's subject.
     *
     * <p>The candidates come from the canonical retrieval component, and the
     * subject rule is applied on top of them: at least two distinctive terms
     * in common, and at least {@value #MINIMUM_SUBJECT_OVERLAP} of the
     * premise's distinctive terms covered.
     *
     * @param premise the premise
     * @param premiseStems the premise's distinctive terms
     * @param retriever the canonical retrieval component
     * @param snapshot the workspace
     * @return the candidates, best first, at most {@link #MAXIMUM_EVIDENCE}
     */
    private static List<Candidate> candidatesFor(String premise, Set<String> premiseStems,
            KnowledgeRetriever retriever, KnowledgeSnapshot snapshot) {
        Set<String> known = new LinkedHashSet<>();
        for (Claim claim : snapshot.claims()) {
            known.add(claim.id());
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Claim claim : retriever.answer(premise).claims()) {
            if (!known.contains(claim.id())) {
                continue;
            }
            Set<String> claimStems = distinctiveStems(claim.proposition());
            Set<String> shared = sharedStems(premiseStems, wordsByStem(premise), claimStems);
            if (shared.size() < MINIMUM_SHARED_TERMS) {
                continue;
            }
            double overlap = premiseStems.isEmpty() ? 0.0
                    : (double) shared.size() / premiseStems.size();
            if (overlap < MINIMUM_SUBJECT_OVERLAP) {
                continue;
            }
            candidates.add(new Candidate(claim, overlap));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::overlap).reversed()
                .thenComparing(candidate -> candidate.claim().id()));
        return candidates.size() > MAXIMUM_EVIDENCE
                ? List.copyOf(candidates.subList(0, MAXIMUM_EVIDENCE))
                : List.copyOf(candidates);
    }

    // ── the relation ─────────────────────────────────────────────────────────

    /** How a claim bears on a premise. */
    private record Reading(String relation, String disputedClause) {
    }

    /**
     * The relation between a premise and a claim, read from the two texts.
     *
     * @param premise the premise
     * @param claim the claim
     * @return the relation and, for a contradiction, the clause of the
     *     premise the claim disputes
     */
    private static Reading read(String premise, Claim claim) {
        String proposition = claim.proposition();
        Set<String> premiseStems = distinctiveStems(premise);
        Set<String> claimStems = distinctiveStems(proposition);

        // A denial counts only when it is ABOUT the other side's subject.
        String premiseDenial = denialAbout(premise, claimStems);
        String claimDenial = denialAbout(proposition, premiseStems);
        if (premiseDenial != null && claimDenial == null) {
            return new Reading("CONTRADICTS", premiseDenial);
        }
        if (claimDenial != null && premiseDenial == null) {
            return new Reading("CONTRADICTS", premise);
        }
        // A LIMITATION of the other side.
        //
        // "Only 5 to 10% of the cases show it" limits what "the cases show
        // it" asserts, and the premise is the broader claim. The limitation
        // has to be QUANTIFIED for this: an unquantified "only" narrows the
        // claim without denying it, and "X is driven only by Y" does not
        // contradict "X is driven by Y" — it says less.
        String premiseLimitation = limitationAbout(premise, claimStems);
        String claimLimitation = limitationAbout(proposition, premiseStems);
        if (claimLimitation != null && premiseLimitation == null) {
            return new Reading("CONTRADICTS", premise);
        }
        if (premiseLimitation != null && claimLimitation == null) {
            return new Reading("CONTRADICTS", premiseLimitation);
        }
        if (sameAssertion(premiseStems, claimStems)) {
            return new Reading("SUPPORTS", null);
        }
        return new Reading("MENTIONS", null);
    }

    /**
     * The clause of the text that LIMITS the other side's subject.
     *
     * <p>A limitation is a restriction with a quantity in it ("only 5 to
     * 10%", "at most a third"). An unquantified restriction is not a
     * limitation of the other side: it is a narrower statement, and a
     * narrower statement does not deny a broader one.
     *
     * @param text the text to read
     * @param otherStems the distinctive terms of the other side
     * @return the limited clause, or null when the text does not limit the
     *     other side's subject
     */
    private static String limitationAbout(String text, Set<String> otherStems) {
        for (String fragment : fragmentsOf(text)) {
            if (!restricts(fragment) || !quantified(fragment)) {
                continue;
            }
            Set<String> fragmentStems = distinctiveStems(fragment);
            if (fragmentStems.size() >= MINIMUM_SHARED_TERMS && covers(fragmentStems, otherStems)) {
                return fragment.strip();
            }
        }
        return null;
    }

    /**
     * Whether a text carries a quantity.
     *
     * @param text the text
     * @return whether it does
     */
    private static boolean quantified(String text) {
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c) || c == '%') {
                return true;
            }
        }
        return false;
    }

    /**
     * The clause of the text that denies the other side's subject.
     *
     * <p>The denial must be SCOPED, and it must be COVERED: the denied
     * clause has to be about what the other side is about, and the other
     * side has to carry the denied clause's terms. "Plants release oxygen
     * during the day, not at night" denies the night, and the night is not
     * what the premise is about; and a claim that talks about the same two
     * words without talking about what was denied does not deny it either.
     *
     * @param text the text to read
     * @param otherStems the distinctive terms of the other side
     * @return the denied clause, or null when the text does not deny the
     *     other side's subject
     */
    private static String denialAbout(String text, Set<String> otherStems) {
        for (String fragment : fragmentsOf(text)) {
            if (!denied(fragment)) {
                continue;
            }
            Set<String> fragmentStems = distinctiveStems(fragment);
            if (fragmentStems.size() < MINIMUM_SHARED_TERMS) {
                continue;
            }
            if (covers(fragmentStems, otherStems)) {
                return fragment.strip();
            }
        }
        return null;
    }

    /**
     * Whether a set of terms is covered by another set.
     *
     * @param terms the terms to be covered
     * @param otherStems the covering terms
     * @return whether at least {@value #MINIMUM_DENIAL_COVERAGE} of the terms
     *     are covered
     */
    private static boolean covers(Set<String> terms, Set<String> otherStems) {
        if (terms.isEmpty()) {
            return false;
        }
        Set<String> shared = new LinkedHashSet<>();
        for (String term : terms) {
            if (otherStems.contains(term)) {
                shared.add(term);
            }
        }
        return (double) shared.size() / terms.size() >= MINIMUM_DENIAL_COVERAGE;
    }

    /**
     * Whether two texts state the same thing.
     *
     * <p>The comparison is on the stemmed content terms, with the polarity
     * markers left out — what is asserted, not how it is qualified. A claim
     * that carries every term of the premise states the premise's content,
     * whether it says exactly that or says it with a qualifier in front
     * ("the oxygen comes from splitting water" against "the oxygen a plant
     * releases comes from splitting water"). A claim that carries LESS of
     * the premise does not state the premise: it states something narrower,
     * and a narrower statement does not establish a broader one.
     *
     * @param premiseStems the premise's distinctive terms
     * @param claimStems the claim's distinctive terms
     * @return whether the two state the same thing
     */
    private static boolean sameAssertion(Set<String> premiseStems, Set<String> claimStems) {
        if (premiseStems.isEmpty() || claimStems.isEmpty()) {
            return false;
        }
        if (premiseStems.equals(claimStems)) {
            return true;
        }
        return premiseStems.size() >= MINIMUM_SHARED_TERMS && claimStems.containsAll(premiseStems);
    }

    /**
     * Whether contradicting claims settle each other.
     *
     * <p>Two claims that contradict the premise AND state the same thing
     * agree with each other: the workspace says one thing, and that thing
     * contradicts the premise. Two claims that contradict the premise and
     * state DIFFERENT things do not settle each other, and the workspace
     * does not say which of them is right.
     *
     * @param claims the contradicting claims
     * @return whether they agree with each other
     */
    private static boolean agreeOn(List<Claim> claims) {
        if (claims.size() < 2) {
            return true;
        }
        Set<String> first = distinctiveStems(claims.get(0).proposition());
        for (Claim claim : claims) {
            if (!distinctiveStems(claim.proposition()).equals(first)) {
                return false;
            }
        }
        return true;
    }

    // ── the evidence ─────────────────────────────────────────────────────────

    /**
     * The evidence reference for a claim.
     *
     * <p>The excerpt is the evidence record's own excerpt when the claim has
     * one, and the claim's proposition when it does not. The source is the
     * one that backs the claim.
     *
     * @param claim the claim
     * @param relation the relation the claim bears on the premise
     * @param snapshot the workspace
     * @return the reference
     */
    private static EvidenceRef evidenceRef(Claim claim, String relation, KnowledgeSnapshot snapshot) {
        Evidence evidence = backingEvidence(claim, snapshot);
        Source source = null;
        if (evidence != null) {
            source = sourceById(snapshot, evidence.sourceId());
        }
        if (source == null) {
            source = sourceById(snapshot, claim.introducedBySourceId());
        }
        String excerpt = evidence != null && evidence.excerpt() != null
                && !evidence.excerpt().isBlank()
                ? evidence.excerpt()
                : claim.proposition();
        return new EvidenceRef(
                claim.id(),
                claim.proposition(),
                source == null ? claim.introducedBySourceId() : source.id(),
                source == null ? null : source.title(),
                source == null ? null : source.locator(),
                excerpt,
                relation,
                claim.confidence(),
                claim.status());
    }

    /**
     * The evidence record that backs a claim.
     *
     * <p>The record that SUPPORTS the claim is the one that says where the
     * claim came from; a record that contradicts it says something else
     * about the claim and is only used when the claim has nothing else.
     *
     * @param claim the claim
     * @param snapshot the workspace
     * @return the evidence record, or null when the claim has none
     */
    private static Evidence backingEvidence(Claim claim, KnowledgeSnapshot snapshot) {
        List<Evidence> records = new ArrayList<>();
        for (Evidence evidence : snapshot.evidence()) {
            if (evidence.claimId().equals(claim.id())) {
                records.add(evidence);
            }
        }
        for (EvidenceRelation relation : List.of(EvidenceRelation.SUPPORTS,
                EvidenceRelation.EXEMPLIFIES, EvidenceRelation.QUALIFIES,
                EvidenceRelation.CONTEXTUAL, EvidenceRelation.INSUFFICIENT,
                EvidenceRelation.FAILS_TO_SUPPORT, EvidenceRelation.CONTRADICTS)) {
            for (Evidence evidence : records) {
                if (evidence.relation() == relation) {
                    return evidence;
                }
            }
        }
        return records.isEmpty() ? null : records.get(0);
    }

    /** The source with the given identifier, or null. */
    private static Source sourceById(KnowledgeSnapshot snapshot, String sourceId) {
        if (sourceId == null) {
            return null;
        }
        for (Source source : snapshot.sources()) {
            if (sourceId.equals(source.id())) {
                return source;
            }
        }
        return null;
    }

    // ── the reasons and the summary ──────────────────────────────────────────

    /** The reason a premise is contradicted. */
    private static String contradictedReason(EvidenceRef ref) {
        return "The workspace contradicts this premise: claim " + ref.claimId() + " of \""
                + titleOf(ref) + "\" states \"" + ref.proposition() + "\", and the evidence "
                + "behind that claim contradicts the premise rather than merely failing to "
                + "support it.";
    }

    /** The reason a premise is supported. */
    private static String supportedReason(EvidenceRef ref) {
        return "The workspace supports this premise: claim " + ref.claimId() + " of \""
                + titleOf(ref) + "\" states the same proposition (\"" + ref.proposition()
                + "\") and carries evidence for it.";
    }

    /** The reason a premise is left unresolved. */
    private static String unresolvedReason(List<EvidenceRef> contradicting, List<EvidenceRef> supporting) {
        StringBuilder reason = new StringBuilder(
                "The workspace holds contradicting accounts of this premise (");
        for (int i = 0; i < contradicting.size(); i++) {
            EvidenceRef ref = contradicting.get(i);
            reason.append(i == 0 ? "" : ", ").append("claim ").append(ref.claimId())
                    .append(" of \"").append(titleOf(ref)).append("\"");
        }
        reason.append(") and it does not say which of them is right, so the premise is left "
                + "unresolved");
        if (!supporting.isEmpty()) {
            reason.append("; claim ").append(supporting.get(0).claimId())
                    .append(" of \"").append(titleOf(supporting.get(0)))
                    .append("\" states it as well");
        }
        reason.append(". The system does not decide between them.");
        return reason.toString();
    }

    /** The reason a premise is unsupported although the workspace mentions it. */
    private static String mentionedOnlyReason(EvidenceRef ref) {
        return "The workspace mentions the subject of this premise — claim " + ref.claimId()
                + " of \"" + titleOf(ref) + "\" states \"" + ref.proposition() + "\" — but that "
                + "claim does not bear on the premise, so the premise has no supporting "
                + "evidence here. The absence of evidence is not a contradiction: the workspace "
                + "neither confirms nor refutes this premise.";
    }

    /** The title of the source behind a reference, or a placeholder. */
    private static String titleOf(EvidenceRef ref) {
        return ref.sourceTitle() == null || ref.sourceTitle().isBlank()
                ? "a source without a title"
                : ref.sourceTitle();
    }

    /**
     * The summary of the whole assertion.
     *
     * <p>It counts the verdicts and names the sources that contradict. It
     * says nothing the evidence does not support: no adjective, no
     * conclusion about the truth of the assertion.
     *
     * @param findings the findings
     * @return the summary
     */
    private static String summaryOf(List<Finding> findings) {
        int contradicted = 0;
        int supported = 0;
        int unsupported = 0;
        int unresolved = 0;
        Set<String> sources = new LinkedHashSet<>();
        for (Finding finding : findings) {
            switch (finding.verdict()) {
                case CONTRADICTED -> {
                    contradicted++;
                    for (EvidenceRef ref : finding.evidence()) {
                        if ("CONTRADICTS".equals(ref.relation())) {
                            sources.add(titleOf(ref));
                        }
                    }
                }
                case SUPPORTED -> supported++;
                case UNRESOLVED -> {
                    unresolved++;
                    for (EvidenceRef ref : finding.evidence()) {
                        if ("CONTRADICTS".equals(ref.relation())) {
                            sources.add(titleOf(ref));
                        }
                    }
                }
                default -> unsupported++;
            }
        }
        StringBuilder summary = new StringBuilder();
        summary.append(findings.size()).append(findings.size() == 1
                ? " premise was assessed: " : " premises were assessed: ");
        summary.append(contradicted).append(" contradicted, ")
                .append(supported).append(" supported, ")
                .append(unsupported).append(" unsupported, ")
                .append(unresolved).append(" unresolved.");
        if (!sources.isEmpty()) {
            summary.append(" The contradicting evidence comes from ")
                    .append(String.join("; ", sources)).append(".");
        }
        return summary.toString();
    }

    // ── the text ─────────────────────────────────────────────────────────────

    /**
     * The premises of an assertion.
     *
     * <p>Sentences first, then the clause-like fragments inside them. Each
     * premise keeps the wording of the assertion, so that the caller can
     * quote it.
     *
     * @param assertion the assertion
     * @return the premises, in the order they were written
     */
    private static List<String> premisesOf(String assertion) {
        List<String> premises = new ArrayList<>();
        for (String sentence : sentencesOf(assertion)) {
            for (String fragment : fragmentsOf(sentence)) {
                String premise = trimClause(fragment);
                if (premise.isEmpty() || distinctiveStems(premise).isEmpty()) {
                    continue;
                }
                if (!premises.contains(premise)) {
                    premises.add(premise);
                }
            }
        }
        return premises;
    }

    /**
     * The sentences of a text.
     *
     * <p>A full stop between two digits is a decimal point and not the end
     * of a sentence.
     *
     * @param text the text
     * @return its sentences
     */
    private static List<String> sentencesOf(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            boolean ends = c == '.' || c == '!' || c == '?';
            if (c == '.' && i > 0 && i + 1 < text.length()
                    && Character.isDigit(text.charAt(i - 1))
                    && Character.isDigit(text.charAt(i + 1))) {
                ends = false;
            }
            if (ends) {
                sentences.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            sentences.add(current.toString());
        }
        return sentences;
    }

    /**
     * The clause-like fragments of a sentence.
     *
     * <p>A comma, a semicolon, or the words "and", "but" and "because" end a
     * fragment. The wording of the sentence is kept: the fragments are
     * substrings of it.
     *
     * @param sentence the sentence
     * @return its fragments
     */
    private static List<String> fragmentsOf(String sentence) {
        List<String> fragments = new ArrayList<>();
        int start = 0;
        int i = 0;
        int length = sentence.length();
        while (i < length) {
            char c = sentence.charAt(i);
            if (c == ',' || c == ';') {
                fragments.add(sentence.substring(start, i));
                start = i + 1;
                i++;
                continue;
            }
            if (Character.isLetter(c)) {
                int end = i;
                while (end < length && Character.isLetter(sentence.charAt(end))) {
                    end++;
                }
                String word = sentence.substring(i, end).toLowerCase(Locale.ROOT);
                boolean before = i == 0 || !Character.isLetterOrDigit(sentence.charAt(i - 1));
                boolean after = end >= length || !Character.isLetterOrDigit(sentence.charAt(end));
                if (before && after && CLAUSE_SEPARATORS.contains(word)) {
                    fragments.add(sentence.substring(start, i));
                    start = end;
                    i = end;
                    continue;
                }
                i = end;
                continue;
            }
            i++;
        }
        fragments.add(sentence.substring(start));
        return fragments;
    }

    /** A clause without its trailing punctuation. */
    private static String trimClause(String clause) {
        String trimmed = clause.strip();
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == '.' || last == '!' || last == '?' || last == ',' || last == ';') {
                trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
                continue;
            }
            break;
        }
        return trimmed;
    }

    /**
     * The words of a text, lower-cased, with the apostrophes kept.
     *
     * <p>"doesn't" is one word here and two in a tokenizer that splits on
     * every non-letter: the denial markers are contractions, and a
     * contraction that is cut in half is not a marker.
     *
     * @param text the text
     * @return its words
     */
    private static List<String> wordsOf(String text) {
        List<String> words = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return words;
        }
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '\'' || c == '’') {
                current.append(c);
            } else if (current.length() > 0) {
                words.add(current.toString().toLowerCase(Locale.ROOT));
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            words.add(current.toString().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /**
     * The distinctive terms of a text: the content words, without the
     * stopwords and without the polarity markers, stemmed.
     *
     * @param text the text
     * @return the terms
     */
    private static Set<String> distinctiveStems(String text) {
        Set<String> stems = new LinkedHashSet<>();
        TextProfile profile = TextProfiles.ofText(text == null ? "" : text);
        for (String word : wordsOf(text)) {
            if (word.isEmpty() || profile.isStopWord(word) || MARKER_WORDS.contains(word)) {
                continue;
            }
            String stem = stem(word);
            if (stem.isEmpty()) {
                continue;
            }
            stems.add(stem);
        }
        return stems;
    }

    /**
     * The word each stem came from.
     *
     * <p>The bilingual lexicon is a table of stems, and a lookup that misses
     * is a bridge that does not reach. The word is kept so that the lookup
     * can be made with the lexicon's own fold.
     *
     * @param text the text
     * @return stem to word
     */
    private static Map<String, String> wordsByStem(String text) {
        Map<String, String> words = new LinkedHashMap<>();
        TextProfile profile = TextProfiles.ofText(text == null ? "" : text);
        for (String word : wordsOf(text)) {
            if (word.isEmpty() || profile.isStopWord(word) || MARKER_WORDS.contains(word)) {
                continue;
            }
            words.putIfAbsent(stem(word), word);
        }
        return words;
    }

    /**
     * The crude stem of a word.
     *
     * <p>A plural, a gerund, a past tense and a silent "e" are folded off, so
     * that "cause" and "causes" meet and so do "move" and "moving". It is a
     * fold and not a stemmer: a full stemmer merges words that are not the
     * same word, and a retrieval that confuses them is worse than one that
     * misses.
     *
     * @param word the word
     * @return the stem
     */
    private static String stem(String word) {
        String stem = word.toLowerCase(Locale.ROOT);
        if (stem.endsWith("'s")) {
            stem = stem.substring(0, stem.length() - 2);
        } else if (stem.endsWith("'")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        if (stem.length() <= 2) {
            return stem;
        }
        if (stem.endsWith("ies") && stem.length() > 4) {
            stem = stem.substring(0, stem.length() - 3) + "y";
        } else if (stem.endsWith("es") && stem.length() > 3) {
            stem = stem.substring(0, stem.length() - 2);
        } else if (stem.endsWith("s") && stem.length() > 3 && !stem.endsWith("ss")) {
            stem = stem.substring(0, stem.length() - 1);
        }
        if (stem.endsWith("ing") && stem.length() > 5) {
            stem = stem.substring(0, stem.length() - 3);
        } else if (stem.endsWith("ed") && stem.length() > 4) {
            stem = stem.substring(0, stem.length() - 2);
        }
        if (stem.endsWith("e") && stem.length() > 3) {
            stem = stem.substring(0, stem.length() - 1);
        }
        return stem;
    }

    /**
     * The terms two texts share.
     *
     * <p>A shared term is either the same stem on both sides, or a pair the
     * bilingual lexicon says names the same thing. The lexicon is a SEARCH
     * aid and is used only here: it widens the reach of a subject match and
     * never makes two propositions equal.
     *
     * @param leftStems the left side's terms
     * @param leftWords the left side's words, by stem
     * @param rightStems the right side's terms
     * @return the shared terms
     */
    private static Set<String> sharedStems(Set<String> leftStems, Map<String, String> leftWords,
            Set<String> rightStems) {
        Set<String> shared = new LinkedHashSet<>();
        for (String stem : leftStems) {
            if (rightStems.contains(stem)) {
                shared.add(stem);
                continue;
            }
            String word = leftWords.get(stem);
            if (word == null) {
                continue;
            }
            for (String counterpart : counterpartsOf(word)) {
                if (coveredBy(rightStems, counterpart)) {
                    shared.add(stem);
                    break;
                }
            }
        }
        return shared;
    }

    /**
     * The counterparts the bilingual lexicon holds for a word.
     *
     * @param word the word
     * @return the counterparts
     */
    private static Set<String> counterpartsOf(String word) {
        Set<String> counterparts = new LinkedHashSet<>();
        String language = TextProfiles.detect(word);
        counterparts.addAll(BilingualLexicon.instance().translations(word));
        counterparts.addAll(BilingualLexicon.instance()
                .translations(TextProfiles.ofLanguage(language).fold(word)));
        return counterparts;
    }

    /**
     * Whether a counterpart is covered by a set of terms.
     *
     * <p>A counterpart may be a phrase, and a phrase is covered when every
     * one of its words is.
     *
     * @param stems the terms
     * @param counterpart the counterpart
     * @return whether it is covered
     */
    private static boolean coveredBy(Set<String> stems, String counterpart) {
        if (counterpart == null || counterpart.isBlank()) {
            return false;
        }
        if (!counterpart.contains(" ")) {
            return stems.contains(stem(counterpart));
        }
        for (String word : counterpart.split(" ")) {
            if (!stems.contains(stem(word))) {
                return false;
            }
        }
        return true;
    }

    // ── the polarity ─────────────────────────────────────────────────────────

    /**
     * Whether a text denies.
     *
     * <p>The English markers are the ones the wave named; a Russian text is
     * read with the Russian profile's negations, because a rule that works
     * in one language and silently fails in the other is a rule that works
     * only where it was tested.
     *
     * @param text the text
     * @return whether it denies
     */
    private static boolean denied(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        for (String marker : DENIAL_MARKERS) {
            if (containsWord(normalized, marker)) {
                return true;
            }
        }
        if ("ru".equals(TextProfiles.detect(normalized))) {
            for (String word : wordsOf(normalized)) {
                for (String negation : TextProfiles.RUSSIAN.negations()) {
                    if (word.equals(negation)) {
                        return true;
                    }
                    if (negation.length() <= 2 && word.startsWith(negation)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether a text limits what it asserts.
     *
     * @param text the text
     * @return whether it carries a limitation marker
     */
    private static boolean restricts(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        for (String marker : RESTRICTION_MARKERS) {
            if (containsWord(normalized, marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The text as the markers are written: lower-cased, with the typographic
     * apostrophe turned into the plain one.
     *
     * @param text the text
     * @return the normalized text
     */
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('’', '\'');
    }

    /**
     * Whether a text carries a marker as a whole word.
     *
     * @param text the normalized text
     * @param marker the marker
     * @return whether it is present
     */
    private static boolean containsWord(String text, String marker) {
        if (marker.contains(" ")) {
            return text.contains(marker);
        }
        int from = 0;
        while (from <= text.length()) {
            int at = text.indexOf(marker, from);
            if (at < 0) {
                return false;
            }
            boolean before = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            int end = at + marker.length();
            boolean after = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (before && after) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }
}
