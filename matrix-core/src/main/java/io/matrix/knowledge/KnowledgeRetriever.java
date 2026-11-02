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
    private final Map<String, Set<String>> representationTerms = new LinkedHashMap<>();
    private final Map<String, Double> idf;
    /** The corrector for the typos in a question (RECON-W42.3). */
    private final TypoCorrector corrector;

    public KnowledgeRetriever(KnowledgeSnapshot snapshot) {
        this(snapshot, DEFAULT_MINIMUM_COVERAGE);
    }

    public KnowledgeRetriever(KnowledgeSnapshot snapshot, double minimumCoverage) {
        this.snapshot = snapshot;
        this.minimumCoverage = minimumCoverage;
        this.corrector = TypoCorrector.over(snapshot);
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
        // THE REPRESENTATIONS ARE INDEXED, AT THE CONCEPT LEVEL.
        //
        // RECON-W42.7. The answer is composed from the REPRESENTATIONS, and
        // the retriever scored only the CLAIMS. So the answer could speak a
        // vocabulary the retrieval could not see: "Explain how green plants
        // make food using sunlight." shares no term with any claim of the
        // photosynthesis concept, and the answer the workspace writes about
        // that concept says "Photosynthesis is how green plants use light
        // energy to build sugars ... The sugars are the plant's stored
        // food." The retriever and the answer must agree on what the
        // workspace knows.
        //
        // The representation's text belongs to the CONCEPT, and it is
        // scored at the concept level. The first version of this added the
        // representation's terms to every claim of the concept, and the
        // vocabulary of one explanation then reached claims about
        // cellular respiration and stomatal conductance: "What is
        // photosynthesis?" went from seven claims to eleven, the pinned
        // support score moved from 0.84 to 0.82, and the answer grew
        // evidence that has nothing to do with the question. The
        // representation is the concept's own explanation, and it is a
        // SEARCH term - not evidence, and not a claim.
        for (Map.Entry<String, List<Representation>> entry : representationsByConcept.entrySet()) {
            StringBuilder text = new StringBuilder();
            for (Representation representation : entry.getValue()) {
                text.append(' ').append(representation.statement());
            }
            representationTerms.put(entry.getKey(),
                    new LinkedHashSet<>(TextFeatures.tokens(text.toString())));
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
                    ResponseFrame.in(TextProfiles.detect(question),
                            "The question contains no searchable terms."),
                    "", 0.0, SupportScore.none(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), TextFeatures.tokens(question));
        }

        Map<String, Double> queryWeights = new HashMap<>();
        double totalWeight = 0.0;
        for (String term : queryTerms) {
            double weight = idf.getOrDefault(term, maxIdf());
            queryWeights.merge(term, weight, Double::sum);
            totalWeight += weight;
        }

        // THE BILINGUAL BRIDGE.
        //
        // RECON-W42.3. A Russian question and an English claim about the
        // same concept share no token, so the retriever could not reach the
        // claim at all - the W42 benchmark measured 0/5 on the Russian
        // items. The bilingual lexicon says which terms name the same
        // thing, and the counterparts join the query.
        //
        // The counterpart is a SEARCH TERM, not evidence: it does not make
        // the claim true, and it does not make the two propositions
        // equivalent. The claim is judged on its own evidence, exactly as
        // before.
        List<String> queryTermsExpanded = new ArrayList<>(queryTerms);
        // The stem each counterpart came from, so that a term the
        // counterpart covered is not also reported as uncovered.
        Map<String, String> counterpartOf = new LinkedHashMap<>();
        for (String term : queryTerms) {
            for (String counterpart : BilingualLexicon.instance().translations(term)) {
                String folded = TextProfiles.ENGLISH.fold(counterpart);
                counterpartOf.putIfAbsent(folded, term);
                if (!queryTermsExpanded.contains(folded)) {
                    queryTermsExpanded.add(folded);
                    // The counterpart INHERITS the weight of the term it
                    // translates, and the question's total weight does not
                    // change. An English question therefore retrieves
                    // exactly what it retrieved before the lexicon existed
                    // - the pinned W41 support score is unchanged - while a
                    // Russian question gains the reach it was missing.
                    queryWeights.merge(folded, queryWeights.get(term), Double::sum);
                }
            }
        }
        // THE TYPOS ARE CORRECTED, AND THE ORIGINAL IS KEPT.
        //
        // RECON-W42.3. "What is fotosynthesis?" shares no token with the
        // claim it is about, and the workspace refused it. The correction
        // is close to the question's own vocabulary, and the original word
        // stays in the query as well - so the answer is found AND the
        // answer says which word it changed.
        List<TypoCorrector.Correction> corrections = corrector.corrections(question);
        if (!corrections.isEmpty()) {
            for (TypoCorrector.Correction correction : corrections) {
                String folded = TextProfiles.ofLanguage(TextProfiles.detect(question))
                        .fold(correction.corrected());
                if (!queryTermsExpanded.contains(folded)) {
                    queryTermsExpanded.add(folded);
                    // The correction INHERITS the weight of the word it
                    // replaces, so that a question with a typo is weighted
                    // exactly like the question without one.
                    String originalStem = TextProfiles.ofLanguage(TextProfiles.detect(question))
                            .fold(correction.original());
                    double weight = queryWeights.getOrDefault(originalStem, maxIdf());
                    queryWeights.merge(folded, weight, Double::sum);
                }
            }
        }
        final List<String> effectiveTerms = queryTermsExpanded.size() != queryTerms.size()
                ? queryTermsExpanded : queryTerms;

        // The uncovered terms are reported in the words the user wrote, not
        // in the folded stems the matcher works with.
        //
        // RECON-W42.1 fixed a defect here. The set of originals was built
        // and never reduced, so the answer reported EVERY word of the
        // question as "not covered by the workspace" - including the words
        // that were covered and the stopwords. The stem set was reduced
        // correctly; the original set was not. The two are now reduced
        // together, through the stem each original folds to.
        Set<String> unmatched = new LinkedHashSet<>(TextFeatures.originalTokens(question));
        Set<String> unmatchedStems = new LinkedHashSet<>(queryTerms);
        // The words the user wrote, keyed by the STEM each one folds to IN
        // THE LANGUAGE OF THE QUESTION.
        //
        // RECON-W42.7: the key was the ENGLISH fold, which is a no-op for a
        // Cyrillic word - so a Russian question's raw words were keyed by
        // themselves and never paired with their stems. A Russian
        // question about "цикл Кальвина" was answered by the bilingual
        // bridge, and the answer still reported "кальвина" as not covered
        // by the workspace: the bridge's counterpart had covered the stem,
        // and the raw word was never reached.
        final String questionLanguage = TextProfiles.detect(question);
        Map<String, Set<String>> originalsByStem = new LinkedHashMap<>();
        for (String original : unmatched) {
            originalsByStem.computeIfAbsent(TextFeatures.foldIn(original, questionLanguage),
                    key -> new LinkedHashSet<>()).add(original);
        }
        List<ScoredClaim> scored = new ArrayList<>();
        for (Claim claim : snapshot.claims()) {
            Set<String> terms = claimTerms.get(claim.id());
            double matched = 0.0;
            for (String term : effectiveTerms) {
                if (terms.contains(term)) {
                    matched += queryWeights.get(term);
                    unmatchedStems.remove(term);
                    Set<String> originals = originalsByStem.remove(term);
                    if (originals != null) {
                        unmatched.removeAll(originals);
                    }
                    // A term that reached the knowledge through its
                    // counterpart is COVERED: reporting "not covered:
                    // фотосинтез" about a question the workspace just
                    // answered is a false report.
                    String original = counterpartOf.get(term);
                    if (original != null) {
                        unmatched.remove(original);
                        unmatchedStems.remove(original);
                        // The words the user actually wrote. The query's
                        // terms are STEMS, and the uncovered list holds the
                        // RAW words — so removing the stem alone left the
                        // raw word in the list. A Russian question about
                        // "цикл Кальвина" was answered, and the answer
                        // still reported "кальвина" as not covered by the
                        // workspace, which is a false report: the
                        // counterpart DID cover it.
                        //
                        // The map is keyed by the stem in the question's
                        // language, and the counterpart's source stem is
                        // one of those keys.
                        Set<String> written = originalsByStem.remove(original);
                        if (written != null) {
                            unmatched.removeAll(written);
                        }
                    }
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

        // THE CONCEPT BRIDGE. A concept can be reached through its own
        // explanation even when no claim of it shares a term with the
        // question. The representation's coverage is the coverage of the
        // CONCEPT, and the concept's claims are the evidence behind it.
        Map<String, Double> representationCoverages = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : representationTerms.entrySet()) {
            double matched = 0.0;
            for (String term : effectiveTerms) {
                if (entry.getValue().contains(term)) {
                    matched += queryWeights.get(term);
                }
            }
            if (matched > 0.0) {
                representationCoverages.put(entry.getKey(), matched / totalWeight);
            }
        }
        // The concepts that the representations reach, and that the
        // claims alone did not.
        Set<String> claimsAlready = new LinkedHashSet<>();
        for (ScoredClaim match : relevant) {
            claimsAlready.add(match.claim().conceptId());
        }
        List<Claim> bridgedClaims = new ArrayList<>();
        for (Map.Entry<String, Double> entry : representationCoverages.entrySet()) {
            if (entry.getValue() >= minimumCoverage && !claimsAlready.contains(entry.getKey())) {
                for (Claim claim : snapshot.claims()) {
                    if (claim.conceptId().equals(entry.getKey())) {
                        bridgedClaims.add(claim);
                    }
                }
            }
        }

        if (relevant.isEmpty() && bridgedClaims.isEmpty()) {
            // The frame's sentence is said in the language of the
            // question; the knowledge is not translated.
            return new Answer(question, wanted, false,
                    ResponseFrame.in(TextProfiles.detect(question),
                            "No claim in this workspace covers enough of the question to answer it."),
                    "", 0.0, SupportScore.none(), List.of(), List.of(), List.of(),
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
        for (Map.Entry<String, Double> entry : representationCoverages.entrySet()) {
            conceptScores.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        List<Concept> concepts = conceptScores.entrySet().stream()
                .sorted((left, right) -> {
                    // The EFFECTIVE terms, not the raw ones: the question
                    // "Что такое фотосинтез?" asks for the concept
                    // photosynthesis by its counterpart, and the concept
                    // whose NAME the question named is the concept the
                    // question is about. With the raw terms the name
                    // coverage of every concept was zero and the order
                    // fell back to the score - and the answer to "Что такое
                    // фотосинтез?" was about cellular respiration.
                    int byName = Double.compare(nameCoverage(right.getKey(), effectiveTerms),
                            nameCoverage(left.getKey(), effectiveTerms));
                    if (byName != 0) {
                        return byName;
                    }
                    return Double.compare(right.getValue(), left.getValue());
                })
                .map(e -> conceptsById.get(e.getKey()))
                .filter(c -> c != null)
                .toList();

        List<Claim> claims = new ArrayList<>(relevant.stream().map(ScoredClaim::claim).toList());
        claims.addAll(bridgedClaims);

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

        // The support score: the mean of the matched claims' OWN confidence
        // field - which was authored by hand in the knowledge package -
        // multiplied by the retrieval coverage. The VALUE is unchanged from
        // RECON-W40, so nothing that depended on it breaks; what changed is
        // that it is now called a support score, decomposed, and shipped with
        // a statement of what it is not. See SupportScore.
        double claimConfidence = claims.stream().mapToDouble(Claim::confidence).average().orElse(0.0);
        double coverage = relevant.stream().mapToDouble(ScoredClaim::coverage).average().orElse(0.0);
        double supportScore = Math.round(claimConfidence * coverage * 100.0) / 100.0;

        SupportScore support = new SupportScore(
                supportScore,
                round(coverage),
                sources.stream().mapToDouble(src -> src.authority()).average().orElse(0.0),
                sources.stream().mapToDouble(src -> src.freshness()).average().orElse(0.0),
                claims.size(),
                sources.size(),
                scopeMatch(queryTerms, claims),
                SupportScore.ContradictionStatus.NOT_COMPUTED,
                SupportScore.UNCALIBRATED,
                SupportScore.MEANING);

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
        for (TypoCorrector.Correction correction : corrector.corrections(question)) {
            limitations.add("Read \"" + correction.original() + "\" as \""
                    + correction.corrected() + "\" (" + correction.distance() + " edit(s)); "
                    + "the question is kept as it was written.");
        }

        return new Answer(question, used, true, null,
                String.join("\n\n", statements), supportScore, support, concepts, claims, sources,
                limitations, List.copyOf(unmatched), List.copyOf(unmatched));
    }

    /**
     * How well the question falls inside the scope the claims declare.
     *
     * <p>A claim's scope says where it holds ("in green plants, in light").
     * If the question's terms fall inside it, the scope matches; if a scope
     * is declared and none of the question's terms fall in it, the answer is
     * outside the scope it was asked about; if no scope is declared, nothing
     * can be said, and that is what is reported.
     */
    private SupportScore.ScopeMatch scopeMatch(List<String> queryTerms, List<Claim> claims) {
        boolean anyScope = false;
        boolean anyTermInScope = false;
        for (Claim claim : claims) {
            String scope = claim.scope();
            if (scope == null || scope.isBlank()) {
                continue;
            }
            anyScope = true;
            Set<String> scopeTerms = new LinkedHashSet<>(TextFeatures.tokens(scope));
            for (String term : queryTerms) {
                if (scopeTerms.contains(term)) {
                    anyTermInScope = true;
                }
            }
        }
        if (!anyScope) {
            return SupportScore.ScopeMatch.UNDECLARED;
        }
        return anyTermInScope ? SupportScore.ScopeMatch.MATCHES : SupportScore.ScopeMatch.OUTSIDE;
    }

    /** Rounds to three decimals, so the support score is not over-precise. */
    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
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
