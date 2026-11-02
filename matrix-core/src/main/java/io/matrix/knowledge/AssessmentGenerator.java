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
 * The assessment generator (RECON-W42.7).
 *
 * <p>The mission's practical goal says the system must "practise applying
 * the concept" — and the concept it acquires from a document had NO
 * exercises. The assessment dataset came from the knowledge package, so a
 * concept the workspace acquired had nothing to practise with: the
 * journey stopped after "explain it".
 *
 * <p><b>What the generator does.</b> For every concept the workspace holds
 * claims about, and that the dataset does not already cover, it writes a
 * TRAINING item and a HELD-OUT item per dimension the claims can support.
 * The held-out item asks the same thing in different words with the same
 * key terms, which is the discipline the packages' own datasets use: the
 * learner cannot pass the independent test by remembering the wording of
 * the practice.
 *
 * <p><b>What the generator refuses to do.</b> It does not invent content.
 * Every key term comes from the workspace's own claims or from the
 * excerpts of the sources behind them, and the prompts are templates that
 * name the concept. An item whose key terms cannot be taken from the
 * workspace is not written, and the reason is reported.
 *
 * <p><b>Determinism.</b> The generation depends on nothing but the
 * workspace's own content: the same claims produce the same items, with
 * the same ids, in the same order. CONSTITUTION Article I asks for
 * exactly that of anything that participates in a decision.
 */
public final class AssessmentGenerator {

    /** How many key terms an item carries. */
    public static final int KEY_TERMS = 6;

    /** What the generation did. */
    public record Result(
            List<AssessmentItem> items,
            int concepts,
            int conceptsSkipped,
            List<String> skipped) {

        public Result {
            items = List.copyOf(items == null ? List.of() : items);
            skipped = List.copyOf(skipped == null ? List.of() : skipped);
        }

        /** Whether the generation wrote anything. */
        public boolean wroteAnything() {
            return !items.isEmpty();
        }
    }

    private AssessmentGenerator() {
    }

    /**
     * Generates the assessment items of the concepts the dataset does not
     * cover.
     *
     * @param snapshot the workspace
     * @param covered the concept ids the dataset already covers
     * @return the items that were written
     */
    public static Result generate(KnowledgeSnapshot snapshot, Set<String> covered) {
        List<AssessmentItem> items = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        Map<String, List<Claim>> claimsByConcept = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            if (claim.status() == ClaimStatus.REFUTED
                    || claim.status() == ClaimStatus.SUPERSEDED) {
                // A refuted claim is not something to be practised; it is
                // something to be corrected.
                continue;
            }
            claimsByConcept.computeIfAbsent(claim.conceptId(), key -> new ArrayList<>()).add(claim);
        }
        Map<String, Source> sourcesById = new LinkedHashMap<>();
        for (Source source : snapshot.sources()) {
            sourcesById.put(source.id(), source);
        }

