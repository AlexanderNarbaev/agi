package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The epistemic disposition of a question's premise, and of the evidence that bears on it
 * (EPISTEMIC-INTEGRITY P0.1 and P0.2).
 *
 * <p><b>Why this class exists.</b> The asking path had two instruments and neither of them
 * answered the question the user asked. {@link PremiseChecker} found a premise and a
 * REFUTATION, and {@link PremiseAssessment} returned one of four verdicts — but the production
 * asking path used the first and only ever REFUSED, so a premise the workspace knows nothing
 * about was answered as if it held, a premise the corpus contradicts in two directions was
 * answered as if it held, and the caller was never told which of the two had happened. The
 * owner's directive names the defect exactly: <i>unsupported is not refuted</i>, and the repair
 * must tell the five situations apart.
 *
 * <p><b>The five situations, and what each one does to the question.</b>
 *
 * <ul>
 *   <li><b>SUPPORTED</b> — the workspace states the premise. The question proceeds to be
 *       answered, and nothing is added to the answer about the premise.</li>
 *   <li><b>REFUTED</b> — the workspace denies the premise and settles the matter. The question
 *       is refused, and the refusal names the claim and the source that refute it.</li>
 *   <li><b>UNSUPPORTED</b> — nothing in the workspace bears on the premise. The premise is
 *       reported as unsupported and the question still proceeds: an absence of evidence is not
 *       a refutation, and a system that refuses what it cannot confirm refuses everything.</li>
 *   <li><b>CONTRADICTORY</b> — the corpus holds accounts that cannot both hold, or holds
 *       accounts on both sides of the premise. The question is NOT refused: the corpus does not
 *       say which account is right, and the system does not decide. Both sides are reported
 *       with their provenance.</li>
 *   <li><b>AMBIGUOUS</b> — the question admits several defensible readings. The readings are
 *       reported separately, each with its provenance, and none of them is chosen.</li>
 * </ul>
 *
 * <p><b>The refusal is the only disposition that stops the answer.</b> That is deliberate: the
 * directive says that refusing everything is not success, and a false refusal is a first-class
 * defect. A supported premise must still be answered, and a premise the workspace knows
 * nothing about must not be turned into a refusal.
 *
 * <p><b>The separation the project already has.</b> The disposition is a field of its own: it
 * is not the action's disposition, not the epistemic status of the answer, not a limitation and
 * not a refusal reason. A caller that wants to know what the workspace believes about the
 * premise reads the disposition; a caller that wants to know whether the answer was given reads
 * the answer. And a report is a RECORD of what the workspace said — it is never evidence for
 * its own contents.
 *
 * <p><b>It is deterministic.</b> No model, no network, no random number and no wall-clock in any
 * decision path: the same workspace and the same question produce the same disposition, every
 * time.
 */
public final class PremiseSemantics {

    /** The epistemic status of a question's premise. */
    public enum Status {
        /** The question asserts no premise; there is nothing to decide about one. */
        NO_PREMISE,
        /** The workspace states the premise. */
        SUPPORTED,
        /** The workspace denies the premise and settles the matter. */
        REFUTED,
        /** Nothing in the workspace bears on the premise. */
        UNSUPPORTED,
        /** The corpus holds accounts that cannot both hold, or accounts on both sides. */
        CONTRADICTORY,
        /** The question admits several defensible readings. */
        AMBIGUOUS
    }

    /**
     * What KIND of premise the question carries.
     *
     * <p><b>The kind decides whether a refutation stops the question.</b> A premise the
     * question asks UNDER is a background assumption: "since X, what is Y?" takes X for granted
     * and asks under it, so a workspace that denies X must not answer the question as if X
     * held. A proposition the question asks ABOUT is not a background assumption: "did X?" asks
     * whether X holds, and the answer to a proposition that does not hold is "no", not a
     * refusal to answer. A premise that is ATTRIBUTED to somebody ("did X, as Z proposed?") is
     * the subject of the question for the same reason.
     *
     * <p>So only a BACKGROUND premise refuses the question. The other kinds are reported, with
     * their status, and the question is answered.
     */
    public enum Kind {
        /** The question asks under the premise; a refutation stops it. */
        BACKGROUND,
        /** The question attributes the premise to somebody; it is reported. */
        ATTRIBUTED,
        /** The question asks whether the premise holds; it is reported. */
        ASSERTED
    }

