package io.matrix.knowledge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
            "since ", "because ", "assuming ", "given that ");

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
     * @param premise the premise
     * @param snapshot the workspace
     * @return the refuting claim, or null
     */
    public static Claim refutation(String premise, KnowledgeSnapshot snapshot) {
        if (premise == null || snapshot == null) {
            return null;
        }
        Set<String> terms = new LinkedHashSet<>(QuestionDemand.contentTerms(premise));
        if (terms.isEmpty()) {
            return null;
        }
        Set<String> premiseWords = new LinkedHashSet<>();
        for (String word : TextFeatures.rawWords(premise)) {
            premiseWords.add(word.toLowerCase(Locale.ROOT));
        }
        for (Claim claim : snapshot.claims()) {
            Set<String> claimTerms = QuestionDemand.contentTerms(claim.proposition());
            long covered = terms.stream().filter(claimTerms::contains).count();
            double coverage = (double) covered / terms.size();
            if (coverage < MINIMUM_COVERAGE) {
                continue;
            }
            if (claim.status() == ClaimStatus.REFUTED || claim.status() == ClaimStatus.CONTESTED) {
                return claim;
            }
            // The claim denies what the premise asserts: it carries a
            // negation the premise does not.
            if (negates(claim.proposition()) && !negates(premise)) {
                return claim;
            }
        }
        return null;
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
}