        int concepts = 0;
        int conceptsSkipped = 0;
        for (Concept concept : snapshot.concepts()) {
            if (covered.contains(concept.id())) {
                continue;
            }
            List<Claim> claims = claimsByConcept.getOrDefault(concept.id(), List.of());
            if (claims.isEmpty()) {
                conceptsSkipped++;
                skipped.add("the concept \"" + concept.name() + "\" has no usable claim to "
                        + "practise");
                continue;
            }
            concepts++;

            List<String> keyTerms = keyTermsOf(concept, claims);
            if (keyTerms.isEmpty()) {
                conceptsSkipped++;
                skipped.add("the claims of the concept \"" + concept.name() + "\" hold no "
                        + "content term to check an answer against");
                continue;
            }
            String name = concept.name();

            // RECALL - what the workspace holds.
            pair(items, concept, LearningDimension.RECALL,
                    "What does the workspace hold about " + name + "?",
                    "State what the workspace holds about " + name + ", in your own words.",
                    keyTerms, 1);
            // EXPLANATION - the same, asked for as an explanation.
            pair(items, concept, LearningDimension.EXPLANATION,
                    "Explain " + name + " using the workspace's own knowledge.",
                    "Describe what " + name + " is and what it does, without reusing the "
                            + "wording of the practice question.",
                    keyTerms, 2);
            // CAUSAL - only when a claim states a cause.
            if (hasCausalClaim(claims)) {
                pair(items, concept, LearningDimension.CAUSAL,
                        "What leads to " + name + ", according to the workspace?",
                        "Give the reason the workspace gives for " + name + ", in different "
                                + "words.",
                        keyTerms, 2);
            }
            // APPLICATION - the learner has to use the fact, not repeat it.
            pair(items, concept, LearningDimension.APPLICATION,
                    "You are asked about " + name + " in a situation you have not seen before. "
                            + "Using what the workspace holds about it, what would you say?",
                    "Someone asks you to use the workspace's knowledge of " + name + " in a new "
                            + "situation. What do you tell them?",
                    keyTerms, 3);
            // GROUNDING - the evidence behind the claim.
            List<String> evidenceTerms = evidenceTerms(claims, sourcesById);
            if (!evidenceTerms.isEmpty()) {
                pair(items, concept, LearningDimension.GROUNDING,
                        "What evidence does the workspace hold for its claims about " + name + "?",
                        "Name what the workspace cites as the evidence for " + name + ".",
                        evidenceTerms, 2);
            }
        }
        return new Result(items, concepts, conceptsSkipped, skipped);
    }

    /**
     * Writes a training item and its held-out twin.
     *
     * <p>The twin asks the same thing in different words with the same key
     * terms. That is the discipline the packages' own datasets use: the
     * learner cannot pass the independent test by remembering the wording
     * of the practice.
     *
     * @param items the items to add to
     * @param concept the concept
     * @param dimension the dimension
     * @param trainingPrompt the practice question
     * @param heldOutPrompt the independent question
     * @param keyTerms the terms an answer must hold
     * @param difficulty the difficulty
     */
    private static void pair(List<AssessmentItem> items, Concept concept,
                             LearningDimension dimension, String trainingPrompt,
                             String heldOutPrompt, List<String> keyTerms, int difficulty) {
        String base = concept.id() + "|" + dimension.name();
        items.add(new AssessmentItem(
                "gen-" + Ids.digest(base + "|training"),
                concept.id(), dimension, trainingPrompt, keyTerms, difficulty,
                "training", false, false, List.of(), null));
        items.add(new AssessmentItem(
                "gen-" + Ids.digest(base + "|held-out"),
                concept.id(), dimension, heldOutPrompt, keyTerms, difficulty,
                "held-out", true, true, List.of(), null));
    }

    /**
     * The key terms of a concept.
     *
     * <p>The concept's OWN NAME comes first: the name is what identifies
     * the concept, and the terms of the claims come after it. The first
     * version sorted the claim terms by their frequency and cut the list
     * at six - and for a concept with a single claim every term has the
     * same frequency, so the alphabet decided, and the most distinctive
     * term of all ("thylakoid") fell off the end.
     *
     * @param concept the concept
     * @param claims its claims
     * @return the key terms
     */
    private static List<String> keyTermsOf(Concept concept, List<Claim> claims) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Claim claim : claims) {
            for (String term : TextFeatures.tokens(claim.proposition())) {
                counts.merge(term, 1, Integer::sum);
            }
        }
        List<String> terms = new ArrayList<>();
        for (String term : TextFeatures.tokens(concept.name())) {
            if (!terms.contains(term)) {
                terms.add(term);
            }
        }
        List<String> rest = new ArrayList<>(counts.keySet());
        rest.sort(Comparator
                .comparingInt((String term) -> counts.get(term)).reversed()
                .thenComparing(term -> term));
        for (String term : rest) {
            if (!terms.contains(term)) {
                terms.add(term);
            }
        }
        return terms.size() > KEY_TERMS ? terms.subList(0, KEY_TERMS) : terms;
    }

    /**
     * The key terms of the evidence: the content terms of the excerpts of
     * the sources behind the claims.
     *
     * @param claims the claims
     * @param sourcesById the sources
     * @return the terms
     */
    private static List<String> evidenceTerms(List<Claim> claims, Map<String, Source> sourcesById) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Claim claim : claims) {
            String introducedBy = claim.introducedBySourceId();
            Source source = introducedBy == null ? null : sourcesById.get(introducedBy);
            if (source == null || source.excerpt() == null || source.excerpt().isBlank()) {
                continue;
            }
            for (String term : TextFeatures.tokens(source.excerpt())) {
                counts.merge(term, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>(counts.keySet());
        terms.sort(Comparator
                .comparingInt((String term) -> counts.get(term)).reversed()
                .thenComparing(term -> term));
        return terms.size() > KEY_TERMS ? terms.subList(0, KEY_TERMS) : terms;
    }

    /**
     * Whether any of the claims states a cause.
     *
     * @param claims the claims
     * @return whether a cause is stated
     */
    private static boolean hasCausalClaim(List<Claim> claims) {
        for (Claim claim : claims) {
            String text = claim.proposition().toLowerCase(Locale.ROOT);
            if (text.contains(" because ") || text.contains(" leads to ") || text.contains(" causes ")
                    || text.contains(" so that ") || text.contains(" therefore ")
                    || text.contains(" enables ") || text.contains(" allows ")) {
                return true;
            }
        }
        return false;
    }
}