    /** One reading of a question that admits several, with the evidence it rests on. */
    public record Reading(String label, List<PremiseAssessment.EvidenceRef> evidence) {

        public Reading {
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
        }
    }

    /**
     * The disposition of a question.
     *
     * @param status what the workspace makes of the premise
     * @param premise the premise in the caller's own words, or null when there is none
     * @param reason why the disposition is what it is
     * @param evidence the evidence that bears on the premise
     * @param conflicts the conflicts the cited evidence holds, both sides named
     * @param readings the readings of an ambiguous question
     */
    public record Disposition(Status status,
                              Kind kind,
                              String premise,
                              String reason,
                              List<PremiseAssessment.EvidenceRef> evidence,
                              List<ClaimConflictDetector.Conflict> conflicts,
                              List<Reading> readings) {

        public Disposition {
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
            conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
            readings = List.copyOf(readings == null ? List.of() : readings);
        }

        /**
         * Whether the disposition stops the question from being answered.
         *
         * <p>Only a premise the question asks UNDER stops it: a proposition the question asks
         * ABOUT is answered, and the answer to a proposition that does not hold is "no".
         */
        public boolean refuses() {
            return status == Status.REFUTED && kind == Kind.BACKGROUND;
        }

        /** Whether the disposition says anything at all about the premise. */
        public boolean reportsAPremise() {
            return status != Status.NO_PREMISE;
        }
    }

    /**
     * The share of the premise's content terms the subject of a denial must cover.
     *
     * <p>It is a policy constant and not a fitted parameter, and the margin between the two
     * measured cases is narrow: the rule says that a denial is about the premise when the
     * words it is about cover this much of what the premise says.
     */
    public static final double MINIMUM_DENIAL_SUBJECT_COVERAGE = 0.30;

    /**
     * The number of CONTENT terms an account must share with a subject to be about it.
     *
     * <p>It is the same rule the conflict detector applies, for the same reason: the frame of
     * two texts is not their content.
     */
    public static final int MINIMUM_SHARED_CONTENT_TERMS = 3;

    /**
     * The share of the subject (or of the account) that the two must cover for the account to
     * be about the subject.
     */
    public static final double MINIMUM_SUBJECT_COVERAGE = 0.30;

    /**
     * The number of readings a term must have before the question is ambiguous.
     *
     * <p>One account of a term settles it; two accounts of it do not, and the system does not
     * choose between them.
     */
    public static final int MINIMUM_READINGS = 2;

    /**
     * The number of accounts of a term that are reported as its readings.
     *
     * <p>It is a bound on the report, not on the search: every account the workspace holds is
     * found, and the ones that are reported are the ones a reader can act on.
     */
    public static final int MAXIMUM_READINGS = 4;

    /**
     * The phrases that name the term a question asks about.
     *
     * <p>They are a property of how a question is asked and not of any corpus: "the word X"
     * and "the term X" introduce the thing whose meaning is in question.
     */
    private static final List<String> TERM_CUES = List.of("the word ", "the term ");

    /**
     * The verbs that ask what a term denotes.
     *
     * <p>A question that names a term but does not ask what it denotes is not asking about the
     * term's meaning — it is asking something else that the term happens to appear in.
     */
    private static final List<String> DENOTATION_VERBS = List.of(
            "name", "denote", "mean", "stand for", "refer to", "signify", "designate");

    /** The phrases that ask about kinds of something. */
    private static final List<String> KIND_CUES = List.of(
            "kinds of ", "types of ", "sorts of ", "forms of ");

    /**
     * The phrases that ask what tells kinds apart.
     *
     * <p>They are what makes a question about kinds a question about a DISTINCTION: "what are
     * the three types of plate boundaries and what happens at each of them" asks for a
     * description of each kind, and "which kinds of arc are distinguished, and what tells them
     * apart" asks for the distinction itself.
     */
    private static final List<String> DISTINCTION_CUES = List.of(
            "distinguish", "differ", "apart", "separate");

    private PremiseSemantics() {
    }

