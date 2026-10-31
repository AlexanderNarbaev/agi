package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answers a question from the claims of a workspace (owner directive §19
 * Workflow D, §21, §27).
 *
 * <p>The retrieval is lexical and IDF-weighted: each claim is a document,
 * each claim's proposition is augmented with the name and definition of its
 * concept, and a question is scored by the share of its informative terms
 * that the claim covers. There is no embedding model and no external call,
 * so the behaviour is deterministic and reproducible — and, unlike a
 * similarity score, it can be explained: the answer names the claims it used
 * and the sources behind them.
 *
 * <p>The answer is assembled at the requested ABSTRACTION LEVEL. A question
 * asked at INTRODUCTORY is answered with the introductory representation and
 * its own scope; the same question at MECHANISTIC is answered with the
 * mechanistic one. Neither erases the other, and when the workspace has no
 * representation at the level that was asked for, the answer says which
 * level it used instead.
 */
public final class KnowledgeRetriever {

    /** The least share of the question's informative terms a claim must cover. */
    public static final double DEFAULT_MINIMUM_COVERAGE = 0.25;

    private final KnowledgeSnapshot snapshot;
    private final double minimumCoverage;

    private final Map<String, Concept> conceptsById = new LinkedHashMap<>();
    private final Map<String, Claim> claimsById = new LinkedHashMap<>();
    private final Map<String, List<Representation>> representationsByConcept = new LinkedHashMap<>();
    private final Map<String, Set<String>> claimTerms = new LinkedHashMap<>();
    private final Map<String, Double> idf;

    public KnowledgeRetriever(KnowledgeSnapshot snapshot) {
        this(snapshot, DEFAULT_MINIMUM_COVERAGE);
    }

    public KnowledgeRetriever(KnowledgeSnapshot snapshot, double minimumCoverage) {
        this.snapshot = snapshot;
        this.minimumCoverage = minimumCoverage;
        for (Concept concept : snapshot.concepts()) {
            conceptsById.put(concept.id(), concept);
        }
        for (Claim claim : snapshot.claims()) {
            claimsById.put(claim.id(), claim);
            Concept concept = conceptsById.get(claim.conceptId());
            StringBuilder text = new StringBuilder(claim.proposition());
            if (concept != null) {
                text.append(' ').append(concept.name());
                if (concept.definition() != null) {
                    text.append(' ').append(concept.definition());
                }
            }
            claimTerms.put(claim.id(), new LinkedHashSet<>(TextFeatures.tokens(text.toString())));
        }
        for (Representation representation : snapshot.representations()) {
            representationsByConcept
                    .computeIfAbsent(representation.conceptId(), k -> new ArrayList<>())
                    .add(representation);
        }
        this.idf = TextFeatures.idf(new ArrayList<>(claimTerms.values()));
    }

    /** Answer a question at the introductory level by default. */
    public Answer answer(String question) {
        return answer(question, AbstractionLevel.INTRODUCTORY);
    }

