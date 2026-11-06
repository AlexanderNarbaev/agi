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
     * The claim that refutes the premise, or null when none does.
     *
     * <p><b>The terms are compared in the form the index uses.</b> The
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
        for (Claim claim : snapshot.claims()) {
            Set<String> claimTerms = termsOf(claimText(claim, conceptsById));
            long covered = terms.stream().filter(term -> covers(claimTerms, term)).count();
            double coverage = (double) covered / terms.size();
            if (coverage < MINIMUM_COVERAGE) {
                continue;
            }
            if (claim.status() == ClaimStatus.REFUTED || claim.status() == ClaimStatus.CONTESTED) {
                return claim;
            }
            // The claim denies what the premise asserts. The denial has to
            // be ABOUT the premise: see denies().
            if (denies(claim, terms, premise)) {
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
    private static boolean denies(Claim claim, Set<String> premiseTerms, String premise) {
        if (negates(premise)) {
            return false;
        }
        for (String clause : clausesOf(claim.proposition())) {
            if (!negates(clause)) {
                continue;
            }
            Set<String> clauseTerms = termsOf(clause);
            if (clauseTerms.isEmpty()) {
                continue;
            }
            long covered = premiseTerms.stream()
                    .filter(term -> covers(clauseTerms, term)).count();
            if ((double) covered / premiseTerms.size() >= MINIMUM_COVERAGE) {
                return true;
            }
        }
        return false;
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
    private static boolean coversCounterpart(Set<String> claimTerms, String counterpart) {
        if (!counterpart.contains(" ")) {
            return claimTerms.contains(counterpart);
        }
        for (String word : counterpart.split(" ")) {
            if (!claimTerms.contains(word)) {
                return false;
            }
        }
        return true;
    }
}