    /**
     * The disposition of a question, read against the workspace it is addressed to.
     *
     * @param workbench the workbench that holds the workspace
     * @param workspaceId the workspace to read
     * @param question the question as the user wrote it
     * @param citedClaims the claims the answer cites; the conflicts are looked for among them
     * @return the disposition
     */
    public static Disposition resolve(KnowledgeWorkbench workbench, String workspaceId,
            String question, List<Claim> citedClaims) {
        if (workbench == null) {
            throw new IllegalArgumentException("a workbench is required to resolve a premise");
        }
        String workspace = workspaceId == null ? "" : workspaceId.strip();
        return resolve(workbench.workspace(workspace), question, citedClaims);
    }

    /**
     * The disposition of a question, read against a workspace that has already been read.
     *
     * @param snapshot the workspace
     * @param question the question as the user wrote it
     * @param citedClaims the claims the answer cites; the conflicts are looked for among them
     * @return the disposition
     */
    public static Disposition resolve(KnowledgeSnapshot snapshot, String question,
            List<Claim> citedClaims) {
        if (snapshot == null) {
            throw new IllegalArgumentException("a workspace is required to resolve a premise");
        }
        Map<String, Source> sources = sourcesById(snapshot);
        List<ClaimConflictDetector.Conflict> conflicts =
                ClaimConflictDetector.detect(citedClaims, sources, snapshot);
        // ONE retriever for the whole resolution (MINOR-4): the assessment and the readings
        // read the same workspace through the same candidate list.
        KnowledgeRetriever retriever =
                new KnowledgeRetriever(snapshot, PremiseAssessment.MINIMUM_RETRIEVAL_COVERAGE);

        String premise = PremiseChecker.premiseOf(question);
        Kind kind = Kind.BACKGROUND;
        if (premise == null) {
            premise = PremiseChecker.presuppositionOf(question);
            kind = Kind.ATTRIBUTED;
        }
        if (premise == null) {
            premise = PremiseChecker.assertionOf(question);
            kind = Kind.ASSERTED;
        }

        // ONE retrieval for the whole resolution (MINOR-4): the accounts the question is read
        // against and the candidates of the premise's assessment are the same list whenever
        // the question's subject IS its premise, which is the case that carries a premise.
        String subject = subjectOf(snapshot, question);
        List<Claim> retrieved = subject == null
                ? List.of() : retriever.answer(subject).claims();
        List<Reading> readings = readingsOf(retrieved, snapshot, question);

        if (premise == null) {
            // A question that asks what a TERM denotes is ambiguous when the workspace holds
            // several accounts of that term. A question that asks about a subject is not made
            // ambiguous by the number of accounts the workspace holds about it: a workspace
            // that describes photosynthesis in seven sentences is not ambiguous, it is
            // thorough, and the accounts are carried as the evidence the answer rests on.
            boolean asksAboutATerm = referentTerm(question) != null;
            if (asksAboutATerm && !readings.isEmpty()) {
                return new Disposition(Status.AMBIGUOUS, kind, null, ambiguityReason(readings),
                        List.of(), conflicts, readings);
            }
            String noPremise = "The question asserts no premise, so there is nothing in it for "
                    + "the workspace to confirm or deny.";
            return new Disposition(Status.NO_PREMISE, kind, null, noPremise,
                    List.of(), conflicts, readings);
        }

        PremiseAssessment.Finding finding =
                PremiseAssessment.assessPremise(premise, retrieved, snapshot);
        List<PremiseAssessment.EvidenceRef> evidence = finding.evidence();
        boolean accountsOnBothSides = hasBothSides(evidence);
        ClaimConflictDetector.Conflict onBothSides = premiseConflict(evidence, sources);
        if (onBothSides != null) {
            conflicts = new ArrayList<>(conflicts);
            conflicts.add(onBothSides);
        }

        Status status;
        String reason;
        List<PremiseAssessment.EvidenceRef> reported = evidence;
        if (accountsOnBothSides) {
            // The workspace holds an account that denies the premise and an account that
            // states it, and it does not say which of them is right. A refutation that ignores
            // the other side is not a refutation, so the question is not refused.
            status = Status.CONTRADICTORY;
            reason = bothSidesReason(evidence);
        } else {
            switch (finding.verdict()) {
                case SUPPORTED -> {
                    status = Status.SUPPORTED;
                    reason = finding.reason();
                }
                case CONTRADICTED -> {
                    if (denialIsAboutThePremise(evidence, premise, null)
                            || !assertedProposition(kind)) {
                        status = Status.REFUTED;
                        reason = finding.reason();
                    } else {
                        // The denial is not ABOUT the premise: it denies a mechanism or a
                        // reception that the premise mentions, and a premise the workspace
                        // does not refute is unsupported, not false. The reason says so in
                        // its own words: a status and a text that disagree are worse than
                        // either of them alone.
                        status = Status.UNSUPPORTED;
                        reason = unsupportedReason(evidence);
                    }
                }
                case UNRESOLVED -> {
                    status = Status.CONTRADICTORY;
                    reason = finding.reason();
                }
                default -> {
                    // The assessment found nothing that bears on the premise. The established
                    // refutation instrument reads a denial the other way round — it asks
                    // whether the CLAIM covers the PREMISE — and it finds refutations the
                    // assessment's own subject rule does not reach. It is consulted here, and
                    // only here: a premise the workspace denies is a premise the workspace
                    // denies.
                    Claim refutation = PremiseChecker.refutation(premise, snapshot);
                    if (refutation == null
                            || assertedProposition(kind)
                            && !denialIsAboutThePremise(evidence, premise, refutation)) {
                        status = Status.UNSUPPORTED;
                        reason = finding.reason();
                    } else {
                        status = Status.REFUTED;
                        reason = refutationReason(refutation, snapshot);
                        reported = withRefutation(evidence, refutation, snapshot);
                    }
                }
            }
        }

        if (referentTerm(question) != null && !readings.isEmpty() && status != Status.REFUTED) {
            // A question that asks what a TERM denotes is ambiguous when the workspace holds
            // several accounts of that term, and the accounts are reported beside the status.
            // A question that asks about a subject is not made ambiguous by the number of
            // accounts the workspace holds about it: a workspace that describes photosynthesis
            // in seven sentences is not ambiguous, it is thorough.
            status = Status.AMBIGUOUS;
            reason = ambiguityReason(readings) + " " + reason;
        }
        if (!conflicts.isEmpty()
                && (status == Status.REFUTED || status == Status.NO_PREMISE)) {
            // The corpus holds accounts that cannot both hold, so it does not settle the
            // premise either: a refutation that sits in a corpus in conflict is not a settled
            // refutation, and a question with no premise can still be answered with a
            // conflict the caller must see.
            status = Status.CONTRADICTORY;
            reason = conflictReason(conflicts) + " " + reason;
        }
        return new Disposition(status, kind, premise, reason, reported, conflicts, readings);
    }

