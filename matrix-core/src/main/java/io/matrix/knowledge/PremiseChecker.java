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
 * Checks the premise a question asserts (RECON-W42.5).
 *
 * <p>The mission's §14 asks the system to "refuse to answer a question it
 * cannot support", and the benchmark holds two questions whose premise is
 * false:
 *
 * <ul>
 *   <li>*"Since photosynthesis produces oxygen from carbon dioxide, how much
 *       oxygen does a plant release in an hour?"* — the oxygen comes from the
 *       splitting of water;</li>
 *   <li>*"Since every valid argument has true premises, what are the
 *       premises of modus ponens?"* — validity says nothing about whether
 *       the premises are true.</li>
 * </ul>
 *
 * <p><b>The check is against the workspace's own claims, not against a
 * world model.</b> The workspace already holds what is needed:
 *
 * <ul>
 *   <li>the photosynthesis package holds the misconception itself as a
 *       {@link ClaimStatus#REFUTED} claim — *"The oxygen a plant releases
 *       comes from the carbon dioxide it absorbs"* — with the scope "The
 *       misconception, stated so that it can be refuted";</li>
 *   <li>the logic package holds a verified claim that denies the second
 *       premise outright — *"An argument of the modus ponens form is valid,
 *       but validity says nothing about whether the premises of the
 *       argument are true."*</li>
 * </ul>
 *
 * <p><b>What makes a claim a refutation.</b> The claim's content terms must
 * cover the premise's, and the claim must either
 *
 * <ul>
 *   <li>have a status that says the proposition does not hold
 *       ({@link ClaimStatus#REFUTED} or {@link ClaimStatus#DISPUTED}), or</li>
 *   <li>carry a negation the premise does not — the claim denies what the
 *       premise asserts.</li>
 * </ul>
 *
 * <p>The first version of this check was a <b>support</b> test: the premise
 * had to be covered by a supported claim. It was measured and rejected —
 * it refused a legitimate multi-hop question whose premise the workspace
 * states in different words, and it could not tell the two cases apart. A
 * premise is not refuted by the absence of support; it is refuted by a
 * claim that says the opposite.
 */
public final class PremiseChecker {

    /**
     * The words that introduce a premise.
     *
     * <p>"if " is NOT one of them, and that was measured: a conditional
     * question ("If p implies q and p is true, what follows?") poses the
     * conditional as the QUESTION, while "Since X, ..." asserts X as the
     * reason for the question. With "if " included, three legitimate
     * MULTI_HOP items were refused and the answer accuracy fell from
     * 0.792 to 0.708.
     */
    private static final List<String> MARKERS = List.of(
            "since ", "because ", "assuming ", "given that ",
            // W42.9: the Russian markers. Without them a Russian question
            // asserted a premise that was never extracted, so the workspace
            // was never asked whether it contradicts that premise - the
            // question was answered under a premise the workspace denies.
            "поскольку ", "так как ", "при условии что ");

    /** The shortest premise worth checking. */
    private static final int MINIMUM_PREMISE_LENGTH = 12;

    /**
     * The verbs that REPORT an account rather than assert one.
     *
     * <p>A question that says "as Wegener originally proposed" attributes the account in its
     * main clause to somebody, and the attribution is what makes the clause a premise: the
     * question asks under it, and it is the part the user may have got wrong. The verbs are
     * past-tense reporting verbs, so the question's own verb ("What did Wegener propose?")
     * does not match.
     */
    private static final List<String> REPORTING_VERBS = List.of(
            "proposed", "suggested", "believed", "thought", "claimed", "argued", "held",
            "assumed", "concluded", "maintained", "stated", "supposed", "hypothesized",
            "posited", "postulated", "утверждал", "считал", "предполагал", "заявил");

    /**
     * The phrases that introduce an attribution.
     *
     * <p>An attribution needs a cue as well as a reporting verb: the verb says that an account
     * was reported, and the cue says that the account is being attributed rather than asserted.
     */
    private static final List<String> ATTRIBUTION_CUES = List.of(
            "as ", "according to ", "in the manner ", "in the way ", "как ", "по мнению ");

    /**
     * The interrogative auxiliaries a presupposition opens with.
     *
     * <p>A presupposition extracted from a polar question ("Did the continents plow through the
     * ocean floor?") keeps the question's own words, and the auxiliary is what makes it a
     * question. Removing it turns the clause back into the statement it presupposes, and it is
     * the only word removed: the subject and the predicate are not parsed, because a rule that
     * tried to find them would be a parser and a parser is what this project refused to build.
     */
    private static final List<String> INTERROGATIVE_AUXILIARIES = List.of(
            "do ", "does ", "did ", "is ", "are ", "was ", "were ", "can ", "could ", "will ",
            "would ", "has ", "have ", "had ", "should ", "must ", "may ", "might ");

    /**
     * The share of a premise's content terms a claim must carry for the
     * claim to speak about it.
     *
     * <p>This is a policy constant, not a fitted parameter: it is the
     * point at which a claim is about the premise's subject rather than
     * merely near it. The false-premise items of the benchmark are TWO, so
     * whatever this number is, the measurement it supports is a
     * demonstration and not a generalisation.
     */
    public static final double MINIMUM_COVERAGE = 0.5;

    private PremiseChecker() {
    }

    /**
     * The premise a question asserts, or null when it asserts none.
     *
     * <p>The marker has to be at the START of the question. The first
     * version matched it anywhere, and it found "as " inside ordinary
     * sentences; the measured cost was four wrongly refused questions.
     *
     * @param question the question
     * @return the premise, without the marker that introduced it
     */
    public static String premiseOf(String question) {
        if (question == null) {
            return null;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        for (String marker : MARKERS) {
            if (!lower.startsWith(marker)) {
                continue;
            }
            String premise = question.substring(marker.length()).trim();
            int comma = premise.indexOf(',');
            int mark = premise.indexOf('?');
            int end;
            if (comma >= 0 && mark >= 0) {
                end = Math.min(comma, mark);
            } else if (comma >= 0) {
                end = comma;
            } else if (mark >= 0) {
                end = mark;
            } else {
                end = premise.length();
            }
            if (end > 0) {
                premise = premise.substring(0, end).trim();
            }
            if (premise.length() >= MINIMUM_PREMISE_LENGTH) {
                return premise;
            }
        }
        return null;
    }

    /**
     * The premise a question PRESUPPOSES, or null when it presupposes none.
     *
     * <p><b>Why a second entry point is needed.</b> {@link #premiseOf(String)} finds the
     * premise a question INTRODUCES with a marker — "since X, ...". A question can also
     * presuppose without a marker: "Did the continents plow through the ocean floor, as Wegener
     * originally proposed?" asks under the premise that the continents plowed through the ocean
     * floor, and the attribution is what says so. The measured defect was that the production
     * asking path named no premise at all in six of the six unsupported probes, and three of
     * those six presuppose in exactly this way.
     *
     * <p><b>What an attribution is.</b> A reporting verb ("proposed", "believed", "claimed")
     * inside a clause that carries an attribution cue ("as", "according to", "in the manner").
     * The premise is what comes BEFORE that clause: the account being attributed. The clause
     * itself says who held it, which is not the proposition under question.
     *
     * <p><b>What is NOT done here.</b> The subject and the predicate of the clause are not
     * parsed. The only word removed is the interrogative auxiliary that made the clause a
     * question, and a rule that tried to find the subject would be a parser — and a parser is
     * exactly what this project refused to build.
     *
     * @param question the question
     * @return the presupposed premise, or null
     */
    public static String presuppositionOf(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        int verbAt = lastReportingVerbAt(lower);
        if (verbAt < 0) {
            return null;
        }
        // The attribution clause begins at the clause boundary before the reporting verb, or at
        // the attribution cue when the clause has no boundary of its own.
        int boundary = -1;
        for (int i = verbAt - 1; i >= 0; i--) {
            char c = lower.charAt(i);
            if (c == ',' || c == ';' || c == ':') {
                boundary = i + 1;
                break;
            }
        }
        int start = boundary;
        if (start < 0) {
            start = attributionCueAt(lower, verbAt);
        }
        if (start < 0) {
            return null;
        }
        String clause = lower.substring(start, verbAt);
        if (!carriesAttributionCue(clause)) {
            return null;
        }
        String premise = question.substring(0, start).strip();
        premise = stripTrailingPunctuation(premise);
        premise = stripInterrogativeAuxiliary(premise);
        if (premise.length() < MINIMUM_PREMISE_LENGTH) {
            return null;
        }
        return premise;
    }

    /** The place of the last reporting verb in a lower-cased question, or -1. */
    private static int lastReportingVerbAt(String lower) {
        int at = -1;
        for (String verb : REPORTING_VERBS) {
            int from = 0;
            while (true) {
                int found = lower.indexOf(verb, from);
                if (found < 0) {
                    break;
                }
                int end = found + verb.length();
                boolean whole = end >= lower.length()
                        || !Character.isLetter(lower.charAt(end));
                boolean starts = found == 0 || !Character.isLetter(lower.charAt(found - 1));
                if (whole && starts && found > at) {
                    at = found;
                }
                from = found + 1;
            }
        }
        return at;
    }

    /** The place of the attribution cue that governs a reporting verb, or -1. */
    private static int attributionCueAt(String lower, int verbAt) {
        int at = -1;
        for (String cue : ATTRIBUTION_CUES) {
            int found = lower.lastIndexOf(cue, verbAt);
            if (found > at) {
                at = found;
            }
        }
        return at;
    }

    /** Whether a clause carries an attribution cue. */
    private static boolean carriesAttributionCue(String clause) {
        for (String cue : ATTRIBUTION_CUES) {
            if (clause.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    /** A clause without the punctuation that ended it. */
    private static String stripTrailingPunctuation(String text) {
        String stripped = text.strip();
        while (!stripped.isEmpty()) {
            char last = stripped.charAt(stripped.length() - 1);
            if (last == ',' || last == ';' || last == ':') {
                stripped = stripped.substring(0, stripped.length() - 1).strip();
                continue;
            }
            break;
        }
        return stripped;
    }

    /**
     * A clause without the interrogative auxiliary that opened it.
     *
     * @param clause the clause
     * @return the clause as a statement
     */
    private static String stripInterrogativeAuxiliary(String clause) {
        String lower = clause.toLowerCase(Locale.ROOT);
        for (String auxiliary : INTERROGATIVE_AUXILIARIES) {
            if (lower.startsWith(auxiliary)) {
                return clause.substring(auxiliary.length()).strip();
            }
        }
        return clause;
    }

    /**
     * The proposition a POLAR question asks about, or null when the question is not polar.
     *
     * <p><b>Why only a polar question.</b> "Did the continents plow through the ocean floor?"
     * asserts a proposition and asks whether it holds, and that proposition is what the
     * workspace can confirm or deny. "What is a mid-ocean ridge?" asks for a description, and
     * the proposition behind it is not written anywhere in the question — recovering it would
     * mean parsing the question into a subject and a predicate, and this project does not build
     * a parser.
     *
     * <p><b>What is removed.</b> The interrogative auxiliary and the question mark. Nothing
     * else: the clause keeps the words the user wrote, so that the caller can quote it back.
     *
     * @param question the question
     * @return the proposition, or null
     */
    public static String assertionOf(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        for (String auxiliary : INTERROGATIVE_AUXILIARIES) {
            if (!lower.startsWith(auxiliary)) {
                continue;
            }
            String proposition = question.substring(auxiliary.length()).strip();
            while (!proposition.isEmpty()) {
                char last = proposition.charAt(proposition.length() - 1);
                if (last == '?' || last == '.' || last == '!') {
                    proposition = proposition.substring(0, proposition.length() - 1).strip();
                    continue;
                }
                break;
            }
            if (proposition.length() >= MINIMUM_PREMISE_LENGTH) {
                return proposition;
            }
            return null;
        }
        return null;
    }

    /**
     * The claim that refutes the premise, or null when none does.
     *
     * <p><b>The terms are compared in the form the index uses.</b>
     * The
     * premise and the claims are tokenised the way the retriever tokenises
     * them — folded, with the stopwords out — and a claim carries its
     * concept's name and definition alongside its proposition, because the
     * concept is what the claim is about. The first version compared RAW
     * words against RAW words, and it worked in English only by accident:
     * the bilingual lexicon is a table of FOLDED stems, so a Russian term
     * never reached it, and the premise of a Russian question was never
     * compared with the claim that denies it.
     *
     * @param premise the premise
     * @param snapshot the workspace
     * @return the refuting claim, or null
     */
    /**
     * The floor of the forward comparison of a REFUTED claim with the premise it may refute:
     * the claim's proposition has to carry this share of the premise's terms.
     */
    public static final double MINIMUM_SAME_ASSERTION_COVERAGE = 0.80;

    /**
     * The floor of the backward comparison: the claim's proposition may add this share of
     * terms the premise does not carry (a qualifier), and no more.
     */
    public static final double MINIMUM_SAME_ASSERTION_QUALIFIER_SHARE = 0.50;

    /**
     * The floor of the CROSS-LINGUAL comparison: the share of the premise's terms the
     * bilingual bridge has to carry when the premise shares NO word with the claim.
     *
     * <p><b>Why there is a separate floor.</b> A premise that shares words with the claim in
     * the same language states the claim only when it IS the claim — a partial overlap is a
     * shared subject, not a shared assertion. A premise that shares NO word with the claim
     * can only meet it through the bilingual lexicon, and the lexicon is the bridge between
     * two languages: such a premise is the claim written in another language, and the share
     * of the bridge's coverage is what says so. The measured cases sit either side of this
     * floor: the Russian premise of the misconception covers three of its five terms
     * (0.60) and the Russian premise of the second misconception four of its eight (0.50).
     */
    public static final double MINIMUM_CROSS_LINGUAL_SAME_ASSERTION_COVERAGE = 0.50;

    /**
     * The English terms that state the same thing.
     *
     * <p><b>Why a rule that compares words needs this.</b> Two statements state the same
     * assertion when they differ in their WORDS and not in their content: "the carbon dioxide
     * it takes in" and "the carbon dioxide it absorbs" say the same thing, and "the water it
     * absorbs" and "the carbon dioxide it absorbs" do not. A rule that compares the words of
     * the two statements cannot tell those apart: it sees "takes in" where the claim says
     * "absorbs", and nothing in the system knows that the two name one thing — the fold is a
     * fold, the bilingual lexicon is a table of TWO languages, and the corpus states both
     * words in different sentences. The table is where that knowledge lives: the same place
     * the polarity words live ({@link ClaimConflictDetector}), and for the same reason — a
     * word list is a property of the language and not of a corpus.</p>
     *
     * <p><b>Why the folded form.</b> The terms of a text are folded before they are compared
     * ("takes" folds to "tak", "absorbs" folds to "absorb"), so the table is keyed by the
     * folded form. Every pair is listed in both directions: the comparison asks whether one
     * side carries the other side's term, and it does not know which side is the premise.</p>
     */
    private static final Map<String, Set<String>> EQUIVALENT_TERMS = equivalentTerms(
            "tak", "absorb",
            "absorb", "tak",
            "use", "employ",
            "employ", "use",
            "need", "require",
            "require", "need",
            "help", "assist",
            "assist", "help");

    /**
     * The table of the equivalent terms, from the pairs.
     *
     * @param pairs the terms, each pair in both directions
     * @return the table
     */
    private static Map<String, Set<String>> equivalentTerms(String... pairs) {
        Map<String, Set<String>> table = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            table.computeIfAbsent(pairs[i], key -> new LinkedHashSet<>()).add(pairs[i + 1]);
        }
        return Map.copyOf(table);
    }

    public static Claim refutation(String premise, KnowledgeSnapshot snapshot) {
        return refutation(premise, snapshot, false);
    }

    /**
     * The claim the workspace HOLDS AS TRUE that denies a premise.
     *
     * <p><b>Why the claim the workspace calls false is not among them.</b> The photosynthesis
     * package holds the misconception "the oxygen a plant releases comes from the carbon
     * dioxide it absorbs" as a REFUTED claim, and its scope says it is "the misconception,
     * stated so that it can be refuted". A claim the workspace has already called false is the
     * STATEMENT of a falsehood; using it as the evidence that something else is false inverts
     * its meaning. The corpus's own correction of the misconception is the claim that carries
     * the truth, and that is the claim that refutes the premise.</p>
     *
     * @param premise the premise
     * @param snapshot the workspace
     * @return the refuting claim, or null
     */
    static Claim refutationHeldAsTrue(String premise, KnowledgeSnapshot snapshot) {
        return refutation(premise, snapshot, true);
    }

    /**
     * The claim the workspace HOLDS AS TRUE that denies a premise, read through the
     * retrieval's own index of the workspace's terms.
     *
     * <p><b>Why the index and not the workspace.</b> The scan used to read EVERY claim of the
     * workspace for every question: the proposition of each one was tokenised again, and the
     * bilingual lexicon was consulted for every pair of terms. The retrieval has already
     * tokenised every claim of the workspace — it does it to rank them — so the terms are at
     * hand, and a claim that shares no term with the premise cannot cover half of the
     * premise's terms and is not read at all. The verdict does not change: the claim that
     * could refute the premise is a claim that shares terms with it.</p>
     *
     * @param premise the premise
     * @param snapshot the workspace
     * @param retriever the retrieval that indexed the workspace's terms
     * @return the refuting claim, or null
     */
    static Claim refutationHeldAsTrue(String premise, KnowledgeSnapshot snapshot,
            KnowledgeRetriever retriever) {
        return refutation(premise, snapshot, true, retriever);
    }

    private static Claim refutation(String premise, KnowledgeSnapshot snapshot,
            boolean heldAsTrue) {
        return refutation(premise, snapshot, heldAsTrue, null);
    }

    private static Claim refutation(String premise, KnowledgeSnapshot snapshot,
            boolean heldAsTrue, KnowledgeRetriever retriever) {
        if (premise == null || snapshot == null) {
            return null;
        }
        Set<String> terms = termsOf(premise);
        if (terms.isEmpty()) {
            return null;
        }
        // THE LEXICON IS CONSULTED ONCE, NOT ONCE PER CLAIM.
        //
        // The premise's terms are translated into the terms a claim may carry INSTEAD of
        // being translated again for every claim of the workspace.
        Covering coverings = coveringsOf(terms);
        Map<String, Concept> conceptsById = new LinkedHashMap<>();
        for (Concept concept : snapshot.concepts()) {
            conceptsById.put(concept.id(), concept);
        }
        Map<String, Set<String>> indexed = retriever == null ? null : retriever.claimTerms();
        // THE TERMS THE BRIDGE CAN SPEAK ABOUT.
        //
        // A premise in another language names its subject in words the lexicon may not know
        // at all: the Russian "кислород растения происходит из углекислого газа" names three
        // words the lexicon knows and two it does not, and the two it does not know are
        // words NO claim of the workspace can carry. The coverage is measured over the terms
        // the bridge can reach, because a term that cannot be carried is not evidence against
        // the claim.
        Set<String> known = knownTerms(contentTerms(terms));
        // The scan reads the whole workspace: the refuting claim of a false premise is often
        // a claim the retrieval does not rank high (it states the misconception in the
        // corpus's own words), and a refutation that only looked at the retrieved claims
        // would miss it — the false premise would be answered as if it held.
        for (Claim claim : snapshot.claims()) {
            Set<String> indexedTerms = indexed == null ? null : indexed.get(claim.id());
            if (indexedTerms != null) {
                // THE COVERAGE IS COUNTED ON THE RETRIEVAL'S TERMS, NOT ON THE CLAIM'S OWN.
                //
                // The retrieval has already tokenised every claim of the workspace, and its
                // terms are the claim's terms with the concept's definition beside them: a
                // claim that does not cover the premise's terms there cannot cover them
                // either. The claim is read — and tokenised again — only when the index says
                // that it can bear on the premise.
                if (!bearsOnThePremise(terms, indexedTerms, coverings, known)) {
                    continue;
                }
            }
            // THE CLAIM'S OWN TERMS ARE READ WHEN THE CLAIM CAN BEAR ON THE PREMISE.
            //
            // The retrieval's index holds the claim's SEARCH terms — its proposition with the
            // concept's name and definition beside them — and the definition is the concept's
            // explanation, not the claim's assertion. The comparison is about the claim's
            // assertion, so the claim's own terms are read here.
            //
            // They are read HERE and not from the index because the index is built for EVERY
            // question, while a claim is read for the FEW that can bear on the premise: the
            // measurement of W53 round 5 says that a second form of every claim in the index
            // costs more than the re-reading it saves (the index build grew by 2.8 ms a
            // question on the research pool, and the asking path with it).
            Set<String> claimTerms = termsOf(claimText(claim, conceptsById));
            if (!bearsOnThePremise(terms, claimTerms, coverings, known)) {
                // The claim has to be about the premise: it has to carry at least half of the
                // premise's terms. The coverage is read through the lexicon, so a Russian
                // premise and the English claim about it meet.
                continue;
            }
            if (claim.status() == ClaimStatus.REFUTED && heldAsTrue) {
                // A CLAIM THE WORKSPACE CALLS FALSE DOES NOT REFUTE ANOTHER PROPOSITION.
                //
                // The photosynthesis package holds the misconception "the oxygen a plant
                // releases comes from the carbon dioxide it absorbs" as a REFUTED claim, and
                // its scope says it is "the misconception, stated so that it can be
                // refuted". A claim the workspace has already called false is the STATEMENT
                // of a falsehood; using it as the evidence that something else is false
                // inverts its meaning — the premise "plants release oxygen during the day"
                // is TRUE, and the misconception denies something else (where the oxygen
                // comes from).
                //
                // It DOES refute the proposition it states — and ONLY that proposition.
                //
                // THE SAME ASSERTION, NOT THE SAME SUBJECT. A claim the workspace calls false
                // is the workspace's own record that a PROPOSITION is false; it is the
                // evidence that a question asserting THAT proposition does not hold. It is
                // not the evidence that some other proposition about the same subject is
                // false: "plants release oxygen during the day" and "the oxygen a plant
                // releases comes from the carbon dioxide it absorbs" are about the same
                // subject and are not the same assertion, and the corpus does not deny the
                // first one anywhere.
                //
                // The comparison is bidirectional — the premise's terms are carried by the
                // claim's proposition AND the claim's proposition adds no more than a
                // qualifier — so a premise that is a different assertion about the same
                // subject is refused neither way.
                if (statesTheSameAssertion(claim, terms, claimTerms)) {
                    return claim;
                }
                continue;
            }
            if (claim.status() == ClaimStatus.REFUTED || claim.status() == ClaimStatus.CONTESTED) {
                return claim;
            }
            if (claim.status() == ClaimStatus.CONTESTED) {
                return claim;
            }
            // The claim denies what the premise asserts. The denial has to
            // be ABOUT the premise: see denies().
            if (heldAsTrue ? deniesWithItsScope(claim, terms, premise)
                    : denies(claim, terms, premise)) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Whether the claim denies what the premise asserts.
     *
     * <p><b>Why the negation has to be scoped.</b> The first version asked
     * only whether the claim carried a negation word, and that is not a
     * denial: "Photosynthetic organisms store the converted chemical energy
     * … the two processes are NOT alternatives in time" negates a
     * proposition about alternatives in time, and says nothing about
     * whether photosynthesis converts light energy into chemical energy.
     * The bare test refused a premise the workspace holds — and it did so
     * only after the terms were compared in the index form, because the
     * comparison then reached a claim it had not reached before.
     *
     * <p><b>The rule.</b> A denial is a NEGATED CLAUSE whose subject is the
     * premise's subject: the clause must cover at least the same share of
     * the premise's terms as the claim as a whole must. The negated clause
     * of the logic package's claim — "validity says nothing about whether
     * the premises of the argument are true" — covers the premise "every
     * valid argument has true premises"; the negated clause of the
     * photosynthesis claim — "the two processes are not alternatives in
     * time" — covers none of it.
     *
     * @param claim the claim
     * @param premiseTerms the premise's terms
     * @param premise the premise
     * @return whether the claim denies the premise
     */
    /**
     * Whether the claim denies the premise, the claim's scope read with its proposition.
     *
     * @param claim the claim
     * @param premiseTerms the premise's terms
     * @param premise the premise
     * @return whether it denies
     */
    static boolean deniesWithItsScope(Claim claim, Set<String> premiseTerms, String premise) {
        return denialOf(claim, premiseTerms, premise, true) != null;
    }

    private static boolean denies(Claim claim, Set<String> premiseTerms, String premise) {
        return denialOf(claim, premiseTerms, premise) != null;
    }

    /**
     * The clause of the claim that denies the premise, or null when none does.
     *
     * <p>It is the clause and not only the verdict, because a caller that reports the denial
     * has to say WHICH words deny the premise: a clause about a mechanism or about the
     * reception of a theory is not the same denial as a clause about the premise itself.
     *
     * @param claim the claim
     * @param premiseTerms the premise's terms
     * @param premise the premise
     * @return the denying clause, or null
     */
    static String denialOf(Claim claim, Set<String> premiseTerms, String premise) {
        return denialOf(claim, premiseTerms, premise, false);
    }

    /**
     * The clause of the claim that denies the premise.
     *
     * @param claim the claim
     * @param premiseTerms the premise's terms
     * @param premise the premise
     * @param includeScope whether the claim's SCOPE is read with its proposition
     * @return the denied clause, or null
     */
    static String denialOf(Claim claim, Set<String> premiseTerms, String premise,
            boolean includeScope) {
        if (negates(premise)) {
            return null;
        }
        for (String clause : clausesOf(claim.proposition())) {
            if (!negates(clause)) {
                continue;
            }
            if (denialLandsOn(clause, premiseTerms)) {
                return clause.strip();
            }
        }
        if (!includeScope) {
            return null;
        }
        // THE SCOPE IS PART OF THE CLAIM'S STATEMENT (see PremiseAssessment.read).
        for (String clause : clausesOf(claim.scope() == null ? "" : claim.scope())) {
            if (!negates(clause)) {
                continue;
            }
            if (denialLandsOn(clause, premiseTerms)) {
                return clause.strip();
            }
        }
        return null;
    }

    /**
     * Whether a denial lands on the premise.
     *
     * <p>The object of the denial has to carry a third of the premise's terms: a negation
     * that scopes over something else denies something else.</p>
     *
     * @param clause the denied clause
     * @param premiseTerms the premise's terms
     * @return whether the denial is about the premise
     */
    private static boolean denialLandsOn(String clause, Set<String> premiseTerms) {
        Set<String> deniedTerms = termsOf(PremiseAssessment.deniedObject(clause));
        if (deniedTerms.isEmpty()) {
            return false;
        }
        // The premise's terms are covered BY the denied object, and the coverage is read
        // through the lexicon: a Russian premise and an English denial carry the same words
        // in two languages, and the raw words do not meet.
        long covered = premiseTerms.stream()
                .filter(term -> covers(deniedTerms, term))
                .count();
        return (double) covered / deniedTerms.size()
                >= PremiseAssessment.MINIMUM_DENIAL_OBJECT_COVERAGE;
    }

    /**
     * The clauses of a proposition.
     *
     * <p>A clause is what a negation can be about, and a negation reaches
     * over the clause it is in and not over the sentence. The boundaries
     * are the ones English and Russian prose actually uses: the semicolon,
     * the comma, the adversative "but" and the full stop.
     *
     * @param proposition the proposition
     * @return its clauses
     */
    private static List<String> clausesOf(String proposition) {
        if (proposition == null || proposition.isBlank()) {
            return List.of();
        }
        return List.of(proposition.split("(?:;|,|\\.|\\bbut\\b)", -1));
    }

    /**
     * The terms of a text, folded the way the index folds them.
     *
     * @param text the text
     * @return its folded terms
     */
    private static Set<String> termsOf(String text) {
        return new LinkedHashSet<>(TextFeatures.tokens(text));
    }

    /**
     * The text a claim is indexed by: its proposition and the concept's own
     * NAME.
     *
     * <p>This is the retriever's index form, and the premise is compared
     * against the claim in the SAME form — the W42.9 lesson of the scope
     * match, where the question was folded in one language and the claim in
     * another and neither term ever met.
     *
     * <p><b>The definition is not here, and that was measured.</b> The
     * definition is the concept's own explanation, and it covers every
     * claim of the concept: adding it made the refuted claim of
     * "photosynthesis" reach a premise about light and chemical energy —
     * the concept is right, the claim is not, and the premise was refused
     * for a claim that says nothing about it. The concept's NAME is the
     * claim's subject and belongs in the comparison; the definition is a
     * search term and does not.
     *
     * @param claim the claim
     * @param conceptsById the workspace's concepts
     * @return the indexed text
     */
    private static String claimText(Claim claim, Map<String, Concept> conceptsById) {
        StringBuilder text = new StringBuilder(claim.proposition());
        Concept concept = conceptsById.get(claim.conceptId());
        if (concept != null) {
            text.append(' ').append(concept.name());
        }
        return text.toString();
    }

    /**
     * Whether a proposition carries a negation.
     *
     * <p>The English negations are the W41 list; the Russian ones come
     * from the language profile, so a Russian premise is checked the same
     * way an English one is.
     */
    public static boolean negates(String proposition) {
        if (proposition == null) {
            return false;
        }
        for (String word : TextFeatures.rawWords(proposition)) {
            String lower = word.toLowerCase(Locale.ROOT);
            switch (lower) {
                case "not", "no", "never", "cannot", "nothing", "neither", "nor",
                     "without" -> {
                    return true;
                }
                default -> {
                    // The Russian negation is a prefix: "не", "ни", "нет".
                    if (lower.startsWith("не") || lower.startsWith("ни") || lower.equals("нет")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether the claim carries the premise's term.
     *
     * <p>The premise is written in the language of the question and the
     * claim in the language of the claim, so the two are compared through
     * the bilingual lexicon: a Russian term is covered when the claim
     * carries its counterpart, and the English term the other way round.
     * Without this the premise of a Russian question never met the claim
     * that refutes it, and the question was answered under a premise the
     * workspace denies.
     *
     * <p>The term is FOLDED before the lookup, because the lexicon is a
     * table of folded stems: "углекислого" folds to "углекисл" and is
     * found there, while the raw word is not. The first version looked up
     * the raw word, and the lookup always missed.
     *
     * <p>A counterpart may be a PHRASE ("carbon dioxide"), and the claim's
     * terms are single words — so a phrase covers when every one of its
     * words is there.
     */
    private static boolean covers(Set<String> claimTerms, String premiseTerm) {
        if (claimTerms.contains(premiseTerm)) {
            return true;
        }
        for (String counterpart : BilingualLexicon.instance().translationsOfCaseForm(premiseTerm)) {
            if (coversCounterpart(claimTerms, counterpart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The terms a claim may carry INSTEAD of the premise's term.
     *
     * <p><b>Why the lexicon is consulted here and not in the comparison.</b> The lexicon is a
     * table, and a table is cheap to read ONCE: the premise's terms are translated into the
     * terms a claim may carry — the term itself and the lexicon's counterparts — and the
     * comparison that follows is a set test. The first version asked the lexicon for every
     * pair of a claim's terms and the premise's terms, once per claim of the workspace, and
     * the lexicon was the second half of the cost of a question.</p>
     *
     * <p><b>Why the phrases are kept apart.</b> A phrase covers only when EVERY one of its
     * words is there ("cellular respiration" covers when the claim holds both words), and a
     * set of words cannot say that. The phrases are therefore kept as they are and read
     * exactly as before.</p>
     *
     * @param premiseTerms the premise's terms
     * @return what covers each of the premise's terms
     */
    private static Covering coveringsOf(Set<String> premiseTerms) {
        Map<String, Set<String>> terms = new LinkedHashMap<>();
        Map<String, List<String>> phrases = new LinkedHashMap<>();
        for (String term : premiseTerms) {
            Set<String> covering = new LinkedHashSet<>();
            covering.add(term);
            List<String> phraseCounterparts = new ArrayList<>();
            for (String counterpart : BilingualLexicon.instance().translationsOfCaseForm(term)) {
                if (counterpart.contains(" ")) {
                    phraseCounterparts.add(counterpart);
                    for (String word : counterpart.split(" ")) {
                        covering.add(word);
                        covering.add(PremiseAssessment.stem(word));
                    }
                } else {
                    covering.add(counterpart);
                }
            }
            terms.put(term, covering);
            phrases.put(term, phraseCounterparts);
        }
        return new Covering(terms, phrases);
    }

    /**
     * What covers a premise's terms: the single terms, and the phrases that cover only when
     * every one of their words is there.
     *
     * @param terms the single terms that cover each of the premise's terms
     * @param phrases the phrases that cover each of the premise's terms
     */
    private record Covering(Map<String, Set<String>> terms, Map<String, List<String>> phrases) {

        /** Every term that can take part in the covering. */
        Set<String> allTerms() {
            Set<String> all = new LinkedHashSet<>();
            for (Set<String> covering : terms.values()) {
                all.addAll(covering);
            }
            return all;
        }
    }

    /**
     * Whether the claim carries the premise's term, read through the coverings.
     *
     * @param claimTerms the claim's terms
     * @param covering what covers the premise's terms
     * @param premiseTerm the premise's term
     * @return whether the claim carries it
     */
    private static boolean covers(Set<String> claimTerms, Covering covering,
            String premiseTerm) {
        Set<String> single = covering.terms().get(premiseTerm);
        if (single != null) {
            for (String term : single) {
                if (claimTerms.contains(term)) {
                    return true;
                }
            }
        }
        List<String> phrases = covering.phrases().get(premiseTerm);
        if (phrases != null) {
            for (String phrase : phrases) {
                if (coversCounterpart(claimTerms, phrase)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the claim carries the counterpart, a word or a phrase.
     *
     * @param claimTerms the claim's terms
     * @param counterpart the term's counterpart
     * @return whether the claim carries it
     */
    /**
     * Whether a claim can bear on the premise: it carries the premise's terms, in either
     * language.
     *
     * <p><b>Why the two readings.</b> A premise and a claim of the same language meet when
     * the claim carries the premise's terms. A premise in another language can only meet a
     * claim through the bilingual bridge, and the bridge's reach is measured over the terms
     * the lexicon knows: a word that the lexicon has never seen is a word no claim of the
     * workspace can carry, in any language, and counting it against the claim would make
     * every premise in another language unreachable.</p>
     *
     * @param premiseTerms the premise's terms
     * @param claimTerms   the claim's terms
     * @param coverings    what covers each of the premise's terms
     * @param known        the premise's terms the lexicon knows
     * @return whether the claim can bear on the premise
     */
    private static boolean bearsOnThePremise(Set<String> premiseTerms, Set<String> claimTerms,
            Covering coverings, Set<String> known) {
        long covered = premiseTerms.stream()
                .filter(term -> covers(claimTerms, coverings, term)).count();
        if ((double) covered / premiseTerms.size() >= MINIMUM_COVERAGE) {
            return true;
        }
        if (known.isEmpty()) {
            return false;
        }
        long bridged = known.stream().filter(term -> bridged(claimTerms, term)).count();
        return (double) bridged / known.size()
                >= MINIMUM_CROSS_LINGUAL_SAME_ASSERTION_COVERAGE;
    }

    /**
     * The terms the bilingual lexicon knows.
     *
     * @param terms the terms
     * @return the terms the lexicon can speak about
     */
    private static Set<String> knownTerms(Set<String> terms) {
        Set<String> known = new LinkedHashSet<>();
        for (String term : terms) {
            if (!BilingualLexicon.instance().translationsOfCaseForm(term).isEmpty()) {
                known.add(term);
            }
        }
        return known;
    }

    /**
     * Whether a claim states the same thing as the premise.
     *
     * @param claimTerms the claim's terms
     * @param premiseTerms the premise's terms
     * @return whether the claim carries every term of the premise
     */
    /**
     * Whether a claim states the SAME ASSERTION as the premise.
     *
     * <p>The test is bidirectional: the premise's terms have to be carried by the claim's
     * proposition (the claim says everything the premise says) and the claim's proposition
     * must not add a DIFFERENT predicate (it may add a qualifier). A premise that is only
     * about the same subject is a different assertion, and the workspace's record that the
     * claim is false is no evidence about it.</p>
     *
     * <p><b>The two languages are read separately.</b> A premise that shares words with the
     * claim in the SAME language states the claim only when it IS the claim: "plants release
     * oxygen during the day" shares three words with "The oxygen a plant releases comes from
     * the carbon dioxide it absorbs" and states a different proposition about the same
     * subject. A premise that shares NO word with the claim can only meet it through the
     * bilingual lexicon, and the lexicon is the bridge between the two languages — such a
     * premise is the claim written in another language, and the bridge's coverage says how
     * much of it the premise carries.</p>
     *
     * @param claim the claim the workspace holds as REFUTED
     * @param premiseTerms the premise's terms
     * @return whether the two state the same thing
     */
    private static boolean statesTheSameAssertion(Claim claim, Set<String> premiseTerms,
            Set<String> claimTerms) {
        if (claimTerms == null || claimTerms.isEmpty() || premiseTerms.isEmpty()) {
            return false;
        }
        Set<String> premiseContent = contentTerms(premiseTerms);
        Set<String> claimContent = contentTerms(claim, claimTerms);
        // THE SAME WORDS: the premise's terms are the claim's terms.
        long shared = premiseTerms.stream().filter(term -> claimTerms.contains(term)).count();
        if ((double) shared / premiseTerms.size() >= MINIMUM_SAME_ASSERTION_COVERAGE) {
            // THE DISTINGUISHING TERMS MUST MATCH.
            //
            // The premise may say LESS than the claim — a premise is often a truncation of a
            // longer statement, and the claim adds the qualifier — but it may not say
            // something DIFFERENT. Every content term of the premise that the claim does not
            // carry has to be a term the claim carries under another name, and the claim may
            // add no more than a qualifier of its own.
            //
            // This is the rule the overlap could not be. "The oxygen a plant releases comes
            // from the water it absorbs" and "The oxygen a plant releases comes from the
            // carbon dioxide it absorbs" share five of the premise's six terms, and the
            // workspace holds the second as FALSE — so the overlap called the first the same
            // assertion and refused a premise the corpus states. The terms that tell them
            // apart are the ones the two do NOT share, and the premise's "water" is one of
            // them.
            for (String term : premiseContent) {
                if (!carried(claimContent, term)) {
                    return false;
                }
            }
            long back = claimContent.stream().filter(term -> carried(premiseContent, term)).count();
            return (double) back / claimContent.size() >= MINIMUM_SAME_ASSERTION_QUALIFIER_SHARE;
        }
        // THE SAME WORDS IN ANOTHER LANGUAGE.
        //
        // The premise's words are not the claim's words, so the two can only meet through the
        // bilingual bridge — and the bridge is what says that they say the same thing. A
        // premise that meets the claim only in its own language has no such evidence: the
        // words it shares are the words of the SUBJECT ("plants release oxygen during the
        // day" against "The oxygen a plant releases comes from the carbon dioxide it
        // absorbs"), and a shared subject is not a shared assertion.
        //
        // THE COVERAGE IS MEASURED OVER THE TERMS THE LEXICON KNOWS. A term the lexicon does
        // not know cannot be bridged, and counting it against the premise would make every
        // Russian premise unreachable: the Russian fold cuts the case endings of a word in a
        // way the lexicon's own keys do not always carry ("растения" against "растение"), and
        // a premise that names a word the lexicon has never seen would never reach the floor
        // however much of it the claim does carry.
        Set<String> known = knownTerms(premiseContent);
        if (known.isEmpty()) {
            return false;
        }
        long bridged = known.stream().filter(term -> bridged(claimContent, term)).count();
        if ((double) bridged / known.size() < MINIMUM_CROSS_LINGUAL_SAME_ASSERTION_COVERAGE) {
            return false;
        }
        // THE CLAIM'S SIDE IS READ WITH THE SAME SHARE AS THE SAME-LANGUAGE RULE.
        //
        // The claim may add a qualifier, and how much it may add is the rule the
        // same-language comparison uses. Here the claim's own content terms are counted
        // through the bridge: the claim's "carbon" and "dioxide" are carried by the
        // premise's "углекисл", and its "oxygen" by the premise's "кислород".
        long carried = bridgedClaimTerms(premiseContent, claimContent);
        return (double) carried / claimContent.size() >= MINIMUM_SAME_ASSERTION_QUALIFIER_SHARE;
    }

    /**
     * The content terms of a set of terms: the frame held apart.
     *
     * <p>The scope terms, the symbols and the polarity words are the frame of a statement and
     * not its content, and the conflict detector already holds them apart: two accounts that
     * share only their frame are not about the same thing. The premise has no scope, and the
     * claim's scope is read from the claim.</p>
     *
     * @param terms the terms
     * @return the content terms
     */
    private static Set<String> contentTerms(Set<String> terms) {
        Set<String> content = new LinkedHashSet<>(terms);
        content.removeAll(ClaimConflictDetector.polarityWords());
        content.removeIf(term -> term.length() == 1);
        return content;
    }

    /**
     * The content terms of a claim: its own terms without the conditions it holds under.
     *
     * @param claim the claim
     * @param claimTerms the claim's terms
     * @return the content terms
     */
    private static Set<String> contentTerms(Claim claim, Set<String> claimTerms) {
        Set<String> content = contentTerms(claimTerms);
        String scope = claim.scope();
        if (scope != null && !scope.isBlank()) {
            content.removeAll(termsOf(scope));
        }
        return content;
    }

    /**
     * Whether a set of terms carries a term, directly or under another name.
     *
     * @param terms the terms
     * @param term  the term
     * @return whether they carry it
     */
    private static boolean carried(Set<String> terms, String term) {
        if (terms.contains(term)) {
            return true;
        }
        Set<String> equivalents = EQUIVALENT_TERMS.get(term);
        if (equivalents == null) {
            return false;
        }
        for (String equivalent : equivalents) {
            if (terms.contains(equivalent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The number of the claim's content terms the premise carries THROUGH THE BRIDGE.
     *
     * @param premiseContent the premise's content terms
     * @param claimContent   the claim's content terms
     * @return the number of terms the bridge carries
     */
    private static long bridgedClaimTerms(Set<String> premiseContent, Set<String> claimContent) {
        long carried = 0;
        for (String claimTerm : claimContent) {
            for (String premiseTerm : premiseContent) {
                if (bridgedCarries(claimContent, claimTerm, premiseTerm)) {
                    carried++;
                    break;
                }
            }
        }
        return carried;
    }
    /**
     * Whether the counterpart of a premise's term carries a claim's term.
     *
     * <p>A counterpart that is a PHRASE carries the claim's term when the claim holds every
     * word of the phrase: the lexicon's "carbon dioxide" covers the claim's "carbon" only
     * when the claim also holds its "dioxide".</p>
     *
     * @param claimContent the claim's content terms
     * @param claimTerm    the claim's term
     * @param premiseTerm  the premise's term
     * @return whether the bridge carries it
     */
    private static boolean bridgedCarries(Set<String> claimContent, String claimTerm,
            String premiseTerm) {
        for (String counterpart : BilingualLexicon.instance().translationsOfCaseForm(premiseTerm)) {
            if (!counterpart.contains(" ")) {
                if (counterpart.equals(claimTerm)) {
                    return true;
                }
                continue;
            }
            // A phrase carries the claim's term when the claim holds EVERY word of the
            // phrase — and a word is held either as it is written or as it folds, which is
            // the test the coverage of a phrase uses everywhere else.
            String[] words = counterpart.split(" ");
            boolean written = false;
            for (String word : words) {
                if (word.equals(claimTerm) || PremiseAssessment.stem(word).equals(claimTerm)) {
                    written = true;
                    break;
                }
            }
            if (!written) {
                continue;
            }
            boolean whole = true;
            for (String word : words) {
                if (!claimContent.contains(word)
                        && !claimContent.contains(PremiseAssessment.stem(word))) {
                    whole = false;
                    break;
                }
            }
            if (whole) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the claim carries the premise's term THROUGH THE BILINGUAL BRIDGE.
     *
     * <p>This is {@link #covers} without the direct test: the claim does NOT hold the term
     * itself, it holds its counterpart in the other language. The distinction is the whole
     * point — the direct test says that the two texts share a word, and the bridge says that
     * they name the same thing in two languages.</p>
     *
     * @param claimTerms the claim's terms
     * @param premiseTerm the premise's term
     * @return whether the bridge carries it
     */
    private static boolean bridged(Set<String> claimTerms, String premiseTerm) {
        for (String counterpart : BilingualLexicon.instance().translationsOfCaseForm(premiseTerm)) {
            if (coversCounterpart(claimTerms, counterpart)) {
                return true;
            }
        }
        return false;
    }

    private static boolean coversCounterpart(Set<String> claimTerms, String counterpart) {
        if (!counterpart.contains(" ")) {
            return claimTerms.contains(counterpart);
        }
        for (String word : counterpart.split(" ")) {
            // The claim's terms are folded words and the counterpart is written out, so the
            // word is folded before it is looked for.
            if (!claimTerms.contains(word) && !claimTerms.contains(PremiseAssessment.stem(word))) {
                return false;
            }
        }
        return true;
    }
}
