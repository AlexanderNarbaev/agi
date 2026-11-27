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
import io.matrix.neuron.HebbianUpdater;

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

    /**
     * The system property that overrides the coverage floor, for diagnosis and
     * the retrieval experiments (EVID-BASE E2).
     *
     * <p>The name follows the house style of the other runtime knobs
     * ({@code matrix.knowledge.snapshot.max-payload-bytes}): a dotted name
     * under the {@code matrix.} prefix, read with {@link System#getProperty}.</p>
     */
    public static final String MINIMUM_COVERAGE_PROPERTY =
            "matrix.retrieval.minimum-coverage";

    /**
     * The coverage floor in force: the configured value, or the default.
     *
     * <p>A property that is absent, unreadable, or outside the unit interval
     * leaves the default in place, so an unconfigured deployment retrieves
     * exactly as it did before this knob existed.</p>
     *
     * @return the share a claim must cover to reach the answer's evidence
     */
    public static double configuredMinimumCoverage() {
        String configured = System.getProperty(MINIMUM_COVERAGE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MINIMUM_COVERAGE;
        }
        try {
            double value = Double.parseDouble(configured.trim());
            if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
                return DEFAULT_MINIMUM_COVERAGE;
            }
            return value;
        } catch (NumberFormatException unreadable) {
            return DEFAULT_MINIMUM_COVERAGE;
        }
    }

    private final KnowledgeSnapshot snapshot;
    private final double minimumCoverage;

    /**
     * The weakened bindings, by concept.
     *
     * <p>RECON-W42.7. The error-driven decay weakens a concept's binding
     * to the terms the learner got wrong, and until now nothing in the
     * retrieval consulted it: the answer said the bindings had been
     * weakened, and the answer stood on exactly the same claims as
     * before. The mission's own definition of an integration asks that a
     * component's result be CONSUMED by another meaningful component,
     * and this is that consumption.
     *
     * <p>The map holds the STORED accumulator of each concept that has
     * one. A concept with no entry has no weakened binding, and its
     * terms count in full.
     */
    private final Map<String, float[]> weakenedBindings;

    private final Map<String, Concept> conceptsById = new LinkedHashMap<>();
    private final Map<String, Claim> claimsById = new LinkedHashMap<>();
    private final Map<String, List<Representation>> representationsByConcept = new LinkedHashMap<>();
    /** The terms of the claims, by claim identifier. */
    private final Map<String, Set<String>> claimTerms;

    /**
     * The indexed terms of every claim of the workspace.
     *
     * <p><b>Why the index is published.</b> The refutation scan asks whether a claim covers
     * the premise's terms, and it used to tokenise every claim of the workspace to ask it —
     * a second full read of the workspace for every question. The retrieval has already
     * tokenised every claim (it does it to rank them), and a claim that shares no term with
     * the premise cannot cover it. The index is handed over so that the scan reads the
     * claims that can bear on the premise and leaves the rest alone; it holds the SAME
     * tokens the retrieval ranked the claims by, and the verdict does not depend on it.</p>
     *
     * @return the terms of each claim, by claim identifier
     */
    Map<String, Set<String>> claimTerms() {
        return claimTerms;
    }

    private final Map<String, Set<String>> representationTerms = new LinkedHashMap<>();
    /**
     * The lemmas of the claims' words, by claim.
     *
     * <p>RECON-W42.9. The English fold is asymmetric, so a question and a
     * claim that differ only in their inflection never meet: "release" folds
     * to "release" and "releases" folds to "releas". The fold is NOT changed
     * — its behaviour is pinned by W41's tests and a global change to it
     * was measured and rolled back twice — so the equivalence is applied
     * where the terms are COMPARED. This map is the second channel of that
     * comparison; the folded terms still decide the ranking, the coverage
     * threshold and the reported gaps.
     *
     * @see EnglishLemma
     */
    private final Map<String, Set<String>> claimLemmas;
    /** The lemmas of the representations' words, by concept. */
    private final Map<String, Set<String>> representationLemmas = new LinkedHashMap<>();
    private final Map<String, Double> idf;
    /**
     * The statistics of the view the claims are read from.
     *
     * <p>They are held, not computed, when the caller already has them: the statistics of a
     * view are the expensive half of a reading, and a caller that reads the same view twice
     * should pay for them once. The retriever still computes them when it is given none.</p>
     */
    private final KnowledgeStatistics retrievalStatistics;
    /** The corrector for the typos in a question (RECON-W42.3). */
    private final TypoCorrector corrector;

    public KnowledgeRetriever(KnowledgeSnapshot snapshot) {
        this(snapshot, DEFAULT_MINIMUM_COVERAGE);
    }

    public KnowledgeRetriever(KnowledgeSnapshot snapshot, double minimumCoverage) {
        this(snapshot, minimumCoverage, Map.of());
    }

    /**
     * A retriever that consults the weakened bindings.
     *
     * @param snapshot the workspace
     * @param minimumCoverage the coverage a claim must reach
     * @param weakenedBindings the stored accumulator of each concept whose
     *     binding the error-driven decay has weakened
     */
    public KnowledgeRetriever(KnowledgeSnapshot snapshot, double minimumCoverage,
            Map<String, float[]> weakenedBindings) {
        this(snapshot, snapshot, minimumCoverage, weakenedBindings);
    }

    /**
     * A retriever that takes its relevance STATISTICS from another view than the one it reads.
     *
     * <p><b>Why the statistics may come from another view.</b> The inverse document frequency
     * of a term is a number about the CORPUS the question is asked in: a word that is rare in
     * the workspace is discriminative, and a word every claim carries is not. A reading that is
     * RESTRICTED to a set of sources is the same question asked in the same workspace with
     * fewer claims eligible to answer it — and a retriever that recomputed its statistics over
     * the smaller set would make the restriction change the SCORING as well as the candidates.
     * The corpus shrinks, and the weight of a term is
     * {@code log(1 + total / documentFrequency)} — so a smaller corpus gives every term a
     * SMALLER weight, and it gives the COMMON terms a proportionally LARGER fall: over the
     * H2 corpus (59 claims down to 36) a term carried by 12 claims loses 22 % of its weight,
     * while a term carried by one claim loses 12 %. The question's total weight falls with
     * them, but the claims whose matched terms are the common ones lose coverage FASTER than
     * the total does, and they fall below the floor: on the H2-A05 question the unrestricted
     * reading cited four claims and the restricted reading cited two. So a restricted reading
     * drops claims that the unrestricted reading cited, and the two answers stop being
     * comparable.
     *
     * <p>That is not a restriction, it is a DIFFERENT RETRIEVAL, and a procedure that replays a
     * validated support set needs the first: the support set decides WHAT MAY BE CITED, and
     * the workspace's own statistics decide HOW RELEVANT a claim is. The two are separate
     * questions, and the retriever answers them separately.</p>
     *
     * <p><b>What this does not change.</b> The claims that may be cited are still the claims of
     * {@code snapshot}; only the weights come from {@code statistics}. A statistics view that
     * is the retrieval view itself — the ordinary case — computes exactly what it computed
     * before.</p>
     *
     * @param snapshot the view the claims are read from
     * @param statistics the view whose claims define the relevance statistics; null means the
     *     retrieval view itself
     * @param minimumCoverage the coverage a claim must reach
     * @param weakenedBindings the stored accumulator of each concept whose binding the
     *     error-driven decay has weakened
     */
    public KnowledgeRetriever(KnowledgeSnapshot snapshot, KnowledgeSnapshot statistics,
            double minimumCoverage, Map<String, float[]> weakenedBindings) {
        this(snapshot, statistics,
                KnowledgeStatistics.of(snapshot),
                statistics == null || statistics == snapshot
                        ? KnowledgeStatistics.of(snapshot)
                        : KnowledgeStatistics.of(statistics),
                minimumCoverage, weakenedBindings);
    }

    /**
     * A retriever that reads PREBUILT statistics.
     *
     * <p><b>Why the statistics may be handed in.</b> They are the expensive half of a
     * reading: every claim of the view is split, folded, lemmatised and counted. A caller
     * that reads the same view more than once — the procedure path reads the workspace for
     * its statistics and the support set for its claims — should pay for them once, and the
     * statistics of a view are a pure function of the view's content, so a caller can keep
     * them and hand them back.</p>
     *
     * <p><b>What this does not change.</b> The claims that may be cited are still the claims
     * of {@code snapshot}; only the weights come from the statistics view. A statistics view
     * that is the retrieval view itself — the ordinary case — computes exactly what it
     * computed before.</p>
     *
     * @param snapshot            the view the claims are read from
     * @param statistics          the view whose claims define the relevance statistics; null
     *                            means the retrieval view itself
     * @param retrievalStatistics the statistics of the retrieval view
     * @param statisticsIndex     the statistics of the statistics view
     * @param minimumCoverage     the coverage a claim must reach
     * @param weakenedBindings    the stored accumulator of each concept whose binding the
     *                            error-driven decay has weakened
     */
    public KnowledgeRetriever(KnowledgeSnapshot snapshot, KnowledgeSnapshot statistics,
            KnowledgeStatistics retrievalStatistics, KnowledgeStatistics statisticsIndex,
            double minimumCoverage, Map<String, float[]> weakenedBindings) {
        this.snapshot = snapshot;
        this.minimumCoverage = minimumCoverage;
        this.weakenedBindings = weakenedBindings == null ? Map.of() : Map.copyOf(weakenedBindings);
        this.corrector = TypoCorrector.over(snapshot);
        this.retrievalStatistics = retrievalStatistics;
        for (Concept concept : snapshot.concepts()) {
            conceptsById.put(concept.id(), concept);
        }
        for (Claim claim : snapshot.claims()) {
            claimsById.put(claim.id(), claim);
        }
        this.claimTerms = retrievalStatistics.terms();
        this.claimLemmas = retrievalStatistics.lemmas();
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
            representationLemmas.put(entry.getKey(), EnglishLemma.lemmasOf(text.toString()));
        }
        this.idf = statisticsIndex.idf();
    }

    /** Answer a question at the introductory level by default. */
    public Answer answer(String question) {
        return answer(question, AbstractionLevel.INTRODUCTORY);
    }

    /** Answer a question at a requested abstraction level. */
    /** Answer a question at a requested abstraction level. */
    public Answer answer(String question, AbstractionLevel level) {
        AbstractionLevel wanted = level == null ? AbstractionLevel.INTRODUCTORY : level;
        Query query = prepare(question);
        if (query.queryTerms().isEmpty()) {
            return new Answer(question, wanted, false,
                    ResponseFrame.in(TextProfiles.detect(question),
                            "The question contains no searchable terms."),
                    "", 0.0, SupportScore.none(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), TextFeatures.tokens(question));
        }
        Scoring scoring = scoreClaims(query, null);
        List<String> queryTerms = query.queryTerms();
        List<String> effectiveTerms = query.effectiveTerms();
        Map<String, Double> queryWeights = query.queryWeights();
        double totalWeight = query.totalWeight();
        Map<String, Set<String>> queryLemmas = query.queryLemmas();
        Map<String, Set<String>> originalsByStem = query.originalsByStem();
        Map<String, String> counterpartOf = query.counterpartOf();
        Set<String> unmatched = query.unmatched();
        Set<String> unmatchedStems = query.unmatchedStems();
        List<ScoredClaim> scored = scoring.scored();
        List<ScoredClaim> relevant = scoring.relevant();
        long unestablished = scoring.unestablished();
        List<Claim> withdrawn = scoring.withdrawn();
        Map<String, Double> representationCoverages = scoring.representationCoverages();

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
                    // The bridge reaches a CONCEPT; it does not launder a
                    // claim the workspace has not established into evidence.
                    if (claim.conceptId().equals(entry.getKey())
                            && EpistemicPolicy.isCitable(claim.status())) {
                        bridgedClaims.add(claim);
                    }
                }
            }
        }

        if (relevant.isEmpty() && bridgedClaims.isEmpty()) {
            // The frame's sentence is said in the language of the
            // question; the knowledge is not translated.
            //
            // W51 §6: the refusal says WHICH gate refused. A workspace that
            // holds the subject but not an established claim about it has
            // to say so, because the reader's next question is "what does
            // it hold?" — and the answer "it holds something, but not
            // something established" is a different answer from "it holds
            // nothing".
            String reason = unestablished > 0
                    ? "The workspace holds " + unestablished + " claim(s) that cover the question, "
                            + "but none of them is established: a claim the workspace has not "
                            + "validated is not evidence, and the question is not answered from one."
                    : "No claim in this workspace covers enough of the question to answer it.";
            return new Answer(question, wanted, false,
                    ResponseFrame.in(TextProfiles.detect(question), reason),
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

        // THE RULE APPLICATION.
        //
        // RECON-W42.7. The mission's §7 names the transfer category: a
        // principle that has been grasped must be applied to an instance it
        // was not stated in. The workspace can state modus ponens, and it
        // could not apply it - a question that stated a conditional and
        // asked what followed was answered with the DEFINITION of the rule
        // and nothing about the instance.
        //
        // The application is grounded in a claim the workspace holds, and
        // the consequent is the QUESTION's own text: nothing is invented
        // here, and a workspace that holds no rule is not licensed to
        // apply one.
        RuleApplication.Application application = RuleApplication.apply(question, snapshot.claims());
        if (application != null) {
            statements.add(0, RuleApplication.statementOf(application));
            limitations.add("The rule applied is stated by the workspace: "
                    + application.groundingProposition());
            Claim grounding = snapshot.claims().stream()
                    .filter(claim -> claim.id().equals(application.groundingClaimId()))
                    .findFirst()
                    .orElse(null);
            if (grounding != null && !claims.contains(grounding)) {
                claims.add(grounding);
            }
        }

        // THE SUPPORT SCORE, AND WHAT IT IS NOT (W51 §6).
        //
        // The score is the mean of the matched claims' OWN confidence field
        // - which was authored by hand in the knowledge package - multiplied
        // by the retrieval coverage. The VALUE is unchanged from RECON-W40,
        // so nothing that depended on it breaks; what changed is that it is
        // now called a support score, decomposed, and shipped with a
        // statement of what it is not. See SupportScore.
        //
        // W51 §6 SEPARATES RELEVANCE FROM EPISTEMIC SUPPORT. The claims
        // that carry the score are the ESTABLISHED ones: a claim the
        // workspace has not established contributes no numeric support, and
        // it does not drag the mean of the established ones down either.
        // The count of the claims that carry no numeric support is
        // REPORTED, because the W50 evaluation measured that every answer
        // of the demonstrator carried a support value of 0.0 and no
        // explanation of why.
        List<Claim> established = claims.stream()
                .filter(claim -> EpistemicPolicy.isEstablished(claim.status()))
                .toList();
        List<Claim> withoutNumericSupport = claims.stream()
                .filter(claim -> !EpistemicPolicy.carriesNumericSupport(claim))
                .toList();
        double claimConfidence = established.isEmpty() ? 0.0
                : established.stream().mapToDouble(Claim::confidence).average().orElse(0.0);
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
        if (!withoutNumericSupport.isEmpty()) {
            limitations.add(withoutNumericSupport.size() + " of the " + claims.size()
                    + " cited claims carry no authored confidence: the support score is 0.0 for "
                    + "them, and the citation is not a probability.");
        }
        if (unestablished > 0) {
            limitations.add(unestablished + " claim(s) of the workspace cover the question but are "
                    + "not established, and are therefore not cited as evidence.");
        }
        for (Claim claim : withdrawn) {
            limitations.add((claim.status() == ClaimStatus.REFUTED
                    ? "The workspace holds a refuted claim about this, which is not evidence: "
                    : "The workspace superseded a claim about this, which is not evidence: ")
                    + claim.proposition());
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
     * The question, prepared for the retrieval.
     *
     * <p><b>Why the preparation is its own step.</b> The terms a question reaches a claim by
     * are not the words the question was written in: the bilingual bridge adds the
     * counterparts, the typo correction adds the corrected form, the English fold adds the
     * indexed form, and the lemma channel widens the reach of a term that differs only in its
     * inflection. All of that is decided ONCE per question, and both the answer and the
     * evidence assessment read it — so the two can never reach different conclusions about
     * which claims the question touches.</p>
     *
     * @param question the question, as it was written
     * @return the prepared question
     */
    private Query prepare(String question) {
        List<String> queryTerms = TextFeatures.tokens(question);

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
        // W42.9: the bridge must see the CORRECTED words too. It used to run
        // BEFORE the typo correction, so a Russian typo was fixed after the
        // bridge had already looked: the bridge never saw the fixed Russian
        // term, never produced its English counterpart, and the question could
        // not reach the English claim it was about.
        //
        // The counterpart INHERITS the weight of the word it translates, and
        // the question's total weight does not change. An English question
        // therefore retrieves exactly what it retrieved before the lexicon
        // existed - the pinned W41 support score is unchanged - while a
        // Russian question gains the reach it was missing. A CORRECTED word
        // inherits the weight of the word it replaced, so a question with a
        // typo is weighted exactly like the question without one.
        java.util.function.Function<String, String> weightSource = term -> term;
        List<String> bridgeTerms = new ArrayList<>(queryTerms);
        for (TypoCorrector.Correction correction : corrector.corrections(question)) {
            String detected = TextProfiles.detect(question);
            String foldedCorrected = TextProfiles.ofLanguage(detected).fold(correction.corrected());
            bridgeTerms.add(foldedCorrected);
            String foldedOriginal = TextProfiles.ofLanguage(detected).fold(correction.original());
            weightSource = weightSource.andThen(term ->
                foldedCorrected.equals(term) ? foldedOriginal : term);
        }
        for (String term : bridgeTerms) {
            for (String counterpart : BilingualLexicon.instance().translations(term)) {
                String folded = TextProfiles.ENGLISH.fold(counterpart);
                counterpartOf.putIfAbsent(folded, weightSource.apply(term));
                if (!queryTermsExpanded.contains(folded)) {
                    queryTermsExpanded.add(folded);
                    Double weight = queryWeights.get(weightSource.apply(term));
                    if (weight != null) {
                        queryWeights.merge(folded, weight, Double::sum);
                    }
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
        // W42.9: the INDEX folds every claim with the claim's own language and
        // the QUERY folds with the question's language. A Russian question
        // therefore leaves a Latin term ("modus") as it is, while the index
        // holds it folded ("modu"), and the term never reached the claim it
        // was about. The query's terms are therefore ALSO folded the way the
        // index folds them. For an English question this adds nothing - the
        // English fold of an English term is the term itself - so the pinned
        // W41 path stays exactly as it was.
        for (String term : queryTerms) {
            String asIndexed = TextProfiles.ENGLISH.fold(term);
            if (!asIndexed.equals(term) && !queryTermsExpanded.contains(asIndexed)) {
                queryTermsExpanded.add(asIndexed);
                // The word the user wrote is COVERED by the form the index
                // holds, so it must not also be reported as uncovered.
                counterpartOf.putIfAbsent(asIndexed, term);
                double weight = queryWeights.getOrDefault(term, maxIdf());
                queryWeights.merge(asIndexed, weight, Double::sum);
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
        // THE QUERY'S LEMMAS.
        //
        // RECON-W42.9. The query's terms are folded, and the English fold is
        // asymmetric, so a question and a claim that differ only in their
        // inflection do not meet. The query therefore carries a second
        // channel of lemmas, keyed by the same folded term, and a term
        // matches a claim when EITHER channel agrees.
        //
        // The key is the folded term, so the weight, the reporting of the
        // uncovered words and the rest of the accounting stay exactly where
        // they were: the lemma channel only widens the REACH of a claim.
        Map<String, Set<String>> queryLemmas = new LinkedHashMap<>();
        for (String original : TextFeatures.originalTokens(question)) {
            String stem = TextFeatures.foldIn(original, questionLanguage);
            queryLemmas.computeIfAbsent(stem, key -> new LinkedHashSet<>())
                    .add(EnglishLemma.lemma(original));
        }
        // A term that came from the bilingual bridge, from a typo correction
        // or from the index form has no raw word of its own behind it, so its
        // lemma is computed from the term itself.
        for (String term : effectiveTerms) {
            if (!queryLemmas.containsKey(term)) {
                queryLemmas.computeIfAbsent(term, key -> new LinkedHashSet<>())
                        .add(EnglishLemma.lemma(term));
            }
        }
        return new Query(queryTerms, effectiveTerms, queryWeights, totalWeight,
                queryLemmas, originalsByStem, counterpartOf, unmatched, unmatchedStems);
    }

    /**
     * The claims of this view that the question reaches, with their relevance.
     *
     * <p><b>What the candidate set is for.</b> A claim that carries none of the question's
     * terms cannot match it, so the claims that CAN match are found through the statistics'
     * inverted indexes instead of through a scan of every claim. The candidate set is a
     * SUPERSET of the claims a full scan would reach — it holds every claim that carries a
     * term and every claim whose lemmas agree with a term's lemmas, which are exactly the
     * retriever's two matching channels — so the coverage a claim is given here is the
     * coverage it is given by the full scan, and the two answers cannot disagree.</p>
     *
     * @param query             the prepared question
     * @param candidateClaimIds the claims that may be scored, or null for all of them
     * @return the scoring
     */
    private Scoring scoreClaims(Query query, Set<String> candidateClaimIds) {
        List<String> effectiveTerms = query.effectiveTerms();
        Map<String, Double> queryWeights = query.queryWeights();
        double totalWeight = query.totalWeight();
        Map<String, Set<String>> queryLemmas = query.queryLemmas();
        Map<String, Set<String>> originalsByStem = query.originalsByStem();
        Map<String, String> counterpartOf = query.counterpartOf();
        Set<String> unmatched = query.unmatched();
        Set<String> unmatchedStems = query.unmatchedStems();
        List<ScoredClaim> scored = new ArrayList<>();
        for (Claim claim : snapshot.claims()) {
            if (candidateClaimIds != null && !candidateClaimIds.contains(claim.id())) {
                continue;
            }
            Set<String> terms = claimTerms.get(claim.id());
            Set<String> lemmas = claimLemmas.get(claim.id());
            if (terms == null || lemmas == null) {
                // a claim the statistics do not index carries no term the question could
                // reach, so it matches nothing and contributes nothing
                continue;
            }
            double matched = 0.0;
            for (String term : effectiveTerms) {
                if (terms.contains(term) || sameLemma(lemmas, queryLemmas.get(term))) {
                    matched += queryWeights.get(term) * integrityOf(claim, term, originalsByStem);
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
                .filter(s -> EpistemicPolicy.isCitable(s.claim().status()))
                .toList();

        // W51 §6 — THE STATUS FILTER, AND WHAT IT COST.
        //
        // A claim the workspace has not established is not evidence, and a
        // claim the workspace has withdrawn is never evidence. The W50
        // evaluation measured the consequence of not saying so: 569 of the
        // 571 claims an answer cited were CANDIDATE claims nobody had
        // validated, and one was a claim the model had itself superseded.
        //
        // The rule lives in EpistemicPolicy, so that there is ONE place
        // where it can be read and tightened, and the number of claims the
        // filter removed is REPORTED rather than dropped silently: a reader
        // who sees that the workspace holds more than the answer used must
        // be able to ask why.
        long unestablished = scored.stream()
                .filter(s -> s.coverage() >= minimumCoverage)
                .filter(s -> !EpistemicPolicy.isCitable(s.claim().status()))
                .count();

        // The claims that were dropped because the workspace has withdrawn
        // them: superseded by a later revision, or refuted by the evidence.
        // They are not evidence and they are not in the answer's evidence
        // list, but the reader is told they exist: a claim the workspace
        // once believed and has since taken back is exactly the thing a
        // learner has to know about.
        List<Claim> withdrawn = scored.stream()
                .filter(s -> s.coverage() >= minimumCoverage)
                .map(ScoredClaim::claim)
                .filter(claim -> EpistemicPolicy.isWithdrawn(claim.status()))
                .distinct()
                .toList();

        // THE CONCEPT BRIDGE. A concept can be reached through its own
        // explanation even when no claim of it shares a term with the
        // question. The representation's coverage is the coverage of the
        // CONCEPT, and the concept's claims are the evidence behind it.
        Map<String, Double> representationCoverages = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : representationTerms.entrySet()) {
            Set<String> lemmas = representationLemmas.get(entry.getKey());
            double matched = 0.0;
            for (String term : effectiveTerms) {
                if (entry.getValue().contains(term) || sameLemma(lemmas, queryLemmas.get(term))) {
                    matched += queryWeights.get(term);
                }
            }
            if (matched > 0.0) {
                representationCoverages.put(entry.getKey(), matched / totalWeight);
            }
        }
        return new Scoring(scored, relevant, unestablished, withdrawn, representationCoverages);
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

    /**
     * The integrity of the binding that carries a matched term.
     *
     * <p>A concept with no weakened binding counts in full. A concept
     * with one counts by the strength of its binding to the terms the
     * question actually wrote - the raw words, because the bindings were
     * built from the raw words of the claims.
     *
     * @param claim the matched claim
     * @param stem the matched stem
     * @param originalsByStem the raw words the question wrote
     * @return the integrity in [0, 1]
     */
    private double integrityOf(Claim claim, String stem, Map<String, Set<String>> originalsByStem) {
        float[] stored = weakenedBindings.get(claim.conceptId());
        if (stored == null) {
            return 1.0;
        }
        HebbianUpdater.State fresh = HebbianBindings.stateFor(snapshot, claim.conceptId());
        double integrity = HebbianBindings.integrityOf(stored, fresh.accumulator);
        Set<String> originals = originalsByStem.get(stem);
        if (originals == null || originals.isEmpty()) {
            return integrity;
        }
        for (String original : originals) {
            integrity = Math.min(integrity,
                    HebbianBindings.integrityOf(stored, fresh.accumulator, original));
        }
        return integrity;
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

    /**
     * How relevant this view's claims are to a question.
     *
     * <p><b>What this is for.</b> The answer tells a reader WHICH claims were cited; it does
     * not tell a reader how relevant the workspace's OTHER claims were. A question of
     * whether a restricted reading would still carry the workspace's best evidence needs
     * exactly that: the coverage of every claim the question reaches, and the claim the
     * unrestricted reading would cite first.</p>
     *
     * <p><b>Why it is the same scoring the answer uses.</b> The preparation of the question
     * and the scoring of the claims are the answer's own two steps, and this method calls
     * them — it does not have a second implementation of either. The only difference is that
     * the claims that carry none of the question's terms are not scored at all: they cannot
     * match, and the inverted indexes of the statistics say which ones they are. A claim's
     * coverage is therefore the number the answer would give it.</p>
     *
     * <p><b>What it does not do.</b> It does not build an answer: there is no statement, no
     * concept order and no rule application here, because a reader of the relevance does
     * not want a second answer — it wants the numbers the first one was built from.</p>
     *
     * @param question the question
     * @return the relevance of this view's claims to the question
     */
    public Relevance relevance(String question) {
        Query query = prepare(question);
        if (query.queryTerms().isEmpty()) {
            return new Relevance(Map.of(), List.of(), null, 0.0);
        }
        Scoring scoring = scoreClaims(query, candidates(query));
        List<ScoredClaim> relevant = scoring.relevant();
        Map<String, Double> coverage = new LinkedHashMap<>();
        for (ScoredClaim match : scoring.scored()) {
            coverage.put(match.claim().id(), match.coverage());
        }
        String best = relevant.isEmpty() ? null : relevant.get(0).claim().id();
        double bestCoverage = relevant.isEmpty() ? 0.0 : relevant.get(0).coverage();
        return new Relevance(coverage, relevant.stream().map(match -> match.claim().id()).toList(),
                best, bestCoverage);
    }

    /**
     * The claims of this view that the question CAN reach.
     *
     * <p>The two matching channels of the retrieval are the term and the lemma, and the
     * statistics' inverted indexes hold exactly those two: the claims that carry a term, and
     * the claims whose lemmas carry a lemma. The set they produce is a superset of the
     * claims a full scan would reach, so a claim the scan would have matched is never left
     * out of the scoring here.</p>
     *
     * @param query the prepared question
     * @return the claim identifiers that may be scored
     */
    private Set<String> candidates(Query query) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String term : query.effectiveTerms()) {
            candidates.addAll(retrievalStatistics.claimsCarrying(term));
            Set<String> lemmas = query.queryLemmas().get(term);
            if (lemmas == null) {
                continue;
            }
            for (String lemma : lemmas) {
                candidates.addAll(retrievalStatistics.claimsWhoseLemmasCarry(lemma));
            }
        }
        return candidates;
    }

    /**
     * Whether a claim's lemmas and a query term's lemmas name the same word.
     *
     * <p>RECON-W42.9. The empty case is the common one: a term that came
     * from the bilingual bridge has a lemma, and a claim whose words are
     * all unrelated to it has none.
     *
     * @param claimLemmas the lemmas of the claim's words
     * @param queryLemmas the lemmas of the query term's words
     * @return whether the two agree
     */
    private static boolean sameLemma(Set<String> claimLemmas, Set<String> queryLemmas) {
        if (claimLemmas == null || queryLemmas == null || claimLemmas.isEmpty()
                || queryLemmas.isEmpty()) {
            return false;
        }
        for (String lemma : queryLemmas) {
            if (claimLemmas.contains(lemma)) {
                return true;
            }
        }
        return false;
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

    /**
     * The question, prepared for the retrieval.
     *
     * <p>The collections are the ones the scoring mutates — the uncovered words, the stems
     * that were reached and the stems that were not — and they are held by reference, so the
     * scoring sees exactly the state the preparation left.</p>
     *
     * @param queryTerms      the question's own terms
     * @param effectiveTerms  the terms the question reaches a claim by, after the bridge, the
     *                        typo correction and the English fold
     * @param queryWeights    the weight of every effective term
     * @param totalWeight     the question's total weight
     * @param queryLemmas     the lemmas of every effective term
     * @param originalsByStem the words the question wrote, by the stem they fold to
     * @param counterpartOf   the term each bridged counterpart came from
     * @param unmatched       the words no claim of the workspace covers
     * @param unmatchedStems  the stems no claim of the workspace covers
     */
    private record Query(
            List<String> queryTerms,
            List<String> effectiveTerms,
            Map<String, Double> queryWeights,
            double totalWeight,
            Map<String, Set<String>> queryLemmas,
            Map<String, Set<String>> originalsByStem,
            Map<String, String> counterpartOf,
            Set<String> unmatched,
            Set<String> unmatchedStems) {
    }

    /**
     * What the scoring found.
     *
     * @param scored                every claim that matched, best first
     * @param relevant              the claims that reach the coverage floor and may be cited
     * @param unestablished         the claims that reach the floor but are not established
     * @param withdrawn             the claims that reach the floor and have been withdrawn
     * @param representationCoverages the coverage of each concept a representation reaches
     */
    private record Scoring(
            List<ScoredClaim> scored,
            List<ScoredClaim> relevant,
            long unestablished,
            List<Claim> withdrawn,
            Map<String, Double> representationCoverages) {
    }

    /**
     * How relevant a view's claims are to a question.
     *
     * @param coverage        the coverage of every claim that matched, by claim identifier
     * @param relevantClaimIds the claims that reach the coverage floor and may be cited,
     *                        best first
     * @param bestClaimId     the claim a reading of this view would cite first, or null when
     *                        no claim reaches the floor
     * @param bestCoverage    the coverage of that claim
     */
    public record Relevance(
            Map<String, Double> coverage,
            List<String> relevantClaimIds,
            String bestClaimId,
            double bestCoverage) {

        public Relevance {
            coverage = Map.copyOf(coverage);
            relevantClaimIds = List.copyOf(relevantClaimIds);
        }
    }
}