    /**
     * The reason the corpus is in conflict.
     *
     * @param conflicts the conflicts
     * @return the reason
     */
    private static String conflictReason(List<ClaimConflictDetector.Conflict> conflicts) {
        StringBuilder reason = new StringBuilder(
                "The evidence this answer rests on holds claims that cannot both hold ("
                        + conflicts.size() + " conflict(s)); both sides are reported with their "
                        + "provenance and the system does not decide between them: ");
        for (int i = 0; i < conflicts.size(); i++) {
            reason.append(i == 0 ? "" : "; ").append(conflicts.get(i).reason());
        }
        reason.append(".");
        return reason.toString();
    }

    /**
     * Whether the premise is a proposition the question ASKS ABOUT rather than one it asks
     * under.
     *
     * <p><b>Why the two are judged differently.</b> A premise the question asks under — "since
     * X, what is Y?" — is a background assumption, and the workspace's own record of the
     * proposition (a claim held as REFUTED) is the evidence that it does not hold. A
     * proposition the question asks ABOUT — "is X true?" — is the subject of the question: a
     * lexical resemblance is not enough to call it false, because the honest answer to "is X
     * true?" is "no" and not a refutation of the question's subject. The denial has to be
     * about the proposition's own terms there.
     *
     * @param kind the kind of premise
     * @return whether the premise is a proposition the question asks about
     */
    private static boolean assertedProposition(Kind kind) {
        return kind == Kind.ASSERTED || kind == Kind.ATTRIBUTED;
    }