    /** Answer a question at a requested abstraction level. */
    public Answer answer(String question, AbstractionLevel level) {
        AbstractionLevel wanted = level == null ? AbstractionLevel.INTRODUCTORY : level;
        List<String> queryTerms = TextFeatures.tokens(question);
        if (queryTerms.isEmpty()) {
            return new Answer(question, wanted, false,
                    "The question contains no searchable terms.",
                    "", 0.0, List.of(), List.of(), List.of(), List.of(),
                    List.of(), TextFeatures.tokens(question));
        }

        Map<String, Double> queryWeights = new HashMap<>();
        double totalWeight = 0.0;
        for (String term : queryTerms) {
            double weight = idf.getOrDefault(term, maxIdf());
            queryWeights.merge(term, weight, Double::sum);
            totalWeight += weight;
        }

        // The uncovered terms are reported in the words the user wrote, not
        // in the folded stems the matcher works with.
        Set<String> unmatched = new LinkedHashSet<>(TextFeatures.originalTokens(question));
        Set<String> unmatchedStems = new LinkedHashSet<>(queryTerms);
        List<ScoredClaim> scored = new ArrayList<>();
        for (Claim claim : snapshot.claims()) {
            Set<String> terms = claimTerms.get(claim.id());
            double matched = 0.0;
            for (String term : queryTerms) {
                if (terms.contains(term)) {
                    matched += queryWeights.get(term);
                    unmatchedStems.remove(term);
                }
            }
            if (matched > 0.0) {
                scored.add(new ScoredClaim(claim, matched / totalWeight));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredClaim::coverage).reversed()
                .thenComparing(s -> s.claim().id()));

        List<ScoredClaim> relevant = scored.stream()
                .filter(s -> s.coverage() >= minimumCoverage)
                .toList();

        if (relevant.isEmpty()) {
            return new Answer(question, wanted, false,
                    "No claim in this workspace covers enough of the question to answer it.",
                    "", 0.0, List.of(), List.of(), List.of(),
                    List.of("the workspace holds " + snapshot.claims().size() + " claims about "
                            + snapshot.concepts().size() + " concepts"),
                    List.copyOf(unmatched), List.copyOf(unmatched));
        }

        // The concepts behind the relevant claims, best first. A concept whose
        // NAME is what the question asked about outranks one that merely
        // mentions the term: "What is photosynthesis?" is about the concept
        // photosynthesis, not about the concept that happens to share a word
        // with it.
        Map<String, Double> conceptScores = new LinkedHashMap<>();
        for (ScoredClaim match : relevant) {
            conceptScores.merge(match.claim().conceptId(), match.coverage(), Math::max);
        }
        List<Concept> concepts = conceptScores.entrySet().stream()
                .sorted((left, right) -> {
                    int byName = Double.compare(nameCoverage(right.getKey(), queryTerms),
                            nameCoverage(left.getKey(), queryTerms));
                    if (byName != 0) {
                        return byName;
                    }
                    return Double.compare(right.getValue(), left.getValue());
                })
                .map(e -> conceptsById.get(e.getKey()))
                .filter(c -> c != null)
                .toList();

        List<Claim> claims = relevant.stream().map(ScoredClaim::claim).toList();

        // The sources behind the claims: the evidence links, not the claim's
        // own word.
        Set<String> sourceIds = new LinkedHashSet<>();
        for (Evidence evidence : snapshot.evidence()) {
            if (claims.stream().anyMatch(c -> c.id().equals(evidence.claimId()))) {
                sourceIds.add(evidence.sourceId());
            }
        }
        List<Source> sources = snapshot.sources().stream()
                .filter(s -> sourceIds.contains(s.id()))
                .toList();

        // The statement at the requested level.
        List<String> statements = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        AbstractionLevel used = wanted;
        boolean primarySeen = false;
        for (Concept concept : concepts) {
            List<Representation> available = representationsByConcept.getOrDefault(concept.id(), List.of());
            if (available.isEmpty()) {
                continue;
            }
            Representation chosen = Representations.forLevel(available, wanted);
            if (chosen != null) {
                statements.add(chosen.statement());
                limitations.addAll(chosen.validityConditions());
                // The level the answer was rendered at is the level of the
                // PRIMARY concept's account. A secondary concept that has no
                // account at the requested level must not redefine it.
                if (!primarySeen) {
                    primarySeen = true;
                    used = chosen.level();
                }
                if (chosen.level() != wanted) {
                    limitations.add("This workspace has no " + wanted.name().toLowerCase(java.util.Locale.ROOT)
                            + " account of " + concept.name() + "; the answer uses the "
                            + chosen.level().name().toLowerCase(java.util.Locale.ROOT) + " one.");
                }
            }
        }
        if (statements.isEmpty()) {
            // A concept may be known only through its claims, with no
            // representation authored yet. Say so rather than inventing one.
            statements.add(claims.get(0).proposition());
            limitations.add("This concept has claims but no authored representation yet, "
                    + "so the answer is the claim itself.");
        }

        double claimConfidence = claims.stream().mapToDouble(Claim::confidence).average().orElse(0.0);
        double coverage = relevant.stream().mapToDouble(ScoredClaim::coverage).average().orElse(0.0);
        double confidence = Math.round(claimConfidence * coverage * 100.0) / 100.0;

        for (Claim claim : claims) {
            if (claim.status() == ClaimStatus.CONTESTED) {
                limitations.add("The sources disagree about: " + claim.proposition());
            } else if (claim.status() == ClaimStatus.SUPPORTED) {
                limitations.add("Supported but not independently verified: " + claim.proposition());
            }
        }
        if (!unmatchedStems.isEmpty()) {
            limitations.add("Not covered by the workspace: " + String.join(", ", unmatched));
        }

        return new Answer(question, used, true, null,
                String.join("\n\n", statements), confidence, concepts, claims, sources,
                limitations, List.copyOf(unmatched), List.copyOf(unmatched));
    }

    private double maxIdf() {
        double max = 1.0;
        for (double weight : idf.values()) {
            max = Math.max(max, weight);
        }
        return max;
    }

    /**
     * The share of a concept's NAME that the question mentions. A question of
     * "What is photosynthesis?" mentions the name of exactly one concept, and
     * that concept is the one the question is about.
     */
    private double nameCoverage(String conceptId, List<String> queryTerms) {
        Concept concept = conceptsById.get(conceptId);
        if (concept == null) {
            return 0.0;
        }
        List<String> nameTerms = TextFeatures.tokens(concept.name());
        if (nameTerms.isEmpty()) {
            return 0.0;
        }
        long hits = nameTerms.stream().filter(queryTerms::contains).count();
        return (double) hits / nameTerms.size();
    }

    /** The concepts this workspace knows about. */
    public List<Concept> knownConcepts() {
        return snapshot.concepts();
    }

    /** The terms of a question that no claim in the workspace covers. */
    public Set<String> unmatchedTerms(String question) {
        Set<String> covered = new HashSet<>();
        for (Set<String> terms : claimTerms.values()) {
            covered.addAll(terms);
        }
        Set<String> unmatched = new LinkedHashSet<>();
        for (String original : TextFeatures.originalTokens(question)) {
            if (!covered.contains(TextFeatures.fold(original))) {
                unmatched.add(original);
            }
        }
        return unmatched;
    }

    private record ScoredClaim(Claim claim, double coverage) {
    }
}
