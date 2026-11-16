package io.matrix.knowledge;

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

    private static Claim refutation(String premise, KnowledgeSnapshot snapshot,
            boolean heldAsTrue) {
        if (premise == null || snapshot == null) {
            return null;
        }
        Set<String> terms = termsOf(premise);
        if (terms.isEmpty()) {
            return null;
        }
        Map<String, Concept> conceptsById = new LinkedHashMap<>();
        for (Concept concept : snapshot.concepts()) {
            conceptsById.put(concept.id(), concept);
        }
        // The scan reads the whole workspace: the refuting claim of a false premise is often
        // a claim the retrieval does not rank high (it states the misconception in the
        // corpus's own words), and a refutation that only looked at the retrieved claims
        // would miss it — the false premise would be answered as if it held.
        for (Claim claim : snapshot.claims()) {
            Set<String> claimTerms = termsOf(claimText(claim, conceptsById));
            long covered = terms.stream().filter(term -> covers(claimTerms, term)).count();
            double coverage = (double) covered / terms.size();
            if (coverage < MINIMUM_COVERAGE) {
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
                // It DOES refute the proposition it states: when the question repeats the
                // misconception, the workspace's own record that the misconception is false
                // is the evidence that the question's premise does not hold. The workspace's
                // correction of the misconception carries the truth in the other case.
                if (statesTheSame(claimTerms, terms)) {
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
        for (String counterpart : BilingualLexicon.instance().translations(premiseTerm)) {
            if (coversCounterpart(claimTerms, counterpart)) {
                return true;
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
     * Whether a claim states the same thing as the premise.
     *
     * @param claimTerms the claim's terms
     * @param premiseTerms the premise's terms
     * @return whether the claim carries every term of the premise
     */
    private static boolean statesTheSame(Set<String> claimTerms, Set<String> premiseTerms) {
        if (claimTerms.isEmpty() || premiseTerms.isEmpty()) {
            return false;
        }
        long covered = premiseTerms.stream().filter(term -> covers(claimTerms, term)).count();
        // The floor is the same one the denial rules use: a cross-lingual premise and the
        // claim it repeats share three of five words through the lexicon, and the workspace's
        // own record that the claim is false is the evidence that the premise does not hold.
        return (double) covered / premiseTerms.size()
                >= PremiseAssessment.MINIMUM_DENIAL_OBJECT_COVERAGE;
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