    /**
     * Whether the denial behind a refutation is about the premise itself.
     *
     * <p><b>Why the subject matters.</b> A denial that mentions the premise's words is not
     * always about the premise: "there was no mechanism to explain how continents could plow
     * through ocean crust" denies a MECHANISM, and what it says about the continents is that
     * Wegener's proposal was dismissed, not that the continents did not plow. The subject of
     * the denial is what it denies, and a denial whose subject is not the premise's subject
     * does not refute the premise.
     *
     * @param evidence the evidence that bears on the premise
     * @param premise the premise
     * @return whether the denial is about the premise
     */
    private static boolean denialIsAboutThePremise(List<PremiseAssessment.EvidenceRef> evidence,
            String premise, Claim refutation) {
        Set<String> premiseStems = PremiseAssessment.distinctiveStems(premise);
        for (PremiseAssessment.EvidenceRef reference : evidence) {
            if (!"CONTRADICTS".equals(reference.relation())) {
                continue;
            }
            String denial = PremiseAssessment.denialAbout(reference.proposition(), premiseStems);
            if (denial == null) {
                // The refutation does not rest on a denial — the corpus states the claim as
                // refuted — and then it is about the premise by the corpus's own account.
                continue;
            }
            if (!denialIsAbout(denial, premiseStems)) {
                return false;
            }
        }
        if (refutation != null && refutation.status() != ClaimStatus.REFUTED
                && refutation.status() != ClaimStatus.CONTESTED) {
            Set<String> checkerTerms = new LinkedHashSet<>(TextFeatures.tokens(premise));
            String denial = PremiseChecker.denialOf(refutation, checkerTerms, premise);
            if (denial != null && !denialIsAbout(denial, premiseStems)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a denial is about the premise's subject.
     *
     * <p><b>The rule.</b> The subject of the denial — the words before its first auxiliary —
     * must cover at least {@value #MINIMUM_DENIAL_SUBJECT_COVERAGE} of the premise's content
     * terms. A denial that is about something else mentions the premise's words without
     * denying them: "the elevated He-3:He-4 ratio of ocean island basalts ... has not
     * previously been melted and reproduced in the same way" shares "ocean" and "basalt" with
     * a question about whether the lavas of intraplate volcanoes are chemically identical to
     * mid-ocean ridge basalts, and it denies nothing about them.
     *
     * <p><b>How wide the margin is.</b> The two measured cases sit at 0.33 (the logic
     * package's "validity says nothing about whether the premises of the argument are true")
     * and 0.22 (the claim above). The threshold is between them, and it is a policy constant
     * rather than a fitted parameter; a corpus with a different distribution of these words
     * would move it, and the test that names it would have to move with it.
     *
     * @param denial the denying clause
     * @param premiseStems the premise's content terms
     * @return whether the denial is about the premise
     */
    private static boolean denialIsAbout(String denial, Set<String> premiseStems) {
        Set<String> subject = ClaimConflictDetector.subjectStems(denial);
        if (subject.isEmpty()) {
            return false;
        }
        Set<String> shared = new LinkedHashSet<>(subject);
        shared.retainAll(premiseStems);
        if (shared.isEmpty()) {
            return false;
        }
        return (double) shared.size() / premiseStems.size() >= MINIMUM_DENIAL_SUBJECT_COVERAGE;
    }

    /**
     * The reason a premise is unsupported because the denial beside it is about something else.
     *
     * @param reason the assessment's own reason
     * @return the reason
     */
    private static String unsupportedReason(List<PremiseAssessment.EvidenceRef> evidence) {
        StringBuilder reason = new StringBuilder(
                "The workspace does not settle this premise, and it does not deny it either: "
                        + "nothing here bears on it, so it is unsupported rather than false.");
        for (PremiseAssessment.EvidenceRef reference : evidence) {
            if (!"CONTRADICTS".equals(reference.relation())) {
                continue;
            }
            reason.append(" The account that seemed to deny it (\"").append(reference.proposition())
                    .append("\") denies something else, and the premise is neither confirmed "
                            + "nor refuted by it.");
        }
        return reason.toString();
    }

    /**
     * The reason a premise is refuted, naming the claim and the source that refute it.
     *
     * @param refutation the refuting claim
     * @param snapshot the workspace
     * @return the reason
     */
    private static String refutationReason(Claim refutation, KnowledgeSnapshot snapshot) {
        PremiseAssessment.EvidenceRef reference =
                PremiseAssessment.evidenceRefFor(refutation, "CONTRADICTS", snapshot);
        return "The workspace contradicts this premise: claim " + reference.claimId() + " of \""
                + titleOf(reference) + "\" states \"" + reference.proposition() + "\", and the "
                + "evidence behind that claim contradicts the premise rather than merely failing "
                + "to support it. " + PremiseAssessment.RELATION_BASIS;
    }

    /**
     * The evidence of a finding, with a refutation that the finding did not reach.
     *
     * @param evidence the evidence the assessment found
     * @param refutation the refuting claim
     * @param snapshot the workspace
     * @return the evidence, with the refutation first
     */
    private static List<PremiseAssessment.EvidenceRef> withRefutation(
            List<PremiseAssessment.EvidenceRef> evidence, Claim refutation,
            KnowledgeSnapshot snapshot) {
        List<PremiseAssessment.EvidenceRef> combined = new ArrayList<>();
        combined.add(PremiseAssessment.evidenceRefFor(refutation, "CONTRADICTS", snapshot));
        combined.addAll(evidence);
        return List.copyOf(combined);
    }

    /**
     * Whether the evidence holds accounts on both sides of the premise.
     *
     * <p>An account that denies the premise and an account that states it are two accounts the
     * workspace has not settled against each other, and a refutation that ignores the other
     * side is not a refutation.
     *
     * @param evidence the evidence that bears on the premise
     * @return whether both sides are present
     */
    private static boolean hasBothSides(List<PremiseAssessment.EvidenceRef> evidence) {
        boolean denies = false;
        boolean states = false;
        for (PremiseAssessment.EvidenceRef reference : evidence) {
            if ("CONTRADICTS".equals(reference.relation())) {
                denies = true;
            }
            if ("SUPPORTS".equals(reference.relation())) {
                states = true;
            }
        }
        return denies && states;
    }

    /**
     * The conflict the workspace's accounts make of the premise, when it holds both sides.
     *
     * <p>One account states the premise and another denies it, and the workspace does not say
     * which of them is right. The two are reported with their provenance, and the system does
     * not decide between them.
     *
     * @param evidence the evidence that bears on the premise
     * @param sources the workspace's sources, by identifier
     * @return the conflict, or null
     */
    private static ClaimConflictDetector.Conflict premiseConflict(
            List<PremiseAssessment.EvidenceRef> evidence, Map<String, Source> sources) {
        PremiseAssessment.EvidenceRef denying = null;
        PremiseAssessment.EvidenceRef stating = null;
        for (PremiseAssessment.EvidenceRef reference : evidence) {
            if ("CONTRADICTS".equals(reference.relation())) {
                denying = reference;
            }
            if ("SUPPORTS".equals(reference.relation())) {
                stating = reference;
            }
        }
        if (denying == null || stating == null) {
            return null;
        }
        return new ClaimConflictDetector.Conflict(
                "the workspace holds an account that states the premise and an account that "
                        + "denies it, and it does not say which of them is right",
                List.of(
                        new ClaimConflictDetector.Side(stating.claimId(), stating.proposition(),
                                stating.sourceId(), stating.sourceTitle(), stating.sourceLocator(),
                                "states the premise"),
                        new ClaimConflictDetector.Side(denying.claimId(), denying.proposition(),
                                denying.sourceId(), denying.sourceTitle(), denying.sourceLocator(),
                                "denies it")));
    }

    /**
     * The reason the workspace has not settled the premise.
     *
     * @param evidence the evidence that bears on the premise
     * @return the reason
     */
    private static String bothSidesReason(List<PremiseAssessment.EvidenceRef> evidence) {
        StringBuilder reason = new StringBuilder(
                "The workspace holds accounts on both sides of this premise, so it does not "
                        + "settle it: ");
        boolean first = true;
        for (PremiseAssessment.EvidenceRef reference : evidence) {
            if (!"CONTRADICTS".equals(reference.relation())
                    && !"SUPPORTS".equals(reference.relation())) {
                continue;
            }
            reason.append(first ? "" : "; ").append("claim ").append(reference.claimId())
                    .append(" of \"").append(titleOf(reference)).append("\" ")
                    .append("CONTRADICTS".equals(reference.relation())
                            ? "denies it" : "states it");
            first = false;
        }
        reason.append(". Both sides are reported with their provenance and the system does not "
                + "decide between them.");
        return reason.toString();
    }

    /** The title of the source behind a reference, or a placeholder. */
    private static String titleOf(PremiseAssessment.EvidenceRef reference) {
        return reference.sourceTitle() == null || reference.sourceTitle().isBlank()
                ? "a source without a title"
                : reference.sourceTitle();
    }

    /**
     * The readings a question admits, or an empty list when it admits one.
     *
     * <p>A question asks about a term's MEANING — "which plate does the word slab name" — and
     * the workspace holds several accounts of that term. The accounts are the readings, and
     * each one is reported with the claim and the source it comes from. A question that does
     * not ask about a term's meaning has no readings here, whatever the corpus holds.
     *
     * @param snapshot the workspace
     * @param question the question
     * @return the readings, at most {@link #MAXIMUM_READINGS} of them
     */
    private static List<Reading> readingsOf(List<Claim> retrieved, KnowledgeSnapshot snapshot,
            String question) {
        String subject = subjectOf(snapshot, question);
        if (subject == null) {
            return List.of();
        }
        List<Claim> accounts = accountsAbout(retrieved, subject);
        if (accounts.size() < MINIMUM_READINGS) {
            return List.of();
        }
        List<Reading> readings = new ArrayList<>();
        for (Claim claim : accounts) {
            if (readings.size() >= MAXIMUM_READINGS) {
                break;
            }
            PremiseAssessment.EvidenceRef reference =
                    PremiseAssessment.evidenceRefFor(claim, snapshot);
            readings.add(new Reading(claim.proposition(), List.of(reference)));
        }
        return readings.size() < MINIMUM_READINGS ? List.of() : List.copyOf(readings);
    }

    /**
     * The subject a question asks about, or null when it asks about nothing in particular.
     *
     * <p><b>The three subjects.</b> A question that names a term asks what the term denotes,
     * and the term IS its subject. A question that asserts a premise asks about the subject
     * the premise is about. A question that does neither asks about its own content terms.
     *
     * @param snapshot the workspace
     * @param question the question
     * @return the subject, or null
     */
    private static String subjectOf(KnowledgeSnapshot snapshot, String question) {
        String term = referentTerm(question);
        if (term != null) {
            return term;
        }
        String premise = PremiseChecker.premiseOf(question);
        if (premise == null) {
            premise = PremiseChecker.presuppositionOf(question);
        }
        if (premise == null) {
            premise = PremiseChecker.assertionOf(question);
        }
        if (premise != null) {
            return premise;
        }
        Set<String> terms = QuestionDemand.contentTerms(question);
        if (terms.isEmpty()) {
            return null;
        }
        return String.join(" ", terms);
    }

    /**
     * The accounts the workspace holds ABOUT a subject.
     *
     * <p><b>Why the accounts are about the subject and not merely near it.</b> A reading is a
     * possible answer to the question, and an account that only contains the subject's words
     * is not an answer to it. An account is about the subject when the two share enough of
     * their CONTENT — the frame of the two sides (the conditions they hold under, the symbols
     * they use) does not count — and the account covers the subject, or the subject covers the
     * account.
     *
     * @param snapshot the workspace
     * @param retriever the canonical retrieval component
     * @param subject the subject
     * @return the accounts, the ones that answer the question first
     */
    private static List<Claim> accountsAbout(List<Claim> retrieved, String subject) {
        Set<String> subjectTerms = contentTerms(subject);
        if (subjectTerms.isEmpty()) {
            return List.of();
        }
        List<Claim> accounts = new ArrayList<>();
        for (Claim claim : retrieved) {
            Set<String> claimTerms = contentTerms(claim.proposition());
            if (claimTerms.isEmpty()) {
                continue;
            }
            Set<String> shared = new LinkedHashSet<>(subjectTerms);
            shared.retainAll(claimTerms);
            if (shared.size() < requiredSharedTerms(subjectTerms.size())) {
                continue;
            }
            double overSubject = (double) shared.size() / subjectTerms.size();
            double overClaim = (double) shared.size() / claimTerms.size();
            if (overSubject < MINIMUM_SUBJECT_COVERAGE
                    && overClaim < MINIMUM_SUBJECT_COVERAGE) {
                continue;
            }
            accounts.add(claim);
        }
        // The retrieval's own order decides which accounts are reported first: it is the
        // component that answers the question, and its order is the order of the answer.
        return List.copyOf(accounts);
    }

    /**
     * The number of shared terms an account needs to be about a subject.
     *
     * <p>It is the smaller of the content floor and the size of the subject: a subject of one
     * term cannot be covered by three terms, and asking for three would leave every question
     * about a single term without an account.
     *
     * @param subjectSize the number of the subject's terms
     * @return the number of shared terms required
     */
    private static int requiredSharedTerms(int subjectSize) {
        return Math.max(1, Math.min(MINIMUM_SHARED_CONTENT_TERMS, subjectSize));
    }

    /**
     * The content terms of a text: its distinctive terms, without the conditions it holds
     * under and without the symbols it uses.
     *
     * @param text the text
     * @return the content terms
     */
    private static Set<String> contentTerms(String text) {
        Set<String> terms = new LinkedHashSet<>(PremiseAssessment.distinctiveStems(text));
        terms.removeIf(term -> term.length() < 2);
        return terms;
    }

    /**
     * The reason a question is ambiguous.
     *
     * @param readings the readings
     * @return the reason
     */
    private static String ambiguityReason(List<Reading> readings) {
        StringBuilder reason = new StringBuilder(
                "The question admits more than one reading and the workspace does not say which "
                        + "one is meant. The readings are reported with their provenance and none "
                        + "of them is chosen: ");
        for (int i = 0; i < readings.size(); i++) {
            Reading reading = readings.get(i);
            reason.append(i == 0 ? "" : "; ").append("claim ")
                    .append(reading.evidence().isEmpty() ? "?"
                            : reading.evidence().get(0).claimId())
                    .append(" of \"").append(reading.evidence().isEmpty() ? "a source"
                            : titleOf(reading.evidence().get(0)))
                    .append("\" states \"").append(reading.label()).append("\"");
        }
        reason.append(".");
        return reason.toString();
    }

    /**
     * The term a question asks the meaning of, or null when it asks about no term.
     *
     * <p>Two question forms ask about a term's meaning, and both are forms of the QUESTION and
     * not of any corpus: the question names a term ("the word X") and asks what it denotes, or
     * it asks which kinds of something there are and what tells them apart.
     *
     * @param question the question
     * @return the term, or null
     */
    static String referentTerm(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        if (carriesAny(lower, DENOTATION_VERBS)) {
            for (String cue : TERM_CUES) {
                int at = lower.indexOf(cue);
                if (at < 0) {
                    continue;
                }
                String term = termAfter(lower, at + cue.length());
                if (term != null) {
                    return term;
                }
            }
        }
        if (!carriesAny(lower, DISTINCTION_CUES)) {
            return null;
        }
        for (String cue : KIND_CUES) {
            int at = lower.indexOf(cue);
            if (at < 0) {
                continue;
            }
            String term = termAfter(lower, at + cue.length());
            if (term != null) {
                return term;
            }
        }
        return null;
    }

    /**
     * The term that follows a phrase in a question.
     *
     * @param lower the question, lower-cased
     * @param from the place the term starts at
     * @return the term, or null when there is none
     */
    private static String termAfter(String lower, int from) {
        if (from >= lower.length()) {
            return null;
        }
        int end = from;
        while (end < lower.length() && Character.isLetter(lower.charAt(end))) {
            end++;
        }
        String term = lower.substring(from, end).strip();
        return term.isEmpty() ? null : term;
    }

    /** Whether a text carries any of a list of phrases. */
    private static boolean carriesAny(String text, List<String> phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    /** The workspace's sources, by identifier. */
    private static Map<String, Source> sourcesById(KnowledgeSnapshot snapshot) {
        Map<String, Source> sources = new LinkedHashMap<>();
        for (Source source : snapshot.sources()) {
            sources.put(source.id(), source);
        }
        return sources;
    }
}
